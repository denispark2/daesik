package app.workoutplanner.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.workoutplanner.data.Celebrate
import kotlinx.coroutines.launch
import kotlin.random.Random

private data class Confetti(val x: Float, val dx: Float, val rot: Float, val delay: Float, val color: Color)

/** 목표 달성 축하: 배지 튀어나옴 + 메달 회전 + 색종이 (시안의 pop·spin·fall). */
@Composable
fun Celebration(kind: Celebrate, onClose: () -> Unit) {
    val dim = remember { Animatable(0f) }
    val pop = remember { Animatable(0.4f) }
    val spin = remember { Animatable(0f) }
    val fall = remember { Animatable(0f) }
    val pieces = remember {
        val colors = listOf(C.run, C.swim, C.gym, C.leg, C.ok, C.warn)
        List(60) { Confetti(Random.nextFloat(), Random.nextFloat() * 160f - 80f, Random.nextFloat() * 720f, Random.nextFloat() * 0.35f, colors[it % colors.size]) }
    }
    LaunchedEffect(kind) {
        launch { dim.animateTo(1f, tween(250)) }
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
        launch { spin.animateTo(720f, tween(1100, delayMillis = 150, easing = FastOutSlowInEasing)) }
        launch { fall.animateTo(1f, tween(2600, easing = LinearEasing)) }
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xFF141418).copy(alpha = 0.45f * dim.value)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            pieces.forEach { p ->
                val t = ((fall.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                if (t <= 0f) return@forEach
                val pos = Offset(p.x * size.width + p.dx * t * 3, -20f + t * (size.height + 40f))
                rotate(p.rot * t, pos) { drawRect(p.color, pos, Size(16f, 26f)) }
            }
        }
        Column(
            Modifier.width(260.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = dim.value }
                .clip(RoundedCornerShape(30.dp)).background(C.card).padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (kind == Celebrate.WEEK) "🏆" else "🏅", fontSize = 58.sp,
                modifier = Modifier.graphicsLayer { rotationY = spin.value; cameraDistance = 12f * density })
            Text(if (kind == Celebrate.WEEK) "주간 목표 달성!" else "오늘 운동 끝!", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Text(
                if (kind == Celebrate.WEEK) "이번 주 최소 횟수를 모두 채웠어요. 남은 날은 보너스!" else "오늘 계획한 운동을 다 했어요.",
                fontSize = 13.sp, color = C.sub, textAlign = TextAlign.Center,
            )
            Pill("좋아요", onClose)
        }
    }
}
