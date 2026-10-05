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
import com.interli.plural.R
import com.interli.plural.SysmediaPost
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.member.MemberHelper
import java.text.SimpleDateFormat
import java.util.*

class SysmediaTimelineWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return SysmediaTimelineRemoteViewsFactory(this.applicationContext, intent)
    }
}

class SysmediaTimelineRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )

    data class PostItemInfo(
        val postId: String,
        val senderId: String,
        val authorName: String,
        val authorHandle: String,
        val content: String,
        val formattedTime: String,
        val avatarUri: String?,
        val profileColor: Int
    )

    private val postList = mutableListOf<PostItemInfo>()

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun getCount(): Int = postList.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun onDataSetChanged() {
        val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val postsJson = sharedPref.getString("sysmedia_posts", "[]") ?: "[]"
        val typePosts = object : TypeToken<List<SysmediaPost>>() {}.type
        val allPosts: List<SysmediaPost> = try {
            Gson().fromJson(postsJson, typePosts) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val people = MemberHelper.loadAllPeople(context)
        val peopleMap = people.associateBy { it.id }

        val widgetPrefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val config = widgetPrefs.getString("sysmedia_timeline_widget_$appWidgetId", "") ?: ""
        val selectedAccountIds = config.split(",").filter { it.isNotEmpty() }

        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

        postList.clear()

        val filtered = allPosts.filter { post ->
            val isScheduledFuture = post.scheduledTime != null && post.scheduledTime!! > now
            val matchesAccount = selectedAccountIds.isEmpty() ||
                    selectedAccountIds.contains("ALL") ||
                    selectedAccountIds.contains(post.senderId)
            !isScheduledFuture && matchesAccount
        }.sortedByDescending { it.timestamp }

        filtered.forEach { post ->
            val person = peopleMap[post.senderId]
            val name = person?.sysmediaProfile?.displayName ?: person?.name ?: "Onbekend"
            val handle = person?.sysmediaProfile?.handle?.let { "@$it" } ?: ""
            val avatar = person?.sysmediaProfile?.profilePictureUri ?: person?.profilePictureUri
            val color = person?.profileColor ?: -6934396

            postList.add(
                PostItemInfo(
                    postId = post.id,
                    senderId = post.senderId,
                    authorName = name,
                    authorHandle = handle,
                    content = post.content,
                    formattedTime = sdf.format(Date(post.timestamp)),
                    avatarUri = avatar,
                    profileColor = color
                )
            )
        }
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= postList.size) return RemoteViews(context.packageName, R.layout.widget_sysmedia_post_item)

        val views = RemoteViews(context.packageName, R.layout.widget_sysmedia_post_item)
        val item = postList[position]

        val authorDisplay = if (item.authorHandle.isNotEmpty()) {
            "${item.authorName} ${item.authorHandle}"
        } else {
            item.authorName
        }

        views.setTextViewText(R.id.tvWidgetPostAuthor, authorDisplay)
        views.setTextViewText(R.id.tvWidgetPostTime, item.formattedTime)
        views.setTextViewText(R.id.tvWidgetPostContent, item.content)

        val textColor = ColorHelper.getTextColor(context)
        val bgColor = ColorHelper.getBgColor(context)

        views.setInt(R.id.widget_post_item_root, "setBackgroundColor", bgColor)
        views.setTextColor(R.id.tvWidgetPostAuthor, textColor)
        views.setTextColor(R.id.tvWidgetPostContent, textColor)
        views.setTextColor(R.id.tvWidgetPostTime, textColor)

        val avatarBitmap = getAvatarBitmap(context, item.avatarUri, item.profileColor)
        views.setImageViewBitmap(R.id.ivWidgetPostAvatar, avatarBitmap)

        val fillInIntent = Intent().apply {
            putExtra("post_id", item.postId)
            putExtra("active_member_id", item.senderId)
        }
        views.setOnClickFillInIntent(R.id.widget_post_item_root, fillInIntent)

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
            } catch (e: Exception) {
                e.printStackTrace()
            }
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