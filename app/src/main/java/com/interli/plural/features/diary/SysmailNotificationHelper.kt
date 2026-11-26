package com.interli.plural.features.diary

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.interli.plural.R

object SysmailNotificationHelper {
    private const val CHANNEL_ID = "SYSMAIL_CHANNEL"

    fun notifyRecipients(
        context: Context,
        messageId: String,
        recipientNames: List<String>
    ) {
        val settings = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        if (!settings.getBoolean("sysmail_notif_enabled", true) || recipientNames.isEmpty()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_sysmail_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_sysmail_description)
            }

            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val diaryIntent = Intent(context, DiaryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_SYSMAIL", true)
            putExtra("SELECT_TAB", 1)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            messageId.hashCode(),
            diaryIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val names = recipientNames.distinct().joinToString(", ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(context.getString(R.string.notification_sysmail_title))
            .setContentText(context.getString(R.string.notification_sysmail_text, names))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_sysmail_text, names))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(messageId.hashCode(), notification)
        } catch (_: SecurityException) {
            // must keep empty or code breaks.
        }
    }
}