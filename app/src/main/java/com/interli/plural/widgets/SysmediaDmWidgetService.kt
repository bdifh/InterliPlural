package com.interli.plural.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.ChatGroup
import com.interli.plural.DirectMessage
import com.interli.plural.R
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.member.MemberHelper

class SysmediaDmWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return SysmediaDmRemoteViewsFactory(this.applicationContext, intent)
    }
}

class SysmediaDmRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )

    data class UserUnreadDmInfo(
        val personId: String,
        val displayName: String,
        val handle: String,
        val avatarUri: String?,
        val profileColor: Int,
        val unreadCount: Int
    )

    private val userUnreadList = mutableListOf<UserUnreadDmInfo>()

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun getCount(): Int = userUnreadList.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun onDataSetChanged() {
        val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)

        val msgJson = sharedPref.getString("sysmedia_dms", "[]") ?: "[]"
        val allMessages: List<DirectMessage> = try {
            Gson().fromJson(msgJson, object : TypeToken<List<DirectMessage>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }

        val groupJson = sharedPref.getString("sysmedia_chat_groups", "[]") ?: "[]"
        val chatGroups: List<ChatGroup> = try {
            Gson().fromJson(groupJson, object : TypeToken<List<ChatGroup>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }

        val people = MemberHelper.loadAllPeople(context).filter { !it.isArchived }

        val widgetPrefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val config = widgetPrefs.getString("sysmedia_dm_widget_$appWidgetId", "") ?: ""
        val selectedAccountIds = config.split(",").filter { it.isNotEmpty() }

        userUnreadList.clear()

        people.forEach { person ->
            val matchesAccount = selectedAccountIds.isEmpty() ||
                    selectedAccountIds.contains("ALL") ||
                    selectedAccountIds.contains(person.id)
            if (!matchesAccount) return@forEach

            val myGroupIds = chatGroups.filter { it.participantIds.contains(person.id) }.map { it.id }

            val unreadCount = allMessages.count { msg ->
                !msg.isRead &&
                        msg.senderId != person.id &&
                        (msg.chatId.contains(person.id) || myGroupIds.contains(msg.chatId))
            }

            if (unreadCount > 0) {
                val name = person.sysmediaProfile?.displayName ?: person.name
                val handle = person.sysmediaProfile?.handle?.let { "@$it" } ?: ""
                val avatar = person.sysmediaProfile?.profilePictureUri ?: person.profilePictureUri
                val color = person.profileColor

                userUnreadList.add(
                    UserUnreadDmInfo(
                        personId = person.id,
                        displayName = name,
                        handle = handle,
                        avatarUri = avatar,
                        profileColor = color,
                        unreadCount = unreadCount
                    )
                )
            }
        }

        userUnreadList.sortByDescending { it.unreadCount }
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= userUnreadList.size) {
            return RemoteViews(context.packageName, R.layout.widget_sysmedia_dm_item)
        }

        val views = RemoteViews(context.packageName, R.layout.widget_sysmedia_dm_item)
        val item = userUnreadList[position]

        views.setTextViewText(R.id.tvWidgetDmName, item.displayName)
        if (item.handle.isNotEmpty()) {
            views.setTextViewText(R.id.tvWidgetDmHandle, item.handle)
            views.setViewVisibility(R.id.tvWidgetDmHandle, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.tvWidgetDmHandle, android.view.View.GONE)
        }

        val unreadText = context.getString(R.string.unread_dms_count, item.unreadCount)
        views.setTextViewText(R.id.tvWidgetDmUnreadCount, unreadText)

        val textColor = ColorHelper.getTextColor(context)
        val bgColor = ColorHelper.getBgColor(context)

        views.setInt(R.id.widget_dm_item_root, "setBackgroundColor", bgColor)
        views.setTextColor(R.id.tvWidgetDmName, textColor)
        views.setTextColor(R.id.tvWidgetDmHandle, textColor)

        val avatarBitmap = getAvatarBitmap(context, item.avatarUri, item.profileColor)
        views.setImageViewBitmap(R.id.ivWidgetDmAvatar, avatarBitmap)

        val fillInIntent = Intent().apply {
            putExtra("active_member_id", item.personId)
            putExtra("SELECT_TAB", 4)
        }
        views.setOnClickFillInIntent(R.id.widget_dm_item_root, fillInIntent)

        return views
    }

    private fun getAvatarBitmap(context: Context, avatarUriStr: String?, profileColor: Int): Bitmap {
        if (!avatarUriStr.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(avatarUriStr)
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val original = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    if (original != null) {
                        val size = 96
                        val scaled = Bitmap.createScaledBitmap(original, size, size, true)
                        return createCircularBitmap(scaled)
                    }
                }
            } catch (_: Exception) {}
        }
        return createColoredCircleBitmap(profileColor)
    }

    private fun createCircularBitmap(src: Bitmap): Bitmap {
        val size = src.width.coerceAtMost(src.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        val rectF = RectF(rect)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawOval(rectF, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, rect, rect, paint)
        return output
    }

    private fun createColoredCircleBitmap(color: Int): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (color == -6934396) 0xFF3F51B5.toInt() else color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return bitmap
    }
}