package app.workoutplanner.planner

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** 주간 계획 짜기. AI 없이 규칙(제약 조건)만으로 배치. */
fun planWeek(request: PlanRequest): WeekPlan = Planner(request).plan()

/** 후보 칸. windowIndex = 그 요일 시간대 중 몇 번째(작을수록 먼저 씀). */
internal data class Slot(val session: PlannedSession, val windowIndex: Int)

private data class Item(val activity: Activity, val leg: Boolean = false)

internal class Planner(private val req: PlanRequest) {
    private val r = req.rules
    private val logic = RuleLogic(r, req.weather)
    private val days: List<LocalDate> = (0L..6L).map { req.weekStart.plusDays(it) }
    private val done: List<PlannedSession> = req.done.map {
        PlannedSession(it.activity, it.start, it.recordedMinutes + r.recordPaddingMinutes, leg = it.leg, done = true)
    }
    private val reasons = mutableListOf<String>()
    private val unmet = mutableListOf<UnmetRule>()
    private val skippedFixed = mutableListOf<Pair<PlannedSession, String>>()
    private var nodes = 0

    private fun inWeek(d: LocalDate) = d in days
    private fun weekDone() = done.filter { inWeek(it.date) }

    fun plan(): WeekPlan {
        // ① 고정 일정 (강습)
        var placed = placeFixed()

        // ② 최소 횟수 (러닝·하체·헬스)
        placed = placeMinimums(placed)

        // ③·④ 빈 시간 채우기: 자유수영 먼저, 그다음 헬스·러닝
        placed = fillExtras(placed)
        recordUnmet(placed)

        weatherReasons(placed)
        req.previous?.let { diffReasons(it, placed) }

        val all = (done.filter { inWeek(it.date) } + placed).sortedBy { it.start }
        val checks = Checker(req, logic, days, done + placed, unmet, skippedFixed).run()
        return WeekPlan(req.weekStart, all, checks, reasons.toList(), unmet.toList())
    }

    // ---------- ① 고정 일정 ----------

    private fun placeFixed(): List<PlannedSession> {
        val placed = mutableListOf<PlannedSession>()
        for (f in r.fixed) {
            val date = days.firstOrNull { it.dayOfWeek == f.day } ?: continue
            val s = PlannedSession(f.activity, date.atTime(f.start), f.minutes, fixed = true, note = f.label)
            if (req.replanFrom != null && s.start < req.replanFrom) continue
            if (done.any { it.date == date && it.activity == f.activity }) continue
            val closure = logic.facility(f.activity)?.let { logic.facilityClosure(it, date) }
            val blackout = logic.blackoutAt(s.start, s.end)
            val why = when {
                closure != null -> "$closure"
                blackout != null -> "예외 일정(${blackout.label})과 겹침"
                // 강습은 모든 운동 규칙보다 우선 → 휴장·예외 일정만 아니면 넣음
                else -> null
            }
            if (why != null) {
                skippedFixed += s to why
                reasons += Reasons.fixedSkipped(s, why)
            } else {
                placed += s
            }
        }
        return placed
    }

    // ---------- ② 최소 횟수 ----------

