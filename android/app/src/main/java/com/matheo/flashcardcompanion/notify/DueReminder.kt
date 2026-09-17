package com.matheo.flashcardcompanion.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.matheo.flashcardcompanion.MainActivity
import com.matheo.flashcardcompanion.R
import com.matheo.flashcardcompanion.data.Repository
import com.matheo.flashcardcompanion.ui.Translator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The daily "you still have cards due" reminder.
 *
 * This replaces the Termux cron job that used to poll the local server: the
 * count is computed in-process, so the reminder works with no server and no
 * Google services.
 */
object DueReminder {

    private const val CHANNEL_ID = "due_cards"
    private const val NOTIFICATION_ID = 4820
    private const val REQUEST_CODE = 4821

    fun schedule(context: Context, hour: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DueReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

        // Inexact on purpose: a study reminder does not justify an exact-alarm
        // permission prompt, and the system batching it by a few minutes is fine.
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pending,
        )
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, DueReminderReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        )
    }

    fun notifyDue(context: Context, count: Int) {
        if (count <= 0) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Cartes dues", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val t = Translator(Repository(context).prefs.lang)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text =
            if (t.lang == "en") "$count card${if (count > 1) "s" else ""} to review"
            else "$count carte${if (count > 1) "s" else ""} à réviser"

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cards)
            .setContentTitle("Flashcard Companion")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

class DueReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = Repository(context.applicationContext)
                DueReminder.notifyDue(context.applicationContext, repo.dueCount())
            } catch (_: Exception) {
                // A reminder is best-effort; never crash the receiver.
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms do not survive a reboot, so re-arm from the stored hour. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val hour = runCatching {
            Repository(context.applicationContext).store.getSettingsMap()["notify_hour"]?.toInt()
        }.getOrNull() ?: 9
        DueReminder.schedule(context.applicationContext, hour)
    }
}
