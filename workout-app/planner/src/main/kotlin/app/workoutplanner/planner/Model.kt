@file:UseSerializers(
    LocalDateSerializer::class,
    LocalTimeSerializer::class,
    LocalDateTimeSerializer::class,
    DayOfWeekSerializer::class,
)

package app.workoutplanner.planner

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class Category(val label: String) { SWIM("수영"), GYM("헬스"), RUN("러닝") }

enum class Activity(val label: String, val category: Category) {
    SWIM_LESSON("수영 강습", Category.SWIM),
    FREE_SWIM("자유수영", Category.SWIM),
    GYM("헬스", Category.GYM),
    RUN("러닝", Category.RUN),
}

// ---------- 규칙 (사용자가 바꾸는 설정 데이터) ----------

@Serializable
data class TimeWindow(val start: LocalTime, val end: LocalTime)

/** 요일별 운동 가능 시간대. windows 는 먼저 쓰고 싶은 순서대로. */
@Serializable
data class DayWindows(val days: List<DayOfWeek>, val windows: List<TimeWindow>)

/** 예외 일정 (이 사이엔 운동 불가). */
@Serializable
data class Blackout(val start: LocalDateTime, val end: LocalDateTime, val label: String)

@Serializable
data class SessionLimits(val minMinutes: Int = 30, val maxMinutes: Int = 80)

@Serializable
data class DayLimits(
    val maxSessions: Int = 2,
    /** 앞 운동의 실제 끝 시각 ~ 다음 운동 시작 */
    val minGapMinutes: Int = 120,
    val weekdayDoubleMaxPerWeek: Int = 2,
)

/** 종목별 계획 칸 길이(실제 운동 시간, 준비 포함)와 시설·야외 여부. */
@Serializable
data class ActivityRule(
    val activity: Activity,
    val preferredMinutes: Int,
    val facility: String? = null,
    val outdoor: Boolean = false,
)

/** 종목별 주 최소 횟수 (상한 없음). */
@Serializable
data class Minimum(val category: Category, val perWeek: Int)

@Serializable
data class WeatherRules(
    /** 야외 운동은 비·눈 시간에 절대 안 넣음 (최소 횟수보다 우선) */
    val precipitationForbidden: Boolean = true,
    /** 이 기온 미만이면 영하로 봄 */
    val freezingBelowC: Double = 0.0,
    /** 영하 시간은 최소 횟수를 못 채울 때만 (가장 덜 추운 때) */
    val freezingOnlyForMinimum: Boolean = true,
)

@Serializable
data class LegRules(
    val minPerWeek: Int = 2,
    val maxPerWeek: Int = 2,
    val noRunOnLegDayAndNextDay: Boolean = true,
    /** 하체 운동 사이 최소 간격 (앞 하체의 실제 끝 ~ 다음 하체 시작, 시간) */
    val minHoursBetween: Int = 24,
)

/** 고정 일정 (예: 수영 강습). 운동 가능 시간대 밖이어도 넣음. */
@Serializable
data class FixedSession(
    val activity: Activity,
    val day: DayOfWeek,
    val start: LocalTime,
    val minutes: Int,
    val label: String,
)

/** 매월 n번째 요일 휴관 (예: 둘째·넷째 월요일). */
@Serializable
data class NthWeekday(val day: DayOfWeek, val nth: List<Int>)

/** 날짜 범위 (양 끝 포함). */
@Serializable
data class DateRange(val from: LocalDate, val to: LocalDate, val label: String) {
    operator fun contains(date: LocalDate) = !date.isBefore(from) && !date.isAfter(to)
}

@Serializable
data class Facility(
    val id: String,
    val name: String,
    val open: TimeWindow,
    /** 휴식·휴장 시간 (예: 12:00~13:00) */
    val closedTimes: List<TimeWindow> = emptyList(),
    val closedNthWeekdays: List<NthWeekday> = emptyList(),
    /** 휴장 기간 */
    val closures: List<DateRange> = emptyList(),
    /** 정각에 시작 (매시 50분~정각 휴식 → 50분 운동 + 10분 휴식) */
    val startOnTheHour: Boolean = false,
)

@Serializable
data class FillRules(
    /** 최소 횟수를 채운 뒤 빈 시간을 채우는 순서 */
    val extraOrder: List<Activity> = listOf(Activity.FREE_SWIM, Activity.GYM, Activity.RUN),
    /** 빈 시간 채우기는 하루 운동이 이 개수가 될 때까지만 (1 = 운동 없는 날만 채움) */
    val extraUpToPerDay: Int = 1,
)

@Serializable
data class Rules(
    val availability: List<DayWindows>,
    val exceptions: List<Blackout> = emptyList(),
    val session: SessionLimits = SessionLimits(),
    val day: DayLimits = DayLimits(),
    val activities: List<ActivityRule>,
    val minimums: List<Minimum>,
    val weather: WeatherRules = WeatherRules(),
    val legs: LegRules = LegRules(),
    val fixed: List<FixedSession> = emptyList(),
    val facilities: List<Facility> = emptyList(),
    /** 기록 보정: 실제 운동 시간 = 기록 + N분 (스트레칭·준비) */
    val recordPaddingMinutes: Int = 15,
    val fill: FillRules = FillRules(),
    /** 시작 시각 후보 간격 (분) */
    val slotStepMinutes: Int = 30,
)

// ---------- 입력 ----------

/** 시간별 날씨. time 은 그 시간의 시작 (예: 07:00 → 07:00~08:00). */
@Serializable
data class WeatherHour(val time: LocalDateTime, val precipitation: Boolean, val tempC: Double)

/** 이미 한 운동 (워치 기록). 실제 끝 = 시작 + 기록 + 보정. */
@Serializable
data class DoneRecord(
    val activity: Activity,
    val start: LocalDateTime,
    val recordedMinutes: Int,
    val leg: Boolean = false,
)

@Serializable
data class PlanRequest(
    val rules: Rules,
    /** 주 시작일 (월요일) */
    val weekStart: LocalDate,
    val weather: List<WeatherHour> = emptyList(),
    val done: List<DoneRecord> = emptyList(),
    /** 다시 짤 때: 이 시각 이후만 새로 배치 (이전은 기록만 반영) */
    val replanFrom: LocalDateTime? = null,
    /** 다시 짤 때 바뀐 점 문장을 만들기 위한 이전 계획 */
    val previous: List<PlannedSession>? = null,
)

// ---------- 출력 ----------

@Serializable
data class PlannedSession(
    val activity: Activity,
    val start: LocalDateTime,
    /** 실제 운동 시간 (준비·스트레칭 포함) */
    val minutes: Int,
    val leg: Boolean = false,
    val fixed: Boolean = false,
    val done: Boolean = false,
    val note: String? = null,
) {
    val end: LocalDateTime get() = start.plusMinutes(minutes.toLong())
    val date: LocalDate get() = start.toLocalDate()
}

enum class CheckStatus(val mark: String) { OK("✅"), NOT_POSSIBLE("⚠️"), VIOLATED("❌") }

@Serializable
data class RuleCheck(val rule: String, val status: CheckStatus, val detail: String)

@Serializable
data class UnmetRule(val rule: String, val reason: String)

@Serializable
data class WeekPlan(
    val weekStart: LocalDate,
    /** 기록(done)과 계획, 시간순 */
    val sessions: List<PlannedSession>,
    val checks: List<RuleCheck>,
    val reasons: List<String>,
    val unmet: List<UnmetRule>,
)
