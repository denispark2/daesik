package app.workoutplanner.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

internal typealias Span = Pair<LocalDateTime, LocalDateTime>

/** 규칙 해석 (계획기와 확인표가 같이 씀). */
internal class RuleLogic(val rules: Rules, weatherList: List<WeatherHour>) {
    private val weather = weatherList.associateBy { it.time.truncatedTo(ChronoUnit.HOURS) }

    fun activityRule(a: Activity) =
        rules.activities.firstOrNull { it.activity == a } ?: ActivityRule(a, 60)

    fun facility(a: Activity): Facility? =
        activityRule(a).facility?.let { id -> rules.facilities.firstOrNull { it.id == id } }

    fun isOutdoor(a: Activity) = activityRule(a).outdoor

    fun windowsFor(date: LocalDate): List<TimeWindow> =
        rules.availability.firstOrNull { date.dayOfWeek in it.days }?.windows.orEmpty()

    fun blackoutAt(start: LocalDateTime, end: LocalDateTime): Blackout? =
        rules.exceptions.firstOrNull { it.start < end && start < it.end }

    /** 그날 시설이 종일 닫혀 있으면 이유, 아니면 null */
    fun facilityClosure(f: Facility, date: LocalDate): String? {
        f.closures.firstOrNull { date in it }?.let { return "${f.name} ${it.label}" }
        val nth = (date.dayOfMonth - 1) / 7 + 1
        f.closedNthWeekdays.firstOrNull { it.day == date.dayOfWeek && nth in it.nth }?.let {
            return "${f.name} ${it.nth.joinToString("·") { n -> ordinal(n) }}째 ${dayName(it.day)}요일 휴관"
        }
        return null
    }

    fun facilityAllows(f: Facility, start: LocalDateTime, end: LocalDateTime): Boolean {
        val date = start.toLocalDate()
        if (facilityClosure(f, date) != null) return false
        if (start < date.atTime(f.open.start) || end > date.atTime(f.open.end)) return false
        if (f.closedTimes.any { date.atTime(it.start) < end && start < date.atTime(it.end) }) return false
        if (f.startOnTheHour && start.minute != 0) return false
        return true
    }

    private fun hours(start: LocalDateTime, end: LocalDateTime): List<LocalDateTime> {
        val out = mutableListOf<LocalDateTime>()
        var h = start.truncatedTo(ChronoUnit.HOURS)
        while (h < end) {
            out += h
            h = h.plusHours(1)
        }
        return out
    }

    fun hasPrecip(start: LocalDateTime, end: LocalDateTime) =
        hours(start, end).any { weather[it]?.precipitation == true }

    fun minTemp(start: LocalDateTime, end: LocalDateTime): Double? =
        hours(start, end).mapNotNull { weather[it]?.tempC }.minOrNull()

    fun isFreezing(start: LocalDateTime, end: LocalDateTime) =
        minTemp(start, end)?.let { it < rules.weather.freezingBelowC } ?: false

    fun weatherMissing(start: LocalDateTime, end: LocalDateTime) =
        hours(start, end).any { weather[it] == null }

    companion object {
        /** 두 운동 사이(앞 운동의 끝 ~ 뒤 운동 시작)가 hours 시간 미만인지 */
        fun legTooClose(a: PlannedSession, b: PlannedSession, hours: Long): Boolean {
            val (first, second) = if (a.start <= b.start) a to b else b to a
            return second.start.isBefore(first.end.plusHours(hours))
        }

        fun isWeekday(d: LocalDate) = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY

        fun subtract(segs: List<Span>, cut: Span): List<Span> = segs.flatMap { (s, e) ->
            if (cut.second <= s || cut.first >= e) listOf(s to e)
            else listOfNotNull(
                (s to cut.first).takeIf { cut.first > s },
                (cut.second to e).takeIf { cut.second < e },
            )
        }

        fun intersect(segs: List<Span>, keep: Span): List<Span> = segs.mapNotNull { (s, e) ->
            val a = maxOf(s, keep.first)
            val b = minOf(e, keep.second)
            if (a < b) a to b else null
        }
    }
}

private val DAY_NAMES = arrayOf("월", "화", "수", "목", "금", "토", "일")
internal fun dayName(d: DayOfWeek) = DAY_NAMES[d.value - 1]
private fun ordinal(n: Int) = when (n) {
    1 -> "첫"; 2 -> "둘"; 3 -> "셋"; 4 -> "넷"; 5 -> "다섯"; else -> "$n"
}
