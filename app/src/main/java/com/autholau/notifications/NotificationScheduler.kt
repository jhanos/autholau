package com.autholau.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.autholau.model.Event
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NotificationScheduler {

    const val CHANNEL_ID   = "autholau_events"
    const val CHANNEL_NAME = "Event Reminders"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
                )
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun scheduleAll(ctx: Context, events: List<Event>, leadDays: Int) {
        cancelAll(ctx, events)
        events.filter { it.notify }.forEach { schedule(ctx, it, leadDays) }
    }

    fun schedule(ctx: Context, event: Event, leadDays: Int) {
        val fmt  = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val date = try { LocalDate.parse(event.date, fmt) } catch (_: Exception) { return }
        val fire = date.minusDays(leadDays.toLong())
        val now  = LocalDate.now()
        if (!fire.isAfter(now.minusDays(1))) return   // already past

        // If the event has a specific time and lead is 0 days, fire at that time;
        // otherwise always fire at 09:00 on the lead day.
        val hour: Int
        val minute: Int
        if (leadDays == 0 && event.time != null) {
            val parts = event.time.split(":")
            hour   = parts.getOrNull(0)?.toIntOrNull() ?: 9
            minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        } else {
            hour   = 9
            minute = 0
        }

        val epochMs = LocalDateTime.of(fire.year, fire.month, fire.dayOfMonth, hour, minute)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(ctx, event)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, pi)
    }

    fun cancel(ctx: Context, event: Event) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(ctx, event))
    }

    fun cancelAll(ctx: Context, events: List<Event>) {
        events.forEach { cancel(ctx, it) }
    }

    private fun pendingIntent(ctx: Context, event: Event): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_EVENT_ID,    event.id)
            putExtra(ReminderReceiver.EXTRA_EVENT_TITLE, event.title)
            putExtra(ReminderReceiver.EXTRA_EVENT_DATE,  event.date)
        }
        // Use a stable int from the event id so each event has its own alarm
        val reqCode = event.id.hashCode()
        return PendingIntent.getBroadcast(
            ctx, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
