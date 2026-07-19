package com.interli.plural.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.core.LocaleHelper
import com.interli.plural.features.mood.MoodActivity
import com.interli.plural.features.todo.TodoActivity
import com.interli.plural.R
import com.interli.plural.TodoList
import java.util.*

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val localizedContext = LocaleHelper.wrapContext(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            rescheduleAlarms(localizedContext)
            return
        }
        val action = intent.action
        val sharedPref = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        if (action == "TODO_REMINDER") {
            val todoEnabled = sharedPref.getBoolean("todo_notif_enabled", true)
            if (!todoEnabled) return
        }
        val channelId = if (action == "TODO_REMINDER") "TODO_CHANNEL" else "MOOD_CHANNEL_V3"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = if (action == "TODO_REMINDER") {
                localizedContext.getString(R.string.todo)
            } else {
                localizedContext.getString(R.string.mood_tracker)
            }
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH 
            val channel = android.app.NotificationChannel(channelId, name, importance).apply {
                description = if (action == "TODO_REMINDER") "To Do reminders" else "Mood Tracker Reminders"
                enableLights(true)
                lightColor = 0xFF800080.toInt()
            }
            val notificationManager = localizedContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (channelId == "MOOD_CHANNEL_V3") {
                notificationManager.deleteNotificationChannel("MOOD_CHANNEL")
                notificationManager.deleteNotificationChannel("MOOD_CHANNEL_V2")
            }
            notificationManager.createNotificationChannel(channel)
        }
        val nextIntent = if (action == "TODO_REMINDER") {
            Intent(localizedContext, TodoActivity::class.java)
        } else {
            Intent(localizedContext, MoodActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            localizedContext, 0, nextIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (action == "TODO_REMINDER") {
            intent.getStringExtra("todo_title") ?: localizedContext.getString(R.string.todo)
        } else {
            localizedContext.getString(R.string.notification_mood_title)
        }
        val message = if (action == "TODO_REMINDER") {
            localizedContext.getString(R.string.todo)
        } else {
            localizedContext.getString(R.string.notification_mood_text)
        }
        val builder = NotificationCompat.Builder(localizedContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        val notificationId = if (action == "TODO_REMINDER") {
            (intent.getStringExtra("todo_id")?.hashCode() ?: 100)
        } else {
            2
        }
        try {
            NotificationManagerCompat.from(localizedContext).notify(notificationId, builder.build())
        } catch (_: SecurityException) {}
        if (action == "MOOD_REMINDER" && intent.hasExtra("mood_alarm_index")) {
            val index = intent.getIntExtra("mood_alarm_index", -1)
            val hour = intent.getIntExtra("mood_alarm_hour", -1)
            val minute = intent.getIntExtra("mood_alarm_minute", -1)
            if (index != -1 && hour != -1 && minute != -1) {
                rescheduleNextMoodAlarm(localizedContext, index, hour, minute)
            }
        }
    }
    private fun scheduleAlarm(alarmManager: android.app.AlarmManager, triggerAtMillis: Long, pendingIntent: android.app.PendingIntent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }
    private fun rescheduleNextMoodAlarm(context: Context, index: Int, hour: Int, minute: Int) {
        val sharedPref = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val enabled = sharedPref.getBoolean("mood_notif_enabled", false)
        if (!enabled) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "MOOD_REMINDER"
            putExtra("mood_alarm_index", index)
            putExtra("mood_alarm_hour", hour)
            putExtra("mood_alarm_minute", minute)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, index, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        scheduleAlarm(alarmManager, calendar.timeInMillis, pendingIntent)
    }
    companion object {
        fun rescheduleAlarms(context: Context) {
            rescheduleMoodAlarms(context)
            rescheduleTodoAlarms(context)
        }
        fun cancelAllMoodAlarms(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            for (i in 0..50) {
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    action = "MOOD_REMINDER"
                }
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context, i, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                alarmManager.cancel(pendingIntent)
            }
        }
        private fun rescheduleMoodAlarms(context: Context) {
            val sharedPref = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            val enabled = sharedPref.getBoolean("mood_notif_enabled", false)
            val timesJson = sharedPref.getString("mood_notif_times", "[]") ?: "[]"
            if (enabled) {
                val times: List<String> = try { 
                    Gson().fromJson(timesJson, object : TypeToken<List<String>>() {}.type) ?: emptyList()
                } catch (_: Exception) { emptyList() }
                times.forEachIndexed { index, timeStr ->
                    val parts = timeStr.split(":")
                    if (parts.size == 2) {
                        val hour = parts[0].toIntOrNull() ?: return@forEachIndexed
                        val minute = parts[1].toIntOrNull() ?: return@forEachIndexed
                        val calendar = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            if (before(Calendar.getInstance())) {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                        val intent = Intent(context, NotificationReceiver::class.java).apply {
                            action = "MOOD_REMINDER"
                            putExtra("mood_alarm_index", index)
                            putExtra("mood_alarm_hour", hour)
                            putExtra("mood_alarm_minute", minute)
                        }
                        val pendingIntent = android.app.PendingIntent.getBroadcast(
                            context, index, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        val receiver = NotificationReceiver()
                        receiver.scheduleAlarm(context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager, calendar.timeInMillis, pendingIntent)
                    }
                }
            }
        }
        private fun rescheduleTodoAlarms(context: Context) {
            val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
            val todoJson = sharedPref.getString("todo_lists", "[]") ?: "[]"
            val type = object : TypeToken<List<TodoList>>() {}.type
            val todoLists: List<TodoList> = try { Gson().fromJson(todoJson, type) } catch (_: Exception) { emptyList() }
            todoLists.forEach { list ->
                if (list.reminderTime != null && list.reminderTime!! > System.currentTimeMillis()) {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                    val intent = Intent(context, NotificationReceiver::class.java).apply {
                        action = "TODO_REMINDER"
                        putExtra("todo_id", list.id)
                        putExtra("todo_title", list.title)
                    }
                    val pendingIntent = android.app.PendingIntent.getBroadcast(
                        context, list.id.hashCode(), intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    val receiver = NotificationReceiver()
                    receiver.scheduleAlarm(alarmManager, list.reminderTime!!, pendingIntent)
                }
            }
        }
    }
}
