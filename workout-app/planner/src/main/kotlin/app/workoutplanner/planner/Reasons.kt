package app.workoutplanner.planner

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 정해진 문장 틀. */
object Reasons {
    private val MD = DateTimeFormatter.ofPattern("MM-dd")
    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    fun day(d: LocalDate) = "${d.format(MD)}(${dayName(d.dayOfWeek)})"
    fun at(s: PlannedSession) = "${day(s.date)} ${s.start.format(HM)}"
    fun what(s: PlannedSession) = s.activity.label + if (s.leg) " 하체" else ""

    fun fixedSkipped(s: PlannedSession, why: String) = "${at(s)} ${what(s)} 뺌 — $why."
    fun freezingRun(s: PlannedSession, minTemp: Double?) =
        "비·눈 없는 시간이 영하뿐이라 가장 덜 추운 ${at(s)}에 러닝 넣음 (${minTemp ?: "?"}℃)."
    fun rainDays(days: List<LocalDate>, acts: List<Activity>) =
        "비·눈 예보: ${days.joinToString(", ") { day(it) }} → 그 시간엔 ${acts.joinToString("·") { it.label }} 안 넣음."
    fun weatherMissing(s: PlannedSession) = "${at(s)} ${what(s)}: 아직 날씨 예보 없음 → 전날 다시 확인."
    fun removed(s: PlannedSession) = "빠짐: ${at(s)} ${what(s)}"
    fun added(s: PlannedSession) = "추가: ${at(s)} ${what(s)}"

    fun minimumRule(m: Minimum) = "${m.category.label} 주 ${m.perWeek}회 이상"
    fun legRule(l: LegRules) =
        (if (l.minPerWeek == l.maxPerWeek) "하체 주 ${l.maxPerWeek}회 (최대 ${l.maxPerWeek}회)" else "하체 주 ${l.minPerWeek}~${l.maxPerWeek}회") +
            if (l.minHoursBetween > 0) ", 사이 ${l.minHoursBetween}시간 이상" else ""
    fun unmetMinimum(m: Minimum, count: Int, why: String) =
        "${m.category.label} ${m.perWeek}회 중 ${count}회만 넣음 — $why."
}
