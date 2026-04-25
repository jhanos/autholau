package com.autholau.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autholau.R
import com.autholau.storage.Prefs
import com.autholau.ui.MainActivity

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_EVENT_ID    = "event_id"
        const val EXTRA_EVENT_TITLE = "event_title"
        const val EXTRA_EVENT_DATE  = "event_date"
        const val ACTION_BOOT       = "android.intent.action.BOOT_COMPLETED"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_BOOT -> rescheduleAll(ctx)
            else        -> fireNotification(ctx, intent)
        }
    }

    private fun fireNotification(ctx: Context, intent: Intent) {
        val id    = intent.getStringExtra(EXTRA_EVENT_ID)    ?: return
        val title = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: return
        val date  = intent.getStringExtra(EXTRA_EVENT_DATE)  ?: ""

        NotificationScheduler.ensureChannel(ctx)

        val tapIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPi = PendingIntent.getActivity(
            ctx, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = android.app.Notification.Builder(ctx, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Coming up: $date")
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    ctx, 0, tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id.hashCode(), notif)
    }

    private fun rescheduleAll(ctx: Context) {
        val events   = Prefs.loadEvents(ctx)
        val leadDays = Prefs.notifLeadDays(ctx)
        NotificationScheduler.scheduleAll(ctx, events, leadDays)
    }
}
