package app.workoutplanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.workoutplanner.data.Celebrate
import app.workoutplanner.data.WorkoutModel
import app.workoutplanner.planner.Category
import app.workoutplanner.planner.CheckStatus
import app.workoutplanner.planner.Minimum
import app.workoutplanner.planner.PlanJson
import app.workoutplanner.planner.PlannedSession
import app.workoutplanner.planner.Reasons
import app.workoutplanner.planner.Rules
import app.workoutplanner.reminder.Reminders
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val TABS = listOf("오늘", "이번 주", "기록", "변경", "규칙")

@Composable
fun WorkoutApp(model: WorkoutModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                when (tab) {
                    0 -> TodayScreen(model) { tab = 3 }
                    1 -> WeekScreen(model)
                    2 -> RecordScreen()
                    3 -> ReplanScreen(model)
                    else -> RulesScreen(model) { tab = 3 }
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(Modifier.fillMaxWidth().background(C.bg).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                TABS.forEachIndexed { i, name ->
                    Text(
                        name, fontSize = 12.sp,
                        fontWeight = if (i == tab) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == tab) C.ink else Color(0xFF9A9A9E),
                        modifier = Modifier.weight(1f).clickable { tab = i }.padding(vertical = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        model.celebrate?.let { Celebration(it) { model.celebrate = null } }
    }
}

// ---------- ① 오늘 ----------

@Composable
private fun TodayScreen(model: WorkoutModel, toReplan: () -> Unit) {
    val today = LocalDate.now()
    val plan = model.plan ?: return
    val list = plan.sessions.filter { it.date == today }
    val dayNo = java.time.temporal.ChronoUnit.DAYS.between(plan.weekStart, today) + 1
    Title("오늘 할 운동", "${today.monthValue}월 ${today.dayOfMonth}일 (${Reasons.day(today).takeLast(2).take(1)}) · 주간 계획 ${dayNo}일째")
    Card { Rings(model) }
    Card {
        if (list.isEmpty()) Text("오늘은 계획된 운동이 없어요.", fontSize = 14.sp, color = C.sub)
        list.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = C.line)
            SessionRow(s, model.rules.recordPaddingMinutes, onDone = { model.markDone(s) })
        }
        Why(todayWhy(model, list))
    }
    if (list.any { !it.done }) Pill("오늘은 못 해요 → 다시 짜기", { model.skipToday(); toReplan() }, ghost = true)
}

private fun todayWhy(model: WorkoutModel, list: List<PlannedSession>): String {
    val r = model.plan?.reasons.orEmpty()
    val day = list.firstOrNull()?.let { Reasons.day(it.date) }
    return r.firstOrNull { day != null && it.contains(day) } ?: r.firstOrNull() ?: "규칙대로 짠 계획이에요. 날씨는 다음 단계에서 연결돼요."
}

@Composable
private fun SessionRow(s: PlannedSession, padding: Int, onDone: (() -> Unit)?) {
    val missed = !s.done && s.end < LocalDateTime.now()
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(s.start.format(HM), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF444444), modifier = Modifier.width(48.dp))
        Dot(s)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(Reasons.what(s), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("준비 포함 ${s.minutes}분 (기록 ${s.minutes - padding}분까지)", fontSize = 12.sp, color = C.sub)
        }
        when {
            s.done -> Chip("완료", C.ok, Color(0xFFE3F5EB))
            missed -> Chip("못 함", C.miss, Color(0xFFFDE8E8))
            onDone != null -> Box(Modifier.clickable(onClick = onDone)) { Chip("끝!", Color.White, C.ok) }
            else -> Chip("예정", Color(0xFF555555), Color(0xFFEEF0F3))
        }
    }
}

