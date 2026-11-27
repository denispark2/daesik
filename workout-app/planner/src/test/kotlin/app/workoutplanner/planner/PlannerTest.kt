package app.workoutplanner.planner

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 저장소 테스트는 예시 값만 씀 (개인 규칙은 PersonalRulesTest, data/ 폴더). */
class PlannerTest {
    private val week = LocalDate.of(2027, 3, 1) // 월요일

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    private val rules = Rules(
        availability = listOf(
            DayWindows(listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), listOf(TimeWindow(t(19), t(21)), TimeWindow(t(6, 30), t(7, 30)))),
            DayWindows(listOf(SATURDAY, SUNDAY), listOf(TimeWindow(t(9), t(19)))),
        ),
        activities = listOf(
            ActivityRule(Activity.GYM, 80),
            ActivityRule(Activity.RUN, 60, outdoor = true),
            ActivityRule(Activity.FREE_SWIM, 60, facility = "pool"),
            ActivityRule(Activity.SWIM_LESSON, 60, facility = "pool"),
        ),
        minimums = listOf(Minimum(Category.GYM, 4), Minimum(Category.RUN, 2), Minimum(Category.SWIM, 2)),
        legs = LegRules(minPerWeek = 2, maxPerWeek = 2),
        fixed = listOf(
            FixedSession(Activity.SWIM_LESSON, TUESDAY, t(20), 60, "강습"),
            FixedSession(Activity.SWIM_LESSON, THURSDAY, t(20), 60, "강습"),
        ),
        facilities = listOf(
            Facility(
                id = "pool", name = "수영장", open = TimeWindow(t(6), t(22)),
                closedTimes = listOf(TimeWindow(t(12), t(13))),
                closedNthWeekdays = listOf(NthWeekday(SUNDAY, listOf(1, 3))),
                startOnTheHour = true,
            ),
        ),
    )

    private fun weather(
        precip: (LocalDateTime) -> Boolean = { false },
        temp: (LocalDateTime) -> Double = { 10.0 },
        start: LocalDate = week.minusDays(1),
    ) = (0L until 9 * 24L).map { start.atStartOfDay().plusHours(it) }.map { WeatherHour(it, precip(it), temp(it)) }

    private fun plan(
        r: Rules = rules,
        w: List<WeatherHour> = weather(),
        done: List<DoneRecord> = emptyList(),
        from: LocalDateTime? = null,
        previous: List<PlannedSession>? = null,
    ) = planWeek(PlanRequest(r, week, w, done, from, previous))

    private fun WeekPlan.noViolations() {
        val bad = checks.filter { it.status == CheckStatus.VIOLATED }
        assertTrue(bad.isEmpty(), "규칙 위반: $bad\n${toMarkdown(rules)}")
    }

    private fun WeekPlan.count(c: Category) = sessions.count { it.activity.category == c }
    private fun WeekPlan.status(prefix: String) = checks.first { it.rule.startsWith(prefix) }.status

    @Test
    fun `기본 주 - 모든 규칙 통과, 최소 횟수 채움`() {
        val p = plan()
        println(p.toMarkdown(rules))
        p.noViolations()
        assertTrue(p.checks.all { it.status == CheckStatus.OK })
        assertTrue(p.count(Category.GYM) >= 4)
        assertTrue(p.count(Category.RUN) >= 2)
        assertEquals(2, p.sessions.count { it.leg })
        assertEquals(2, p.sessions.count { it.activity == Activity.SWIM_LESSON })
        // 자유수영은 정각 시작, 12~13시 안 걸침
        p.sessions.filter { it.activity == Activity.FREE_SWIM }.forEach {
            assertEquals(0, it.start.minute)
            assertTrue(it.end.toLocalTime() <= t(12) || it.start.toLocalTime() >= t(13))
        }
        // 첫째 일요일 휴관
        assertTrue(p.sessions.none { it.activity == Activity.FREE_SWIM && it.date == week.plusDays(6) })
    }

    @Test
    fun `비·눈은 절대 금지 - 일주일 내내 비면 러닝 0회, 이유 표시`() {
        val p = plan(w = weather(precip = { true }))
        p.noViolations()
        assertEquals(0, p.count(Category.RUN))
        assertEquals(CheckStatus.NOT_POSSIBLE, p.status("러닝 주"))
        assertTrue(p.unmet.any { it.rule.startsWith("러닝") && "비·눈" in it.reason })
        assertTrue(p.count(Category.GYM) >= 4)
    }

    @Test
    fun `비 오는 시간은 피해서 러닝`() {
        val rainy = setOf(week, week.plusDays(1), week.plusDays(2))
        val p = plan(w = weather(precip = { it.toLocalDate() in rainy }))
        p.noViolations()
        assertTrue(p.count(Category.RUN) >= 2)
        assertTrue(p.sessions.none { it.activity == Activity.RUN && it.date in rainy })
        assertTrue(p.reasons.any { it.startsWith("비·눈 예보") })
    }

    @Test
    fun `영하뿐이면 최소 횟수만, 가장 덜 추운 때`() {
        val warmest = setOf(week.plusDays(2).atTime(19, 0), week.plusDays(5).atTime(14, 0))
        val p = plan(w = weather(temp = { if (it in warmest) -1.0 else -6.0 }))
        p.noViolations()
        val runs = p.sessions.filter { it.activity == Activity.RUN }
        assertEquals(2, runs.size)
        assertEquals(warmest, runs.map { it.start }.toSet())
        assertTrue(p.reasons.any { "영하" in it })
    }

    @Test
    fun `하체 날과 다음 날 러닝 금지 - 지난주 기록 포함`() {
        val done = listOf(DoneRecord(Activity.GYM, week.minusDays(1).atTime(10, 0), 50, leg = true))
        val p = plan(done = done)
        p.noViolations()
        assertTrue(p.sessions.none { it.activity == Activity.RUN && it.date == week })
        val legDays = p.sessions.filter { it.leg }.map { it.date }
        p.sessions.filter { it.activity == Activity.RUN }.forEach { run ->
            assertTrue(run.date !in legDays && run.date.minusDays(1) !in legDays)
        }
    }

    @Test
    fun `휴장 기간엔 수영 빼고 이유 남김`() {
        val closed = rules.copy(facilities = rules.facilities.map {
            it.copy(closures = listOf(DateRange(week.plusDays(2), week.plusDays(4), "휴장")))
        })
        val p = plan(r = closed)
        p.noViolations()
        val closedDays = (2L..4L).map { week.plusDays(it) }
        assertTrue(p.sessions.none { it.activity.category == Category.SWIM && it.date in closedDays })
        assertEquals(CheckStatus.NOT_POSSIBLE, p.status("고정 일정"))
        assertTrue(p.reasons.any { "강습" in it && "휴장" in it })
    }

    @Test
    fun `예외 일정엔 운동 안 넣음`() {
        val r = rules.copy(exceptions = listOf(Blackout(week.plusDays(5).atTime(0, 0), week.plusDays(6).atTime(23, 59), "출장")))
        val p = plan(r = r)
        p.noViolations()
        assertTrue(p.sessions.none { it.date == week.plusDays(5) || it.date == week.plusDays(6) })
    }

    @Test
    fun `기록 보정 - 실제 끝(기록+15분) 기준 2시간 간격`() {
        val done = listOf(DoneRecord(Activity.GYM, week.atTime(17, 0), 60)) // 실제 끝 18:15 → 20:15 이후만
        val p = plan(done = done)
        p.noViolations()
        assertTrue(p.sessions.none { !it.done && it.date == week && it.start < week.atTime(20, 15) && it.start > week.atTime(17, 0) })
    }

    @Test
    fun `규칙 바꾸면 남은 날만 다시 짬 - 기록 반영, 바뀐 점 문장`() {
        val first = plan()
        val from = week.plusDays(2).atStartOfDay() // 수요일부터 다시
        val done = first.sessions.filter { it.start < from }.map {
            DoneRecord(it.activity, it.start, it.minutes - rules.recordPaddingMinutes, it.leg)
        }
        val changed = rules.copy(minimums = listOf(Minimum(Category.GYM, 6), Minimum(Category.RUN, 2), Minimum(Category.SWIM, 2)))
        val p = planWeek(PlanRequest(changed, week, weather(), done, from, first.sessions))
        println(p.toMarkdown(changed))
        assertTrue(p.checks.none { it.status == CheckStatus.VIOLATED }, p.toMarkdown(changed))
        assertTrue(p.sessions.filter { !it.done }.all { it.start >= from })
        assertTrue(p.count(Category.GYM) >= 6)
        assertTrue(p.reasons.any { it.startsWith("추가:") })
    }

    @Test
    fun `하체 사이 최소 24시간 - 지난주 기록 포함`() {
        val done = listOf(DoneRecord(Activity.GYM, week.minusDays(1).atTime(17, 0), 65, leg = true)) // 실제 끝 일 18:20
        val p = plan(done = done)
        p.noViolations()
        val legs = (done.map { it.start.plusMinutes(80) to it.start } +
            p.sessions.filter { it.leg && !it.done }.map { it.end to it.start }).sortedBy { it.second }
        legs.zipWithNext().forEach { (a, b) -> assertTrue(!b.second.isBefore(a.first.plusHours(24)), "$a → $b") }
    }

    @Test
    fun `빈 시간 하루 2개까지 - 자유수영 먼저, 평일 2개는 주 2회까지`() {
        val r = rules.copy(
            fill = FillRules(extraUpToPerDay = 2),
            facilities = rules.facilities.map { it.copy(closedNthWeekdays = emptyList()) }, // 일요일도 수영장 엶
        )
        val p = plan(r = r)
        println(p.toMarkdown(r))
        p.noViolations()
        val weekdayDoubles = (0L..4L).map { week.plusDays(it) }.count { d -> p.sessions.count { it.date == d } >= 2 }
        assertTrue(weekdayDoubles <= 2)
        assertEquals(2, p.sessions.count { it.date == week.plusDays(5) }) // 토 2개
        assertTrue(p.sessions.any { it.activity == Activity.FREE_SWIM }) // 수영장 여는 날 빈 시간은 자유수영
    }

    @Test
    fun `강습 빠진 주 - 최소 횟수 단계에선 자유수영으로 안 메움`() {
        val closed = rules.copy(facilities = rules.facilities.map {
            it.copy(closures = listOf(DateRange(week, week.plusDays(4), "휴장")))
        })
        val p = plan(r = closed) // extraUpToPerDay=1, 주말은 최소 횟수 운동으로 차 있음
        p.noViolations()
        assertTrue(p.count(Category.GYM) >= 4 && p.count(Category.RUN) >= 2)
        assertEquals(CheckStatus.NOT_POSSIBLE, p.status("수영 주"))
    }

    @Test
    fun `규칙은 JSON으로 저장하고 다시 읽을 수 있음`() {
        val text = PlanJson.toJson(rules)
        assertTrue("\"MONDAY\"" in text && "\"19:00\"" in text)
        assertEquals(rules, PlanJson.rules(text))
    }
}
