package app.workoutplanner.planner

import java.time.LocalDate

/** 완성된 계획을 규칙마다 다시 확인해 확인표를 만듦 (계획기와 별개로 검사). */
internal class Checker(
    private val req: PlanRequest,
    private val logic: RuleLogic,
    private val days: List<LocalDate>,
    /** 기록 + 계획 (주 밖 기록 포함: 하체 다음 날 확인용) */
    private val all: List<PlannedSession>,
    private val unmet: List<UnmetRule>,
    private val skippedFixed: List<Pair<PlannedSession, String>>,
) {
    private val r = req.rules
    private val week = all.filter { it.date in days }
    private val planned = week.filter { !it.done }

    fun run(): List<RuleCheck> = buildList {
        r.minimums.forEach { add(minimum(it)) }
        add(legs())
        add(legRun())
        add(weather())
        add(fixed())
        add(dayMax())
        add(gap())
        add(weekdayDoubles())
        add(availability())
        add(duration())
        add(facilities())
    }

    private fun check(rule: String, bad: List<String>, okDetail: String) =
        if (bad.isEmpty()) RuleCheck(rule, CheckStatus.OK, okDetail)
        else RuleCheck(rule, CheckStatus.VIOLATED, bad.joinToString(", "))

    private fun countText(list: List<PlannedSession>) =
        "${list.size}회" + if (list.isEmpty()) "" else " (${list.sortedBy { it.start }.joinToString("·") { Reasons.day(it.date) }})"

    private fun minimum(m: Minimum): RuleCheck {
        val name = Reasons.minimumRule(m)
        val list = week.filter { it.activity.category == m.category }
        return when {
            list.size >= m.perWeek -> RuleCheck(name, CheckStatus.OK, countText(list))
            else -> unmet.firstOrNull { it.rule == name }
                ?.let { RuleCheck(name, CheckStatus.NOT_POSSIBLE, it.reason) }
                ?: RuleCheck(name, CheckStatus.VIOLATED, countText(list))
        }
    }

    private fun legs(): RuleCheck {
        val name = Reasons.legRule(r.legs)
        val list = week.filter { it.leg }
        return when {
            list.size > r.legs.maxPerWeek -> RuleCheck(name, CheckStatus.VIOLATED, "최대 초과: " + countText(list))
            all.filter { it.leg }.sortedBy { it.start }.zipWithNext()
                .any { (a, b) -> b.date in days && RuleLogic.legTooClose(a, b, r.legs.minHoursBetween.toLong()) } ->
                RuleCheck(name, CheckStatus.VIOLATED, "하체 사이 ${r.legs.minHoursBetween}시간 미만: " + countText(list))
            list.size >= r.legs.minPerWeek -> RuleCheck(name, CheckStatus.OK, countText(list))
            else -> unmet.firstOrNull { it.rule == name }
                ?.let { RuleCheck(name, CheckStatus.NOT_POSSIBLE, it.reason) }
                ?: RuleCheck(name, CheckStatus.VIOLATED, countText(list))
        }
    }

    private fun legRun(): RuleCheck {
        val bad = if (!r.legs.noRunOnLegDayAndNextDay) emptyList() else
            week.filter { it.activity == Activity.RUN }.filter { run ->
                all.any { it.leg && (it.date == run.date || it.date == run.date.minusDays(1)) }
            }.map { "${Reasons.at(it)} 러닝" }
        return check("하체 날·다음 날 러닝 금지", bad, "지킴")
    }

    private fun weather(): RuleCheck {
        val outdoor = planned.filter { logic.isOutdoor(it.activity) }
        val bad = mutableListOf<String>()
        if (r.weather.precipitationForbidden) {
            outdoor.filter { logic.hasPrecip(it.start, it.end) }.forEach { bad += "${Reasons.at(it)} 비·눈" }
        }
        if (r.weather.freezingOnlyForMinimum) {
            val runMin = r.minimums.filter { it.category == Category.RUN }.maxOfOrNull { it.perWeek } ?: 0
            val freezing = outdoor.filter { logic.isFreezing(it.start, it.end) }
            val warm = week.count { it.activity.category == Category.RUN } - freezing.size
            if (freezing.isNotEmpty() && warm >= runMin) freezing.forEach { bad += "${Reasons.at(it)} 영하 (최소 횟수 이미 채움)" }
        }
        return check("야외: 비·눈 금지, 영하는 최소 횟수용만", bad, "지킴")
    }

    private fun fixed(): RuleCheck {
        val name = "고정 일정 (강습 등)"
        val expected = r.fixed.mapNotNull { f -> days.firstOrNull { it.dayOfWeek == f.day }?.let { f to it.atTime(f.start) } }
            .filter { (_, t) -> req.replanFrom == null || t >= req.replanFrom }
        if (expected.isEmpty()) return RuleCheck(name, CheckStatus.OK, "없음")
        val missing = expected.filter { (f, t) -> week.none { it.activity == f.activity && it.date == t.toLocalDate() } }
        val unexplained = missing.filter { (f, t) -> skippedFixed.none { it.first.activity == f.activity && it.first.start == t } }
        return when {
            unexplained.isNotEmpty() -> RuleCheck(name, CheckStatus.VIOLATED,
                unexplained.joinToString(", ") { (f, t) -> "${Reasons.day(t.toLocalDate())} ${f.label} 빠짐" })
            missing.isNotEmpty() -> RuleCheck(name, CheckStatus.NOT_POSSIBLE,
                skippedFixed.joinToString(" / ") { (s, why) -> "${Reasons.day(s.date)} ${s.note ?: s.activity.label}: $why" })
            else -> RuleCheck(name, CheckStatus.OK, "${expected.size}회 모두 넣음")
        }
    }

    private fun dayMax(): RuleCheck {
        val bad = days.filter { d -> week.count { it.date == d } > r.day.maxSessions }.map { Reasons.day(it) }
        return check("하루 최대 ${r.day.maxSessions}개", bad, "지킴")
    }

    private fun gap(): RuleCheck {
        val bad = mutableListOf<String>()
        for (d in days) {
            val list = week.filter { it.date == d }.sortedBy { it.start }
            list.zipWithNext().forEach { (a, b) ->
                if (b.start.isBefore(a.end.plusMinutes(r.day.minGapMinutes.toLong()))) bad += "${Reasons.at(a)}→${Reasons.at(b)}"
            }
        }
        val h = r.day.minGapMinutes / 60.0
        return check("운동 사이 ${if (h % 1.0 == 0.0) h.toInt() else h}시간 (실제 끝 기준, 기록+${r.recordPaddingMinutes}분)", bad, "지킴")
    }

    private fun weekdayDoubles(): RuleCheck {
        val doubles = days.filter { d -> RuleLogic.isWeekday(d) && week.count { it.date == d } >= 2 }
        val name = "평일 하루 2개는 주 ${r.day.weekdayDoubleMaxPerWeek}회까지"
        return if (doubles.size > r.day.weekdayDoubleMaxPerWeek)
            RuleCheck(name, CheckStatus.VIOLATED, doubles.joinToString("·") { Reasons.day(it) })
        else RuleCheck(name, CheckStatus.OK, "${doubles.size}회")
    }

    private fun availability(): RuleCheck {
        val bad = planned.filter { s ->
            val inBlackout = logic.blackoutAt(s.start, s.end) != null
            val inWindow = s.fixed || logic.windowsFor(s.date).any {
                !s.start.isBefore(s.date.atTime(it.start)) && !s.end.isAfter(s.date.atTime(it.end))
            }
            val tooEarly = req.replanFrom != null && s.start < req.replanFrom
            inBlackout || !inWindow || tooEarly
        }.map { "${Reasons.at(it)} ${Reasons.what(it)}" }
        return check("운동 가능 시간대·예외 일정", bad, "지킴")
    }

    private fun duration(): RuleCheck {
        val bad = planned.filter { !it.fixed && (it.minutes < r.session.minMinutes || it.minutes > r.session.maxMinutes) }
            .map { "${Reasons.at(it)} ${it.minutes}분" }
        return check("1회 ${r.session.minMinutes}~${r.session.maxMinutes}분", bad, "지킴")
    }

    private fun facilities(): RuleCheck {
        val bad = planned.filter { s -> logic.facility(s.activity)?.let { !logic.facilityAllows(it, s.start, s.end) } ?: false }
            .map { "${Reasons.at(it)} ${Reasons.what(it)}" }
        return check("시설 운영시간·휴장·휴식시간", bad, "지킴")
    }
}
