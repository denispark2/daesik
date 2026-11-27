package app.workoutplanner.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.workoutplanner.MainActivity
import app.workoutplanner.data.Store
import app.workoutplanner.planner.PlannedSession
import app.workoutplanner.planner.Reasons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 운동 30분 전 로컬 알림 (AlarmManager). 정확 알람 권한이 없으면 대략 시각(몇 분 오차)으로. */
object Reminders {
    const val CHANNEL = "workout"
    const val BEFORE_MIN = 30L
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_TEXT = "text"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "운동 알림", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun intent(ctx: Context) = Intent(ctx, ReminderReceiver::class.java)

    fun schedule(ctx: Context, sessions: List<PlannedSession>, padding: Int, old: Set<Int>): Set<Int> {
        val am = ctx.getSystemService(AlarmManager::class.java)
        old.forEach { code ->
            PendingIntent.getBroadcast(ctx, code, intent(ctx), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let { am.cancel(it) }
        }
        val now = System.currentTimeMillis()
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        return sessions.filter { !it.done }.mapNotNull { s ->
            val at = s.start.minusMinutes(BEFORE_MIN).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (at <= now) return@mapNotNull null
            val code = ((s.start.toEpochSecond(ZoneOffset.UTC) / 60) % 100_000_000).toInt()
            val i = intent(ctx)
                .putExtra(EXTRA_TITLE, "${s.start.format(hm)} ${Reasons.what(s)} — 30분 전")
                .putExtra(EXTRA_TEXT, "준비 포함 ${s.minutes}분 (기록 ${s.minutes - padding}분까지). 출발 준비하세요")
            val pi = PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            code
        }.toSet()
    }

    /** 확인용: 10초 뒤 알림 */
    fun test(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val i = intent(ctx).putExtra(EXTRA_TITLE, "테스트 알림").putExtra(EXTRA_TEXT, "운동 30분 전에 이렇게 와요")
        val pi = PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 10_000, pi)
    }

    fun show(ctx: Context, title: String, text: String, id: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    internal fun onAlarm(ctx: Context, intent: Intent) {
        show(ctx, intent.getStringExtra(EXTRA_TITLE) ?: "운동 알림", intent.getStringExtra(EXTRA_TEXT) ?: "", intent.hashCode())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminders.onAlarm(context, intent)
}

/** 폰 재부팅하면 알람이 지워짐 → 저장된 계획으로 다시 걸기. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = Store(context)
                val snap = store.read()
                val plan = snap.plan ?: return@launch
                val codes = Reminders.schedule(context, plan.sessions, snap.rules?.recordPaddingMinutes ?: 15, emptySet())
                store.writeAlarms(codes)
            } finally {
                pending.finish()
            }
        }
    }
}