@Composable
private fun Rings(model: WorkoutModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        listOf(Category.GYM to C.gym, Category.RUN to C.run, Category.SWIM to C.swim).forEach { (c, col) ->
            val (have, need) = model.progress(c)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(64.dp)) {
                        val st = 7.dp.toPx()
                        val sz = Size(size.width - st, size.height - st)
                        val tl = Offset(st / 2, st / 2)
                        drawArc(Color(0xFFECEEF1), 0f, 360f, false, tl, sz, style = Stroke(st))
                        val p = if (need == 0) 1f else (have.toFloat() / need).coerceAtMost(1f)
                        drawArc(col, -90f, 360f * p, false, tl, sz, style = Stroke(st, cap = StrokeCap.Round))
                    }
                    Text("$have/$need", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(c.label, fontSize = 11.sp, color = C.sub, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

// ---------- ② 이번 주 ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekScreen(model: WorkoutModel) {
    val plan = model.plan ?: return
    val end = plan.weekStart.plusDays(6)
    Title("이번 주 계획", "${plan.weekStart.monthValue}/${plan.weekStart.dayOfMonth} ~ ${end.monthValue}/${end.dayOfMonth}")
    Row(Modifier.padding(start = 6.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("헬스" to C.gym, "하체" to C.leg, "러닝" to C.run, "수영" to C.swim).forEach { (n, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                Text(" $n", fontSize = 11.sp, color = C.sub)
            }
        }
    }
    Card {
        (0L..6L).forEach { i ->
            val d = plan.weekStart.plusDays(i)
            if (i > 0) HorizontalDivider(color = C.line)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(48.dp)) {
                    Text(Reasons.day(d).takeLast(2).take(1), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (d == LocalDate.now()) C.ok else Color(0xFF333333))
                    Text("${d.monthValue}/${d.dayOfMonth}", fontSize = 11.sp, color = C.sub)
                }
                val list = plan.sessions.filter { it.date == d }
                if (list.isEmpty()) Text("쉼", fontSize = 11.sp, color = C.sub)
                FlowRow {
                    list.forEach { s ->
                        val missed = !s.done && s.end < LocalDateTime.now()
                        Text(
                            "${s.start.format(HM)} ${Reasons.what(s)}${if (s.done) " ✓" else ""}",
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                            textDecoration = if (missed) TextDecoration.LineThrough else null,
                            modifier = Modifier.padding(end = 4.dp, top = 2.dp, bottom = 2.dp).alpha(if (missed) 0.45f else 1f)
                                .clip(RoundedCornerShape(99.dp)).background(color(s)).padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
    Section("규칙 확인")
    Card {
        plan.checks.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = C.line)
            Line("${c.status.mark} ${c.rule}", c.detail, when (c.status) {
                CheckStatus.OK -> C.ok; CheckStatus.NOT_POSSIBLE -> C.warn; CheckStatus.VIOLATED -> C.miss
            })
        }
    }
    if (plan.unmet.isNotEmpty()) {
        Section("지킬 수 없는 규칙")
        Card { plan.unmet.forEach { Why("${it.rule}: ${it.reason}") } }
    }
    Pill("주간 목표 달성 보기 (미리보기)", { model.celebrate = Celebrate.WEEK }, ghost = true)
}

// ---------- ③ 기록 비교 (3단계) ----------

@Composable
private fun RecordScreen() {
    Title("계획 vs 실제", "워치 기록 연결은 다음 단계")
    Card {
        Text("삼성헬스 기록(Health Connect)을 읽어서 계획과 비교하는 화면이에요.", fontSize = 14.sp)
        Why("3단계에서 폰을 연결하면 헬스·러닝·수영 기록이 들어오고, 못 한 운동이 있으면 남은 날을 다시 짜요. 지금은 '오늘' 화면의 「끝!」으로 직접 표시해요.")
    }
}

// ---------- ④ 다시 짠 계획 ----------

@Composable
private fun ReplanScreen(model: WorkoutModel) {
    val rp = model.replan
    Title("계획을 다시 짰어요", rp?.let { "${it.at} · ${it.cause}" } ?: "아직 다시 짠 적 없음")
    if (rp == null) {
        Card { Text("규칙을 바꾸거나 '오늘은 못 해요'를 누르면 남은 날을 다시 짜고 여기에 전→후를 보여줘요.", fontSize = 14.sp) }
        return
    }
    fun key(s: PlannedSession) = Triple(s.activity, s.start, s.leg)
    val afterKeys = rp.after.map(::key).toSet()
    val beforeKeys = rp.before.map(::key).toSet()
    Card {
        Text("바뀐 운동", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        val removed = rp.before.filter { key(it) !in afterKeys }
        val added = rp.after.filter { key(it) !in beforeKeys }
        if (removed.isEmpty() && added.isEmpty()) Text("바뀐 운동 없음", fontSize = 13.sp, color = C.sub)
        removed.forEach {
            Text("${Reasons.at(it)} ${Reasons.what(it)}", fontSize = 13.sp, color = C.sub, textDecoration = TextDecoration.LineThrough)
        }
        added.forEach {
            Text("→ ${Reasons.at(it)} ${Reasons.what(it)}", fontSize = 13.sp, color = C.ok, fontWeight = FontWeight.Bold)
        }
    }
    val why = rp.reasons.filter { !it.startsWith("추가:") && !it.startsWith("빠짐:") }
    if (why.isNotEmpty()) {
        Section("이유")
        Card { why.forEach { Why(it) } }
    }
    Section("남은 계획")
    Card {
        rp.after.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = C.line)
            Line("${Reasons.at(s)} ${Reasons.what(s)}", "${s.minutes}분", color(s))
        }
    }
}

// ---------- ⑤ 내 규칙 ----------

@Composable
private fun RulesScreen(model: WorkoutModel, toReplan: () -> Unit) {
    val ctx = LocalContext.current
    var draft by remember(model.rules) { mutableStateOf(model.rules) }
    var jsonOpen by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    Title("내 규칙", "바꾸고 저장하면 남은 날을 다시 짜요")

    Section("주 최소 횟수 (상한 없음)")
    Card {
        listOf(Category.GYM, Category.RUN, Category.SWIM).forEach { c ->
            Stepper(c.label, draft.minimums.firstOrNull { it.category == c }?.perWeek ?: 0, "회") { draft = draft.withMin(c, it) }
        }
    }
    Section("하체")
    Card {
        Stepper("주 최소", draft.legs.minPerWeek, "회") { draft = draft.copy(legs = draft.legs.copy(minPerWeek = it)) }
        Stepper("주 최대", draft.legs.maxPerWeek, "회") { draft = draft.copy(legs = draft.legs.copy(maxPerWeek = it)) }
        Stepper("하체 사이", draft.legs.minHoursBetween, "시간", step = 12) { draft = draft.copy(legs = draft.legs.copy(minHoursBetween = it)) }
    }
    Section("하루")
    Card {
        Stepper("하루 최대", draft.day.maxSessions, "개") { draft = draft.copy(day = draft.day.copy(maxSessions = it)) }
        Stepper("운동 사이", draft.day.minGapMinutes, "분", step = 30) { draft = draft.copy(day = draft.day.copy(minGapMinutes = it)) }
        Stepper("평일 2개는 주", draft.day.weekdayDoubleMaxPerWeek, "회까지") { draft = draft.copy(day = draft.day.copy(weekdayDoubleMaxPerWeek = it)) }
        Stepper("빈 시간 채우기", draft.fill.extraUpToPerDay, "개까지") { draft = draft.copy(fill = draft.fill.copy(extraUpToPerDay = it)) }
        Stepper("기록 보정", draft.recordPaddingMinutes, "분", step = 5) { draft = draft.copy(recordPaddingMinutes = it) }
    }
    Section("시간·일정 (JSON 편집에서 바꿈)")
    Card {
        draft.availability.forEach { a ->
            Line(a.days.joinToString("") { Reasons.day(LocalDate.of(2027, 3, 1).plusDays(it.value - 1L)).takeLast(2).take(1) },
                a.windows.joinToString(", ") { "${it.start}~${it.end}" })
        }
        draft.fixed.forEach { Line("고정: ${it.label}", "${Reasons.day(LocalDate.of(2027, 3, 1).plusDays(it.day.value - 1L)).takeLast(2).take(1)} ${it.start}") }
        draft.exceptions.forEach { Line("예외: ${it.label}", "${it.start.toString().replace('T', ' ')} ~ ${it.end.toString().replace('T', ' ')}") }
        draft.facilities.forEach { f ->
            Line(f.name, "${f.open.start}~${f.open.end}" + f.closures.joinToString("") { " · ${it.label}" })
        }
    }
    msg?.let { Why(it) }
    Pill("저장하고 다시 짜기", {
        model.saveRules(draft); toReplan()
    })
    Pill("규칙 JSON 편집", { jsonOpen = true }, ghost = true)
    Pill("rules.json 가져오기", {
        val f = File(ctx.getExternalFilesDir(null), "rules.json")
        msg = if (!f.exists()) "파일 없음: ${f.absolutePath}" else runCatching { draft = PlanJson.rules(f.readText()); "가져옴 — 저장을 눌러야 적용돼요" }
            .getOrElse { "읽기 실패: ${it.message}" }
    }, ghost = true)
    Pill("알림 테스트 (10초 뒤)", { Reminders.test(ctx); msg = "10초 뒤 알림이 와요 (알림 권한 필요)" }, ghost = true)

    if (jsonOpen) JsonDialog(draft, onClose = { jsonOpen = false }) { draft = it; jsonOpen = false }
}

private fun Rules.withMin(c: Category, n: Int): Rules =
    copy(minimums = minimums.filter { it.category != c } + Minimum(c, n))

@Composable
private fun Stepper(label: String, value: Int, unit: String, step: Int = 1, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Round("−") { onChange((value - step).coerceAtLeast(0)) }
        Text("$value$unit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.ok, modifier = Modifier.width(76.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Round("+") { onChange(value + step) }
    }
}

@Composable
private fun Round(t: String, onClick: () -> Unit) =
    Box(Modifier.size(32.dp).clip(CircleShape).background(C.soft).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(t, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }

@Composable
private fun JsonDialog(rules: Rules, onClose: () -> Unit, onApply: (Rules) -> Unit) {
    var text by remember { mutableStateOf(PlanJson.toJson(rules)) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("규칙 JSON") },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 420.dp))
                err?.let { Text(it, color = C.miss, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton({ runCatching { PlanJson.rules(text) }.onSuccess(onApply).onFailure { err = "형식 오류: ${it.message?.take(120)}" } }) { Text("적용") }
        },
        dismissButton = { TextButton(onClose) { Text("닫기") } },
    )
}

