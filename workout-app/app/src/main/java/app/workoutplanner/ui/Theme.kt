package app.workoutplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.workoutplanner.planner.Activity
import app.workoutplanner.planner.PlannedSession

/** One UI(삼성헬스) 느낌: 회색 바탕 + 흰 둥근 카드, 큰 제목, 초록 강조, 알약 버튼 (시안 색). */
object C {
    val bg = Color(0xFFF4F4F6)
    val card = Color.White
    val ink = Color(0xFF1A1A1A)
    val sub = Color(0xFF8A8A8E)
    val line = Color(0xFFEDEDF0)
    val run = Color(0xFFF2793D)
    val swim = Color(0xFF2F9AE0)
    val gym = Color(0xFF7B61D9)
    val leg = Color(0xFFD9468A)
    val ok = Color(0xFF1FA463)
    val miss = Color(0xFFE5484D)
    val warn = Color(0xFFE8A317)
    val soft = Color(0xFFF6F7F8)
}

fun color(s: PlannedSession) = if (s.leg) C.leg else color(s.activity)
fun color(a: Activity) = when (a) {
    Activity.RUN -> C.run
    Activity.GYM -> C.gym
    Activity.FREE_SWIM, Activity.SWIM_LESSON -> C.swim
}
fun icon(s: PlannedSession) = when {
    s.leg -> "🦵"
    s.activity == Activity.RUN -> "🏃"
    s.activity == Activity.GYM -> "🏋"
    else -> "🏊"
}

@Composable
fun Title(title: String, sub: String) {
    Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 40.dp, bottom = 16.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.ink)
        Spacer(Modifier.height(6.dp))
        Text(sub, fontSize = 13.sp, color = C.sub)
    }
}

@Composable
fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(26.dp)).background(C.card).padding(16.dp),
        content = content,
    )
}

@Composable
fun Section(text: String) =
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.sub, modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 6.dp))

@Composable
fun Chip(text: String, fg: Color, bg: Color) =
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 9.dp, vertical = 4.dp))

@Composable
fun Why(text: String) =
    Text("💡 $text", fontSize = 12.5.sp, color = Color(0xFF444444), lineHeight = 19.sp,
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.soft).padding(horizontal = 12.dp, vertical = 10.dp))

@Composable
fun Dot(s: PlannedSession) = Box(
    Modifier.size(38.dp).clip(CircleShape).background(color(s).copy(alpha = 0.14f)),
    contentAlignment = Alignment.Center,
) { Text(icon(s), fontSize = 18.sp) }

@Composable
fun Pill(text: String, onClick: () -> Unit, ghost: Boolean = false) = Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    shape = RoundedCornerShape(99.dp),
    colors = ButtonDefaults.buttonColors(containerColor = if (ghost) C.card else C.ok, contentColor = if (ghost) C.ink else Color.White),
) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp)) }

@Composable
fun Line(left: String, right: String, rightColor: Color = C.ok) = Row(
    Modifier.fillMaxWidth().padding(vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(left, fontSize = 13.5.sp, color = C.ink, modifier = Modifier.weight(1f))
    Text(right, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = rightColor)
}
