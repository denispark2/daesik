package app.workoutplanner.planner

import kotlinx.serialization.json.Json
import java.time.format.DateTimeFormatter

/** 규칙·요청·결과 JSON 저장/읽기. */
object PlanJson {
    val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun rules(text: String): Rules = json.decodeFromString(text)
    fun toJson(rules: Rules): String = json.encodeToString(Rules.serializer(), rules)
    fun request(text: String): PlanRequest = json.decodeFromString(text)
    fun toJson(plan: WeekPlan): String = json.encodeToString(WeekPlan.serializer(), plan)
}

/** 사람이 읽는 표 (마크다운). */
fun WeekPlan.toMarkdown(rules: Rules): String = buildString {
    val hm = DateTimeFormatter.ofPattern("HH:mm")
    appendLine("| 날짜 | 1차 | 2차 |")
    appendLine("|---|---|---|")
    for (i in 0L..6L) {
        val d = weekStart.plusDays(i)
        val list = sessions.filter { it.date == d }.sortedBy { it.start }
        val cells = list.map { s ->
            val mark = if (s.done) "✔ " else ""
            val rec = if (s.done) "기록 ${s.minutes - rules.recordPaddingMinutes}분"
            else "기록 ${s.minutes - rules.recordPaddingMinutes}분까지"
            "$mark${s.start.format(hm)}~${s.end.format(hm)} ${Reasons.what(s)} ($rec)"
        }
        appendLine("| ${Reasons.day(d)} | ${cells.getOrElse(0) { "-" }} | ${cells.drop(1).joinToString(" / ").ifEmpty { "-" }} |")
    }
    appendLine()
    appendLine("| 규칙 | 결과 | 내용 |")
    appendLine("|---|---|---|")
    checks.forEach { appendLine("| ${it.rule} | ${it.status.mark} | ${it.detail} |") }
    if (reasons.isNotEmpty()) {
        appendLine()
        appendLine("바꾼 이유")
        reasons.forEach { appendLine("- $it") }
    }
    if (unmet.isNotEmpty()) {
        appendLine()
        appendLine("지킬 수 없는 규칙")
        unmet.forEach { appendLine("- ${it.rule}: ${it.reason}") }
    }
}
