package com.interli.plural

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SysmediaNotificationHelper {
    private const val SYSMEDIA_NOTIF_ID = 1001
    private const val CHANNEL_ID = "SYSMEDIA_CHANNEL"

    fun checkAndNotify(context: Context, personId: String?) {
        if (personId == null) return

        val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val settingsPref = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

        val sysmediaNotisEnabled = settingsPref.getBoolean("sysmedia_notif_enabled", true)
        val sysmediaDmsEnabled = settingsPref.getBoolean("sysmedia_dm_notif_enabled", true)
        
        if (!sysmediaNotisEnabled && !sysmediaDmsEnabled) {
            NotificationManagerCompat.from(context).cancel(SYSMEDIA_NOTIF_ID)
            return
        }

        val peopleJson = sharedPref.getString("people_list", "[]")
        val normalPeople: List<Person> = try {
            Gson().fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        
        val sysmediaJson = sharedPref.getString("sysmedia_people_list", "[]")
        val sysmediaPeople: List<Person> = try {
            Gson().fromJson(sysmediaJson, object : TypeToken<List<Person>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        
        val allPeople = normalPeople + sysmediaPeople
        val person = allPeople.find { it.id == personId }
        if (person == null || !person.isFront || person.isArchived) {
            return
        }

        var unreadCount = 0

        if (sysmediaNotisEnabled) {
            val notifJson = sharedPref.getString("sysmedia_notifications", "[]")
            val notifications: List<SysmediaNotification> = try {
                Gson().fromJson(notifJson, object : TypeToken<List<SysmediaNotification>>() {}.type) ?: emptyList()
            } catch (_: Exception) { emptyList() }
            
            unreadCount += notifications.count { 
                it.receiverId == personId && !it.isRead
            }
        }

        if (sysmediaDmsEnabled) {
            val msgJson = sharedPref.getString("sysmedia_dms", "[]")
            val allMessages: List<DirectMessage> = try {
                Gson().fromJson(msgJson, object : TypeToken<List<DirectMessage>>() {}.type) ?: emptyList()
            } catch (_: Exception) { emptyList() }
            
            val groupJson = sharedPref.getString("sysmedia_chat_groups", "[]")
            val chatGroups: List<ChatGroup> = try {
                Gson().fromJson(groupJson, object : TypeToken<List<ChatGroup>>() {}.type) ?: emptyList()
            } catch (_: Exception) { emptyList() }
            
            val myGroupIds = chatGroups.filter { it.participantIds.contains(personId) }.map { it.id }

            unreadCount += allMessages.count { msg ->
                !msg.isRead && msg.senderId != personId && 
                (msg.chatId.contains(personId) || myGroupIds.contains(msg.chatId))
            }
        }

        if (unreadCount > 0) {
            sendNotification(context, unreadCount)
        } else {
            NotificationManagerCompat.from(context).cancel(SYSMEDIA_NOTIF_ID)
        }
    }

    private fun sendNotification(context: Context, count: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SYSMEDIA", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, SYSMEDIA_NOTIF_ID, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(context.getString(R.string.notification_channel_sysmedia_name))
            .setContentText(context.getString(R.string.notification_sysmedia_summary, count))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(SYSMEDIA_NOTIF_ID, builder.build())
        } catch (_: SecurityException) { }
    }
}
