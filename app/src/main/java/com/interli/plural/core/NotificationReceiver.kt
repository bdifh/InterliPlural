package com.interli.plural.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.CalendarEvent
import com.interli.plural.features.mood.MoodActivity
import com.interli.plural.features.todo.TodoActivity
import com.interli.plural.features.calendar.CalendarActivity
import com.interli.plural.R
import com.interli.plural.TodoList
import java.util.*

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val localizedContext = LocaleHelper.wrapContext(context)
        val action = intent.action

        if (action == Intent.ACTION_BOOT_COMPLETED) {
            rescheduleAlarms(localizedContext)
            return
        }

        val sharedPref = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

        val channelId = when (action) {
            "TODO_REMINDER" -> "TODO_CHANNEL"
            "CALENDAR_REMINDER" -> "CALENDAR_CHANNEL"
            else -> "MOOD_CHANNEL_V3"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = when (action) {
                "TODO_REMINDER" -> localizedContext.getString(R.string.todo)
                "CALENDAR_REMINDER" -> localizedContext.getString(R.string.calendar)
                else -> localizedContext.getString(R.string.mood_tracker)
            }
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                enableLights(true)
                lightColor = 0xFF800080.toInt()
            }
            val nm = localizedContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val nextIntent = when (action) {
            "TODO_REMINDER" -> Intent(localizedContext, TodoActivity::class.java)
            "CALENDAR_REMINDER" -> Intent(localizedContext, CalendarActivity::class.java)
            else -> Intent(localizedContext, MoodActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            localizedContext,
            (intent.getStringExtra("todo_id") ?: intent.getStringExtra("event_id") ?: "0").hashCode(),
            nextIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = when (action) {
            "TODO_REMINDER" -> intent.getStringExtra("todo_title") ?: localizedContext.getString(R.string.todo)
            "CALENDAR_REMINDER" -> intent.getStringExtra("event_title") ?: localizedContext.getString(R.string.calendar)
            else -> localizedContext.getString(R.string.notification_mood_title)
        }

        val message = when (action) {
            "TODO_REMINDER" -> localizedContext.getString(R.string.todo)
            "CALENDAR_REMINDER" -> localizedContext.getString(R.string.calendar)
            else -> localizedContext.getString(R.string.notification_mood_text)
        }

        val builder = NotificationCompat.Builder(localizedContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val notificationId = when (action) {
            "TODO_REMINDER" -> (intent.getStringExtra("todo_id")?.hashCode() ?: 100)
            "CALENDAR_REMINDER" -> (intent.getStringExtra("event_id")?.hashCode() ?: 200)
            else -> 2
        }

        try {
            NotificationManagerCompat.from(localizedContext).notify(notificationId, builder.build())
        } catch (_: SecurityException) {}

        if (action == "MOOD_REMINDER") {
            val index = intent.getIntExtra("mood_alarm_index", -1)
            val hour = intent.getIntExtra("mood_alarm_hour", -1)
            val minute = intent.getIntExtra("mood_alarm_minute", -1)
            if (index != -1) rescheduleNextMoodAlarm(localizedContext, index, hour, minute)
        }
    }

    fun scheduleAlarm(alarmManager: android.app.AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        if (!sharedPref.getBoolean("mood_notif_enabled", false)) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "MOOD_REMINDER"
            putExtra("mood_alarm_index", index); putExtra("mood_alarm_hour", hour); putExtra("mood_alarm_minute", minute)
        }
        val pendingIntent = PendingIntent.getBroadcast(context, index, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        scheduleAlarm(alarmManager, calendar.timeInMillis, pendingIntent)
    }

    companion object {
        fun rescheduleAlarms(context: Context) {
            rescheduleMoodAlarms(context)
            rescheduleTodoAlarms(context)
            rescheduleCalendarAlarms(context)
        }

        private fun rescheduleCalendarAlarms(context: Context) {
            val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
            val json = sharedPref.getString("calendar_events", "[]") ?: "[]"
            val events: List<CalendarEvent> = Gson().fromJson(json, object : TypeToken<List<CalendarEvent>>() {}.type) ?: emptyList()
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val receiver = NotificationReceiver()

            events.forEach { event ->
                if (event.reminderTime != null && event.reminderTime!! > System.currentTimeMillis()) {
                    val intent = Intent(context, NotificationReceiver::class.java).apply {
                        action = "CALENDAR_REMINDER"
                        putExtra("event_id", event.id)
                        putExtra("event_title", event.title)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(context, event.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    receiver.scheduleAlarm(alarmManager, event.reminderTime!!, pendingIntent)
                }
            }
        }

        fun cancelAllMoodAlarms(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            for (i in 0..50) {
                val intent = Intent(context, NotificationReceiver::class.java).apply { action = "MOOD_REMINDER" }
                val pendingIntent = PendingIntent.getBroadcast(context, i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                alarmManager.cancel(pendingIntent)
            }
        }

        private fun rescheduleMoodAlarms(context: Context) {
            val sharedPref = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            val enabled = sharedPref.getBoolean("mood_notif_enabled", false)
            val timesJson = sharedPref.getString("mood_notif_times", "[]") ?: "[]"
            if (enabled) {
                val times: List<String> = try { Gson().fromJson(timesJson, object : TypeToken<List<String>>() {}.type) } catch (_: Exception) { emptyList() }
                times.forEachIndexed { index, timeStr ->
                    val parts = timeStr.split(":")
                    if (parts.size == 2) {
                        val hour = parts[0].toIntOrNull() ?: return@forEachIndexed
                        val minute = parts[1].toIntOrNull() ?: return@forEachIndexed
                        val calendar = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
                            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
                        }
                        val intent = Intent(context, NotificationReceiver::class.java).apply {
                            action = "MOOD_REMINDER"
                            putExtra("mood_alarm_index", index); putExtra("mood_alarm_hour", hour); putExtra("mood_alarm_minute", minute)
                        }
                        val pendingIntent = PendingIntent.getBroadcast(context, index, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                        NotificationReceiver().scheduleAlarm(context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager, calendar.timeInMillis, pendingIntent)
                    }
                }
            }
        }

        private fun rescheduleTodoAlarms(context: Context) {
            val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
            val todoJson = sharedPref.getString("todo_lists", "[]") ?: "[]"
            val todoLists: List<TodoList> = try { Gson().fromJson(todoJson, object : TypeToken<List<TodoList>>() {}.type) } catch (_: Exception) { emptyList() }
            todoLists.forEach { list ->
                if (list.reminderTime != null && list.reminderTime!! > System.currentTimeMillis()) {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                    val intent = Intent(context, NotificationReceiver::class.java).apply {
                        action = "TODO_REMINDER"; putExtra("todo_id", list.id); putExtra("todo_title", list.title)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(context, list.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    NotificationReceiver().scheduleAlarm(alarmManager, list.reminderTime!!, pendingIntent)
                }
            }
        }
    }
}