    private fun placeMinimums(base: List<PlannedSession>): List<PlannedSession> {
        val have = weekDone() + base
        fun need(c: Category) =
            (r.minimums.filter { it.category == c }.maxOfOrNull { it.perWeek } ?: 0) - have.count { it.activity.category == c }

        val runs = need(Category.RUN).coerceAtLeast(0)
        val gyms = need(Category.GYM).coerceAtLeast(0)
        val legs = (r.legs.minPerWeek - have.count { it.leg }).coerceAtLeast(0)

        // 제약이 센 것부터: 러닝 → 하체 → 헬스. 수영(강습 외)은 ③ 빈 시간 채우기에서 자유수영으로
        var items = List(runs) { Item(Activity.RUN) } +
            List(legs) { Item(Activity.GYM, leg = true) } +
            List((gyms - legs).coerceAtLeast(0)) { Item(Activity.GYM) }
        val gymTarget = gyms

        // 후보 칸이 아예 없는 종목은 먼저 뺌 (못 채운 건 recordUnmet 이 이유를 남김)
        val dropped = mutableListOf<Item>()
        items.distinct().forEach { kind ->
            if (candidates(kind.activity, kind.leg).all { weatherBlocked(it) }) {
                dropped += items.filter { it == kind }
                items = items.filter { it != kind }
            }
        }

        var result = search(items, base, allowFreezing = false)
        if (result == null) result = search(items, base, allowFreezing = true)
        val dropOrder = listOf(Item(Activity.RUN), Item(Activity.GYM, true), Item(Activity.GYM))
        while (result == null && items.isNotEmpty()) {
            val kind = dropOrder.first { it in items }
            val idx = items.lastIndexOf(kind)
            items = items.toMutableList().apply { removeAt(idx) }
            dropped += kind
            // 하체를 못 넣어도 헬스 횟수는 일반 헬스로 채움
            if (kind.leg && items.count { it.activity == Activity.GYM } < gymTarget) items = items + Item(Activity.GYM)
            result = search(items, base, allowFreezing = true)
        }
        val out = result ?: base
        out.filter { it.activity == Activity.RUN && !it.done && logic.isFreezing(it.start, it.end) }.forEach {
            reasons += Reasons.freezingRun(it, logic.minTemp(it.start, it.end))
        }
        return out
    }

    /** 다른 운동과 상관없이 날씨만으로도 안 되는 칸인지 */
    private fun weatherBlocked(slot: Slot) =
        logic.isOutdoor(slot.session.activity) && r.weather.precipitationForbidden &&
            logic.hasPrecip(slot.session.start, slot.session.end)

    private fun search(items: List<Item>, base: List<PlannedSession>, allowFreezing: Boolean): List<PlannedSession>? {
        nodes = 0
        val cache = items.distinct().associateWith { candidates(it.activity, it.leg) }
        fun dfs(i: Int, placed: List<PlannedSession>): List<PlannedSession>? {
            if (i == items.size) return placed
            if (++nodes > NODE_LIMIT) return null
            val item = items[i]
            // 같은 종목은 시간순으로만 (중복 탐색 줄임). 하체는 서로 멀리 두려고 순서 제한 없음
            val prevSame = if (item.leg) null
            else (0 until i).lastOrNull { items[it] == item }?.let { placed[base.size + it] }
            val opts = cache.getValue(item)
                .filter { prevSame == null || it.session.start > prevSame.start }
                .filter { violation(it.session, placed, allowFreezing) == null }
                .sortedWith(preference(item, placed))
            for (o in opts) dfs(i + 1, placed + o.session)?.let { return it }
            return null
        }
        return dfs(0, base)
    }

    private fun preference(item: Item, placed: List<PlannedSession>): Comparator<Slot> {
        val all = done + placed
        fun load(s: Slot) = all.count { it.date == s.session.date }
        val byDefault = compareBy<Slot>({ load(it) }, { it.session.date }, { it.windowIndex }, { it.session.start })
        return when {
            item.activity == Activity.RUN -> compareBy<Slot>(
                { logic.isFreezing(it.session.start, it.session.end) },
                // 영하뿐이면 가장 덜 추운 때
                { if (logic.isFreezing(it.session.start, it.session.end)) -(logic.minTemp(it.session.start, it.session.end) ?: 0.0) else 0.0 },
                { load(it) },
                { it.session.date },
                { -(logic.minTemp(it.session.start, it.session.end) ?: Double.NEGATIVE_INFINITY) },
                { it.windowIndex },
                { it.session.start },
            )
            item.leg -> {
                val legDays = all.filter { it.leg }.map { it.date }
                compareBy<Slot>(
                    { load(it) },
                    // 하체 날은 서로 멀리
                    { s -> -(legDays.minOfOrNull { abs(ChronoUnit.DAYS.between(it, s.session.date)) } ?: 0L) },
                    { it.session.date },
                    { it.windowIndex },
                    { it.session.start },
                )
            }
            else -> byDefault
        }
    }

