package app.workoutplanner.planner

import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/** 일 최저·최고 기온 → 시간별 (06시 최저, 15시 최고 단순 곡선). 예보가 하루 단위일 때 테스트용. */
@Serializable
data class DailyWeather(val date: String, val min: Double, val max: Double, val precipitation: Boolean) {
    fun hourly() = (0..23).map { h ->
        val x = cos((h - 15) / 24.0 * 2 * PI)
        WeatherHour(LocalDate.parse(date).atTime(h, 0), precipitation, min + (max - min) * (x + 1) / 2)
    }
}

/**
 * 개인 규칙 테스트. 값은 저장소 밖 data/workout/planner/ (gitignore) 에만 있음.
 * 파일이 없으면 (다른 PC·CI) 건너뜀.
 */
class PersonalRulesTest {
    private val dir = File(System.getProperty("personalDataDir") ?: "", "planner")

    @Test
    fun `개인 규칙 - 참고 정답 주가 규칙 확인표를 모두 통과`() {
        val file = File(dir, "request_ref_week.json")
        assumeTrue(file.exists(), "개인 테스트 파일 없음: $file")
        val weatherFile = File(dir, "daily_weather_ref_week.json")
        val daily: List<DailyWeather> = PlanJson.json.decodeFromString(weatherFile.readText())
        val req = PlanJson.request(file.readText()).copy(weather = daily.flatMap { it.hourly() })
        val p = planWeek(req)
        val md = p.toMarkdown(req.rules)
        File(dir, "result_ref_week.md").writeText(md)
        println(md)

        assertTrue(p.checks.none { it.status == CheckStatus.VIOLATED }, md)
        val week = p.sessions.filter { !it.done }
        assertTrue(week.count { it.activity == Activity.GYM } >= 5, md)
        assertTrue(week.count { it.activity == Activity.RUN } >= 1, md)
        assertEquals(2, week.filter { it.leg }.map { it.date }.distinct().size, md)
        assertTrue(week.none { it.date == req.weekStart.plusDays(5) }, md) // 예외 일정 날
        // 휴장 주: 수영 없음, 이유 있음
        assertTrue(week.none { it.activity.category == Category.SWIM }, md)
        assertTrue(p.unmet.any { it.rule.startsWith("수영") }, md)
    }
}