    private fun recordUnmet(placed: List<PlannedSession>) {
        val have = weekDone() + placed
        for (m in r.minimums) {
            val count = have.count { it.activity.category == m.category }
            if (count >= m.perWeek) continue
            unmet += UnmetRule(Reasons.minimumRule(m), Reasons.unmetMinimum(m, count, diagnose(m.category)))
        }
        val legs = have.count { it.leg }
        if (legs < r.legs.minPerWeek) {
            unmet += UnmetRule(Reasons.legRule(r.legs), "하체 ${r.legs.minPerWeek}회 중 ${legs}회만 넣음 — 러닝·하체 간격 규칙과 겹침.")
        }
    }

    private fun diagnose(c: Category): String {
        val act = when (c) { Category.RUN -> Activity.RUN; Category.GYM -> Activity.GYM; Category.SWIM -> Activity.FREE_SWIM }
        val fac = logic.facility(act)
        if (fac != null) {
            val closures = days.mapNotNull { logic.facilityClosure(fac, it) }
            if (closures.size == days.size) return "${closures.distinct().joinToString(", ")}"
        }
        val slots = candidates(act, false)
        if (slots.isEmpty()) return "운동 가능한 시간이 없음"
        if (logic.isOutdoor(act) && r.weather.precipitationForbidden &&
            slots.all { logic.hasPrecip(it.session.start, it.session.end) }
        ) return "운동 가능한 시간이 모두 비·눈 예보 (비·눈 금지가 우선)"
        return "하루 최대 개수·운동 사이 간격·하체 규칙과 겹침"
    }

    // ---------- ③·④ 빈 시간 채우기 ----------

    private fun fillExtras(base: List<PlannedSession>): List<PlannedSession> {
        val placed = base.toMutableList()
        val cache = r.fill.extraOrder.associateWith { candidates(it, false) }
        val limit = minOf(r.fill.extraUpToPerDay, r.day.maxSessions)
        for (date in days) {
            while ((done + placed).count { it.date == date } < limit) {
                val pick = r.fill.extraOrder.firstNotNullOfOrNull { act ->
                    cache.getValue(act)
                        .filter { it.session.date == date && violation(it.session, placed, allowFreezing = false) == null }
                        .sortedWith(preference(Item(act), placed))
                        .firstOrNull()
                } ?: break
                placed += pick.session
            }
        }
        return placed
    }

    // ---------- 후보 칸 만들기 ----------

    internal fun candidates(a: Activity, leg: Boolean): List<Slot> {
        val ar = logic.activityRule(a)
        val fac = logic.facility(a)
        return days.flatMap { date ->
            if (fac != null && logic.facilityClosure(fac, date) != null) return@flatMap emptyList()
            logic.windowsFor(date).flatMapIndexed { wi, w ->
                var segs: List<Span> = listOf(date.atTime(w.start) to date.atTime(w.end))
                r.exceptions.forEach { segs = RuleLogic.subtract(segs, it.start to it.end) }
                req.replanFrom?.let { f -> segs = RuleLogic.subtract(segs, LocalDateTime.MIN to f) }
                if (fac != null) {
                    segs = RuleLogic.intersect(segs, date.atTime(fac.open.start) to date.atTime(fac.open.end))
                    fac.closedTimes.forEach { segs = RuleLogic.subtract(segs, date.atTime(it.start) to date.atTime(it.end)) }
                }
                segs.flatMap { (s, e) -> slotsIn(s, e, a, ar, fac, wi, leg) }
            }
        }
    }

    private fun slotsIn(
        s: LocalDateTime, e: LocalDateTime, a: Activity, ar: ActivityRule, fac: Facility?, wi: Int, leg: Boolean,
    ): List<Slot> {
        val len = ChronoUnit.MINUTES.between(s, e).toInt()
        val d = minOf(ar.preferredMinutes, r.session.maxMinutes, len)
        if (d < r.session.minMinutes) return emptyList()
        val step = if (fac?.startOnTheHour == true) 60 else r.slotStepMinutes
        val dayStart = s.toLocalDate().atStartOfDay()
        val fromMidnight = ChronoUnit.MINUTES.between(dayStart, s)
        var t = dayStart.plusMinutes(((fromMidnight + step - 1) / step) * step)
        val out = mutableListOf<Slot>()
        while (!t.plusMinutes(d.toLong()).isAfter(e)) {
            out += Slot(PlannedSession(a, t, d, leg = leg), wi)
            t = t.plusMinutes(step.toLong())
        }
        return out
    }

    // ---------- 제약 확인 ----------

    /** 이 운동을 넣으면 깨지는 규칙 (없으면 null). */
    internal fun violation(s: PlannedSession, placed: List<PlannedSession>, allowFreezing: Boolean): String? {
        val all = done + placed
        val sameDay = all.filter { it.date == s.date }
        run {
            if (sameDay.size >= r.day.maxSessions) return "하루 최대"
            val gap = r.day.minGapMinutes.toLong()
            for (o in sameDay) {
                val okGap = if (o.start <= s.start) !s.start.isBefore(o.end.plusMinutes(gap))
                else !o.start.isBefore(s.end.plusMinutes(gap))
                if (!okGap) return "간격"
            }
            if (RuleLogic.isWeekday(s.date) && sameDay.size + 1 >= 2) {
                val withS = all + s
                val doubles = days.count { d -> RuleLogic.isWeekday(d) && withS.count { it.date == d } >= 2 }
                if (doubles > r.day.weekdayDoubleMaxPerWeek) return "평일 2개"
            }
        }
        if (s.leg) {
            if (all.count { it.leg && inWeek(it.date) } + 1 > r.legs.maxPerWeek) return "하체 최대"
            val legGap = r.legs.minHoursBetween.toLong()
            if (all.any { o -> o.leg && RuleLogic.legTooClose(o, s, legGap) }) return "하체 간격"
            if (r.legs.noRunOnLegDayAndNextDay &&
                all.any { it.activity == Activity.RUN && (it.date == s.date || it.date == s.date.plusDays(1)) }
            ) return "하체-러닝"
        }
        if (s.activity == Activity.RUN && r.legs.noRunOnLegDayAndNextDay &&
            all.any { it.leg && (it.date == s.date || it.date == s.date.minusDays(1)) }
        ) return "하체-러닝"
        if (logic.isOutdoor(s.activity)) {
            if (r.weather.precipitationForbidden && logic.hasPrecip(s.start, s.end)) return "비·눈"
            if (!allowFreezing && logic.isFreezing(s.start, s.end)) return "영하"
        }
        return null
    }

    // ---------- 바꾼 이유 문장 ----------

    private fun weatherReasons(placed: List<PlannedSession>) {
        val outdoor = r.activities.filter { it.outdoor }.map { it.activity }
        if (outdoor.isEmpty()) return
        val rainDays = outdoor.flatMap { a ->
            candidates(a, false).filter { logic.hasPrecip(it.session.start, it.session.end) }.map { it.session.date }
        }.distinct().sorted()
        if (rainDays.isNotEmpty()) reasons += Reasons.rainDays(rainDays, outdoor)
        placed.filter { !it.done && it.activity in outdoor && logic.weatherMissing(it.start, it.end) }
            .forEach { reasons += Reasons.weatherMissing(it) }
    }

    private fun diffReasons(previous: List<PlannedSession>, placed: List<PlannedSession>) {
        fun key(s: PlannedSession) = Triple(s.activity, s.start, s.leg)
        val from = req.replanFrom
        val prev = previous.filter { !it.done && (from == null || it.start >= from) && inWeek(it.date) }
        val now = placed.filter { !it.done }
        val nowKeys = now.map(::key).toSet()
        val prevKeys = prev.map(::key).toSet()
        prev.filter { key(it) !in nowKeys }.forEach { reasons += Reasons.removed(it) }
        now.filter { key(it) !in prevKeys }.forEach { reasons += Reasons.added(it) }
    }

    companion object {
        const val NODE_LIMIT = 20_000
    }
}
