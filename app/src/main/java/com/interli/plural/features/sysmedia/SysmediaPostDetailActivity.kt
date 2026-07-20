package com.interli.plural.features.sysmedia

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.diary.CreatePostActivity
import com.interli.plural.core.MediaEmbedHelper
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.features.sysmedia.SysmediaNotificationHelper
import com.interli.plural.features.sysmedia.SysmediaProfileActivity
import com.interli.plural.Person
import com.interli.plural.R
import com.interli.plural.SysmediaNotification
import com.interli.plural.SysmediaPost
import com.interli.plural.SysmediaProfile
import java.text.SimpleDateFormat
import java.util.*

class SysmediaPostDetailActivity : BaseActivity() {
    private lateinit var posts: MutableList<SysmediaPost>
    private lateinit var people: List<Person>
    private lateinit var activeMemberId: String
    private lateinit var postId: String
    private lateinit var currentPost: SysmediaPost
    private lateinit var markwon: io.noties.markwon.Markwon
    private val gson = Gson()
    private val sdf = SimpleDateFormat("HH:mm · dd MMM yy", Locale.getDefault())
    private var selectedImageUri: android.net.Uri? = null
    private val pickImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val file = java.io.File(filesDir, "sysmedia_reply_${System.currentTimeMillis()}.jpg")
                val outputStream = java.io.FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                selectedImageUri = android.net.Uri.fromFile(file)
                findViewById<View>(R.id.layoutReplyImagePreview).visibility = View.VISIBLE
                findViewById<ImageView>(R.id.ivReplyPreview).load(selectedImageUri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sysmedia_post_detail)
        markwon = io.noties.markwon.Markwon.builder(this)
            .usePlugin(io.noties.markwon.linkify.LinkifyPlugin.create())
            .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
            .usePlugin(io.noties.markwon.image.coil.CoilImagesPlugin.create(this))
            .build()
        postId = intent.getStringExtra("post_id") ?: ""
        activeMemberId = intent.getStringExtra("active_member_id") ?: ""
        loadData()
        val foundPost = posts.find { it.id == postId }
        if (foundPost == null) {
            finish()
            return
        }
        currentPost = foundPost
        setupToolbar()
        renderParentPost()
        renderMainPost()
        setupInteractions()
        setupReplyBox()
        val bgColor = ColorHelper.getBgColor(this)
        val textColor = ColorHelper.getTextColor(this)
        findViewById<View>(R.id.main).setBackgroundColor(bgColor)
        findViewById<View>(R.id.layoutReply).setBackgroundColor(bgColor)
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar.setBackgroundColor(bgColor)
        toolbar.setNavigationIconTint(textColor)
        toolbar.setTitleTextColor(textColor)
        ColorHelper.applySettings(this)
    }
    override fun onResume() {
        super.onResume()
        loadData()
        posts.find { it.id == postId }?.let {
            currentPost = it
            renderMainPost()
            setupInteractions()
        } ?: finish()
    }
    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val postsJson = sharedPref.getString("sysmedia_posts", "[]")
        posts = gson.fromJson(postsJson, object : TypeToken<MutableList<SysmediaPost>>() {}.type) ?: mutableListOf()
        posts.forEach {
            @Suppress("SENSELESS_COMPARISON")
            if (it.likedByMemberIds == null) it.likedByMemberIds = mutableMapOf()
        }
        people = MemberHelper.loadAllPeople(this)
    }
    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.menu_sysmedia_post_detail)
        val isOwnPost = currentPost.senderId == activeMemberId
        toolbar.menu.findItem(R.id.action_edit).isVisible = isOwnPost
        toolbar.menu.findItem(R.id.action_delete).isVisible = isOwnPost
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> {
                    val intent = android.content.Intent(this, CreatePostActivity::class.java)
                    intent.putExtra("current_user_id", activeMemberId)
                    intent.putExtra("edit_post_id", currentPost.id)
                    startActivity(intent)
                    true
                }
                R.id.action_delete -> {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(getString(R.string.delete))
                        .setMessage("Are you sure you want to delete this post?")
                        .setPositiveButton(getString(R.string.delete)) { _, _ -> deletePost() }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                    true
                }
                else -> false
            }
        }
    }
    private fun deletePost() {
        if (currentPost.replyToId != null) {
            posts.find { it.id == currentPost.replyToId }?.let { original ->
                if (original.replies > 0) original.replies--
            }
        }
        if (currentPost.reblogOfId != null) {
            posts.find { it.id == currentPost.reblogOfId }?.let { original ->
                if (original.retweets > 0) original.retweets--
            }
        }
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val notifJson = sharedPref.getString("sysmedia_notifications", "[]")
        val typeNotifs = object : TypeToken<MutableList<SysmediaNotification>>() {}.type
        val notifications: MutableList<SysmediaNotification> = gson.fromJson(notifJson, typeNotifs) ?: mutableListOf()
        notifications.removeAll { it.postId == currentPost.id }
        sharedPref.edit { putString("sysmedia_notifications", gson.toJson(notifications)) }
        posts.remove(currentPost)
        saveData()
        finish()
    }
    private fun renderParentPost() {
        val container = findViewById<LinearLayout>(R.id.layoutParentsContainer)
        container.removeAllViews()
        val parents = mutableListOf<SysmediaPost>()
        var currentId = currentPost.replyToId
        while (currentId != null) {
            val parent = posts.find { it.id == currentId }
            if (parent != null) {
                parents.add(0, parent)
                currentId = parent.replyToId
            } else {
                currentId = null
            }
        }
        if (parents.isEmpty()) return
        val textColor = ColorHelper.getTextColor(this)
        val inflater = LayoutInflater.from(this)
        parents.forEach { parentPost ->
            val parentView = inflater.inflate(R.layout.item_sysmedia_post, container, false)
            val sender = people.find { it.id == parentPost.senderId }
            bindPostViewData(parentView, parentPost, sender, textColor)
            parentView.setOnClickListener {
                val intent = android.content.Intent(this, SysmediaPostDetailActivity::class.java)
                intent.putExtra("post_id", parentPost.id)
                intent.putExtra("active_member_id", activeMemberId)
                startActivity(intent)
            }
            container.addView(parentView)
            val connector = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(2.dpToPx(), 20.dpToPx()).apply {
                    marginStart = 35.dpToPx()
                }
                setBackgroundColor(0xFFDDDDDD.toInt())
            }
            container.addView(connector)
        }
    }
    private fun bindPostViewData(layout: View, post: SysmediaPost, sender: Person?, textColor: Int) {
        val ivAvatar = layout.findViewById<ImageView>(R.id.ivAvatar)
        val tvName = layout.findViewById<TextView>(R.id.tvName)
        val tvHandle = layout.findViewById<TextView>(R.id.tvHandle)
        val tvContent = layout.findViewById<TextView>(R.id.tvContent)
        val tvTime = layout.findViewById<TextView>(R.id.tvTimeBottom)
        val profile = sender?.sysmediaProfile
        tvName.text = profile?.displayName ?: sender?.name ?: "Unknown"
        tvName.setTextColor(textColor)
        val handle = profile?.handle ?: sender?.name?.replace(" ", "_")?.lowercase()?.replace(Regex("[^a-z0-9_]"), "") ?: sender?.manualId ?: "unknown"
        tvHandle.text = "@$handle"
        tvHandle.setTextColor(textColor and 0x88FFFFFF.toInt())
        markwon.setMarkdown(tvContent, post.content)
        tvContent.setTextColor(textColor)
        tvTime.text = sdf.format(Date(post.timestamp))
        tvTime.setTextColor(textColor and 0x88FFFFFF.toInt())
        val avatarUri = profile?.profilePictureUri ?: sender?.profilePictureUri
        if (avatarUri != null) ivAvatar.load(avatarUri)
        else ivAvatar.setImageDrawable(android.graphics.drawable.ColorDrawable(sender?.profileColor ?: ColorHelper.getBtnColor(this)))
        ivAvatar.setOnClickListener { sender?.id?.let { openProfile(it) } }
        tvName.setOnClickListener { sender?.id?.let { openProfile(it) } }
        val layoutPoll = layout.findViewById<LinearLayout>(R.id.layoutPoll)
        renderPoll(layoutPoll, post)
        val ivImage = layout.findViewById<ImageView>(R.id.ivPostImagePreview)
        if (post.imageUri != null) { ivImage.visibility = View.VISIBLE; ivImage.load(post.imageUri) }
        else ivImage.visibility = View.GONE
        layout.findViewById<TextView>(R.id.tvLikes).text = post.likes.toString()
        layout.findViewById<TextView>(R.id.tvRetweets).text = post.retweets.toString()
        layout.findViewById<TextView>(R.id.tvReplies).text = post.replies.toString()
        val activeLikes = post.likedByMemberIds[activeMemberId] ?: 0
        val likeIconColor = if (activeLikes > 0) ColorHelper.getBtnColor(this) else (textColor and 0x00FFFFFF) or 0x88000000.toInt()
        layout.findViewById<TextView>(R.id.tvLikes).setTextColor(likeIconColor)
        layout.findViewById<android.widget.ImageView>(R.id.ivLikeIcon)?.setColorFilter(likeIconColor)
        layout.findViewById<View>(R.id.btnLike).setOnClickListener {
            val memberId = activeMemberId
            val currentLikes = post.likedByMemberIds[memberId] ?: 0
            if (currentLikes < 3) {
                post.likedByMemberIds[memberId] = currentLikes + 1
                post.likes++
                if (currentLikes == 0 && post.senderId != memberId) {
                    val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                    val notifJson = sharedPref.getString("sysmedia_notifications", "[]")
                    val notifications: MutableList<SysmediaNotification> = gson.fromJson(notifJson, object : TypeToken<MutableList<SysmediaNotification>>() {}.type) ?: mutableListOf()
                    notifications.add(0, SysmediaNotification(receiverId = post.senderId, senderId = memberId, type = "LIKE", postId = post.id))
                    sharedPref.edit().putString("sysmedia_notifications", Gson().toJson(notifications)).apply()
                    SysmediaNotificationHelper.checkAndNotify(this@SysmediaPostDetailActivity, post.senderId)
                }
            } else {
                post.likes -= 3
                post.likedByMemberIds[memberId] = 0
            }
            saveData()
            bindPostViewData(layout, post, sender, textColor)
        }
        layout.findViewById<View>(R.id.btnReblog).setOnClickListener {
            val intent = android.content.Intent(this, CreatePostActivity::class.java)
            intent.putExtra("current_user_id", activeMemberId)
            intent.putExtra("reblog_of_id", post.id)
            startActivity(intent)
        }
        layout.findViewById<View>(R.id.btnShare)?.setOnClickListener {
            downloadPostAsImage(layout, post)
        }
        val mediaContainer = layout.findViewById<LinearLayout>(R.id.mediaEmbedContainer)
        MediaEmbedHelper.addEmbedsToContainer(mediaContainer, post.content)
        layout.findViewById<MaterialCardView>(R.id.cardOriginalPost)?.apply {
            setCardBackgroundColor(ColorHelper.getBgColor(this@SysmediaPostDetailActivity))
        }
    }
    private fun renderMainPost() {
        val sender = people.find { it.id == currentPost.senderId }
        val layout = findViewById<View>(R.id.mainPostLayout)
        val textColor = ColorHelper.getTextColor(this)
        val cardOriginal = layout.findViewById<View>(R.id.cardOriginalPost)
        if (currentPost.reblogOfId != null) {
            cardOriginal.visibility = View.VISIBLE
            val originalPost = posts.find { it.id == currentPost.reblogOfId }
            if (originalPost != null) {
                val originalSender = people.find { it.id == originalPost.senderId }
                val origProfile = originalSender?.sysmediaProfile
                layout.findViewById<TextView>(R.id.tvOriginalName).text = origProfile?.displayName ?: originalSender?.name ?: "Unknown"
                val origHandle = origProfile?.handle ?: originalSender?.name?.replace(" ", "_")?.lowercase() ?: originalSender?.manualId ?: "unknown"
                layout.findViewById<TextView>(R.id.tvOriginalHandle).text = "@$origHandle"
                markwon.setMarkdown(layout.findViewById(R.id.tvOriginalContent), originalPost.content)
                val origAvatar = origProfile?.profilePictureUri ?: originalSender?.profilePictureUri
                if (origAvatar != null) layout.findViewById<ImageView>(R.id.ivOriginalAvatar).load(origAvatar)
                else layout.findViewById<ImageView>(R.id.ivOriginalAvatar).setImageDrawable(android.graphics.drawable.ColorDrawable(originalSender?.profileColor ?: ColorHelper.getBtnColor(this)))
                val ivOriginalImage = layout.findViewById<ImageView>(R.id.ivOriginalPostImage)
                if (originalPost.imageUri != null) {
                    ivOriginalImage.visibility = View.VISIBLE
                    ivOriginalImage.load(originalPost.imageUri)
                } else {
                    ivOriginalImage.visibility = View.GONE
                }
            }
        } else {
            cardOriginal?.visibility = View.GONE
        }
        bindPostViewData(layout, currentPost, sender, textColor)
    }
    private fun setupInteractions() {
        val rvInteractions = findViewById<RecyclerView>(R.id.rvInteractions)
        rvInteractions.layoutManager = LinearLayoutManager(this)
        val interactions = mutableListOf<Any>()
        interactions.addAll(posts.filter { it.replyToId == currentPost.id }.sortedBy { it.timestamp })
        val notifJson = getSharedPreferences("my_app", MODE_PRIVATE).getString("sysmedia_notifications", "[]")
        val notifications: List<SysmediaNotification> = gson.fromJson(notifJson, object : TypeToken<List<SysmediaNotification>>() {}.type) ?: emptyList()
        interactions.addAll(notifications.filter { it.postId == currentPost.id && (it.type == "LIKE" || it.type == "REBLOG") })
        rvInteractions.adapter = InteractionAdapter(interactions)
    }
    private fun setupReplyBox() {
        val etReply = findViewById<EditText>(R.id.etReply)
        val btnSend = findViewById<Button>(R.id.btnSendReply)
        val btnAddMedia = findViewById<android.widget.ImageButton>(R.id.btnReplyAddMedia)
        val btnRemoveImage = findViewById<android.widget.ImageButton>(R.id.btnRemoveReplyImage)
        val layoutPreview = findViewById<View>(R.id.layoutReplyImagePreview)
        val textColor = ColorHelper.getTextColor(this)
        etReply.setTextColor(textColor)
        etReply.setHintTextColor(textColor and 0x88FFFFFF.toInt())
        btnAddMedia.setOnClickListener { pickImage.launch("image/*") }
        btnAddMedia.setColorFilter(ColorHelper.getBtnColor(this))
        btnRemoveImage.setOnClickListener {
            selectedImageUri = null
            layoutPreview.visibility = View.GONE
        }
        btnSend.setOnClickListener {
            val content = etReply.text.toString().trim()
            if (content.isNotEmpty() || selectedImageUri != null) {
                sendReply(content)
                etReply.setText("")
                selectedImageUri = null
                layoutPreview.visibility = View.GONE
                setupInteractions()
                renderMainPost()
            }
        }
    }
    private fun sendReply(content: String) {
        val newPost = SysmediaPost(
            senderId = activeMemberId,
            content = content,
            replyToId = currentPost.id,
            imageUri = selectedImageUri?.toString()
        )
        posts.add(0, newPost)
        posts.find { it.id == currentPost.id }?.let { it.replies++ }
        if (currentPost.senderId != activeMemberId) {
            val notifJson = getSharedPreferences("my_app", MODE_PRIVATE).getString("sysmedia_notifications", "[]")
            val notifications: MutableList<SysmediaNotification> = gson.fromJson(notifJson, object : TypeToken<MutableList<SysmediaNotification>>() {}.type) ?: mutableListOf()
            notifications.add(0, SysmediaNotification(receiverId = currentPost.senderId, senderId = activeMemberId, type = "REPLY", postId = currentPost.id))
            getSharedPreferences("my_app", MODE_PRIVATE).edit().putString("sysmedia_notifications", gson.toJson(notifications)).apply()
            SysmediaNotificationHelper.checkAndNotify(this, currentPost.senderId)
        }
        saveData()
    }
    private fun saveData() {
        getSharedPreferences("my_app", MODE_PRIVATE).edit(commit = true) { putString("sysmedia_posts", gson.toJson(posts)) }
        MemberHelper.savePeople(this, people)
    }
    private fun openProfile(userId: String) {
        val intent = android.content.Intent(this, SysmediaProfileActivity::class.java)
        intent.putExtra("profile_user_id", userId); intent.putExtra("active_member_id", activeMemberId); startActivity(intent)
    }
    private fun renderPoll(layout: LinearLayout?, post: SysmediaPost) {
        if (layout == null) return
        val poll = post.poll
        if (poll == null) {
            layout.visibility = View.GONE
            return
        }
        layout.visibility = View.VISIBLE
        layout.removeAllViews()
        val textColor = ColorHelper.getTextColor(this)
        val btnColor = ColorHelper.getBtnColor(this)
        val activeUserId = activeMemberId
        val hasVoted = poll.votes.containsKey(activeUserId)
        val totalVotes = poll.votes.size
        poll.options.forEachIndexed { index, option ->
            val optionLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dpToPx() }
            }
            if (hasVoted) {
                val votesForThis = poll.votes.values.count { it == index }
                val percentage = if (totalVotes > 0) (votesForThis * 100 / totalVotes) else 0
                val isMyVote = poll.votes[activeUserId] == index
                val resultRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                resultRow.addView(TextView(this).apply {
                    text = option
                    setTextColor(textColor)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    if (isMyVote) setTypeface(null, android.graphics.Typeface.BOLD)
                })
                if (isMyVote) {
                    resultRow.addView(ImageView(this).apply {
                        setImageResource(android.R.drawable.checkbox_on_background)
                        setColorFilter(btnColor)
                        layoutParams = LinearLayout.LayoutParams(16.dpToPx(), 16.dpToPx()).apply { marginStart = 4.dpToPx() }
                    })
                }
                resultRow.addView(TextView(this).apply {
                    text = "$percentage%"
                    setTextColor(textColor)
                    setPadding(8.dpToPx(), 0, 0, 0)
                })
                optionLayout.addView(resultRow)
                val progressBarContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(-1, 4.dpToPx()).apply { topMargin = 4.dpToPx() }
                    setBackgroundColor(textColor and 0x22FFFFFF)
                    weightSum = 100f
                }
                val progressFill = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, -1, percentage.toFloat())
                    setBackgroundColor(btnColor)
                }
                progressBarContainer.addView(progressFill)
                optionLayout.addView(progressBarContainer)
            } else {
                optionLayout.addView(com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = option
                    setTextColor(btnColor)
                    strokeColor = android.content.res.ColorStateList.valueOf(btnColor)
                    isAllCaps = false
                    setOnClickListener {
                        poll.votes[activeUserId] = index
                        saveData()
                        renderMainPost()
                    }
                })
            }
            layout.addView(optionLayout)
        }
        if (totalVotes > 0) {
            layout.addView(TextView(this).apply {
                text = getString(R.string.poll_votes_count, totalVotes)
                textSize = 12f
                alpha = 0.6f
                setTextColor(textColor)
            })
        }
    }
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun downloadPostAsImage(view: View, post: SysmediaPost) {
        val btns = listOf(R.id.btnReply, R.id.btnReblog, R.id.btnLike, R.id.btnShare)
        btns.forEach { view.findViewById<View>(it)?.visibility = View.INVISIBLE }

        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.background?.draw(canvas) ?: canvas.drawColor(android.graphics.Color.WHITE)
        view.draw(canvas)

        btns.forEach { view.findViewById<View>(it)?.visibility = View.VISIBLE }

        val fileName = "sysmedia_${post.id.take(5)}_${System.currentTimeMillis()}.jpg"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
            }
        }

        val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { os ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, os)
            }
            Toast.makeText(this, getString(R.string.saved_in, "Galerij"), Toast.LENGTH_SHORT).show()
        }
    }

    private inner class InteractionAdapter(private val items: List<Any>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int): Int = if (items[position] is SysmediaPost) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) ReplyViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_sysmedia_post, parent, false))
            else NotificationViewHolder(TextView(parent.context).apply { setPadding(48, 16, 16, 16); textSize = 14f; setTextColor(ColorHelper.getTextColor(this@SysmediaPostDetailActivity) and 0x88FFFFFF.toInt()) })
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            val textColor = ColorHelper.getTextColor(this@SysmediaPostDetailActivity)
            if (holder is ReplyViewHolder && item is SysmediaPost) {
                holder.layoutThreadParent.visibility = View.GONE
                holder.cardOriginal.visibility = View.GONE
                holder.layoutReblog.visibility = View.GONE
                if (item.reblogOfId != null) {
                    val originalPost = posts.find { it.id == item.reblogOfId }
                    if (originalPost != null) {
                        holder.cardOriginal.visibility = View.VISIBLE
                        holder.cardOriginal.setCardBackgroundColor(ColorHelper.getBgColor(this@SysmediaPostDetailActivity))
                        val parentSender = people.find { it.id == originalPost.senderId }
                        val parentProfile = parentSender?.sysmediaProfile
                        holder.tvOriginalName.text = parentProfile?.displayName ?: parentSender?.name ?: "Unknown"
                        val parentHandle = parentProfile?.handle ?: parentSender?.name?.replace(" ", "_")?.lowercase()?.replace(Regex("[^a-z0-9_]"), "") ?: parentSender?.manualId ?: "unknown"
                        holder.tvOriginalHandle.text = "@$parentHandle"
                        markwon.setMarkdown(holder.tvOriginalContent, originalPost.content)
                        val parentAvatar = parentProfile?.profilePictureUri ?: parentSender?.profilePictureUri
                        if (parentAvatar != null) holder.ivOriginalAvatar.load(parentAvatar)
                        else holder.ivOriginalAvatar.setImageDrawable(android.graphics.drawable.ColorDrawable(parentSender?.profileColor ?: ColorHelper.getBtnColor(this@SysmediaPostDetailActivity)))
                        if (originalPost.imageUri != null) {
                            holder.ivOriginalPostImage.visibility = View.VISIBLE
                            holder.ivOriginalPostImage.load(originalPost.imageUri)
                        } else {
                            holder.ivOriginalPostImage.visibility = View.GONE
                        }
                        holder.tvOriginalName.setTextColor(textColor)
                        holder.tvOriginalContent.setTextColor(textColor)
                        holder.tvOriginalHandle.setTextColor(textColor and 0x88FFFFFF.toInt())
                    }
                }
                val sender = people.find { it.id == item.senderId }
                val profile = sender?.sysmediaProfile
                holder.tvName.text = profile?.displayName ?: sender?.name ?: "Unknown"
                holder.tvName.setTextColor(textColor)
                val handle = profile?.handle ?: sender?.name?.replace(" ", "_")?.lowercase()?.replace(Regex("[^a-z0-9_]"), "") ?: sender?.manualId ?: "unknown"
                holder.tvHandle.text = "@$handle"
                holder.tvHandle.setTextColor(textColor and 0x88FFFFFF.toInt())
                markwon.setMarkdown(holder.tvContent, item.content)
                holder.tvContent.setTextColor(textColor)
                val avatarUri = profile?.profilePictureUri ?: sender?.profilePictureUri
                if (avatarUri != null) holder.ivAvatar.load(avatarUri)
                else holder.ivAvatar.setImageDrawable(android.graphics.drawable.ColorDrawable(sender?.profileColor ?: ColorHelper.getBtnColor(this@SysmediaPostDetailActivity)))
                if (item.imageUri != null) {
                    holder.ivImage.visibility = View.VISIBLE
                    holder.ivImage.load(item.imageUri)
                } else {
                    holder.ivImage.visibility = View.GONE
                }
                val sdfShort = SimpleDateFormat("HH:mm · dd MMM", Locale.getDefault())
                holder.itemView.findViewById<TextView>(R.id.tvTimeBottom).text = sdfShort.format(Date(item.timestamp))
                holder.itemView.findViewById<TextView>(R.id.tvTimeBottom).setTextColor(textColor and 0x88FFFFFF.toInt())
                holder.itemView.findViewById<TextView>(R.id.tvLikes).text = item.likes.toString()
                holder.itemView.findViewById<TextView>(R.id.tvRetweets).text = item.retweets.toString()
                holder.itemView.findViewById<TextView>(R.id.tvReplies).text = item.replies.toString()
                val activeLikes = item.likedByMemberIds[activeMemberId] ?: 0
                val likeIconColor = if (activeLikes > 0) ColorHelper.getBtnColor(this@SysmediaPostDetailActivity) else (textColor and 0x88FFFFFF.toInt())
                holder.itemView.findViewById<TextView>(R.id.tvLikes).setTextColor(likeIconColor)
                holder.itemView.findViewById<ImageView>(R.id.ivLikeIcon)?.setColorFilter(likeIconColor)
                MediaEmbedHelper.addEmbedsToContainer(holder.mediaEmbedContainer, item.content)
                renderPoll(holder.layoutPoll, item)
                holder.itemView.setOnClickListener {
                    val intent = android.content.Intent(this@SysmediaPostDetailActivity, SysmediaPostDetailActivity::class.java)
                    intent.putExtra("post_id", item.id); intent.putExtra("active_member_id", activeMemberId); startActivity(intent)
                }
                holder.itemView.findViewById<View>(R.id.btnReply).setOnClickListener {
                    val intent = android.content.Intent(this@SysmediaPostDetailActivity, CreatePostActivity::class.java)
                    intent.putExtra("current_user_id", activeMemberId)
                    intent.putExtra("reply_to_id", item.id)
                    startActivity(intent)
                }
                holder.itemView.findViewById<View>(R.id.btnReblog).setOnClickListener {
                    val intent = android.content.Intent(this@SysmediaPostDetailActivity, CreatePostActivity::class.java)
                    intent.putExtra("current_user_id", activeMemberId)
                    intent.putExtra("reblog_of_id", item.id)
                    startActivity(intent)
                }
                holder.btnShare.setOnClickListener {
                    downloadPostAsImage(holder.itemView, item)
                }
                holder.btnLike.setOnClickListener {
                    val memberId = activeMemberId
                    val currentLikes = item.likedByMemberIds[memberId] ?: 0
                    if (currentLikes < 3) {
                        item.likedByMemberIds[memberId] = currentLikes + 1
                        item.likes++
                        if (currentLikes == 0 && item.senderId != memberId) {
                            val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                            val notifJson = sharedPref.getString("sysmedia_notifications", "[]")
                            val notifications: MutableList<SysmediaNotification> = gson.fromJson(notifJson, object : TypeToken<MutableList<SysmediaNotification>>() {}.type) ?: mutableListOf()
                            notifications.add(0, SysmediaNotification(receiverId = item.senderId, senderId = memberId, type = "LIKE", postId = item.id))
                            sharedPref.edit().putString("sysmedia_notifications", Gson().toJson(notifications)).apply()
                            SysmediaNotificationHelper.checkAndNotify(this@SysmediaPostDetailActivity, item.senderId)
                        }
                    } else {
                        item.likes -= 3
                        item.likedByMemberIds[memberId] = 0
                    }
                    saveData()
                    notifyItemChanged(position)
                }
                holder.itemView.setOnLongClickListener {
                    if (item.senderId == activeMemberId) {
                        val options = arrayOf(getString(R.string.action_edit_post), getString(R.string.delete))
                        androidx.appcompat.app.AlertDialog.Builder(this@SysmediaPostDetailActivity)
                            .setItems(options) { _, which ->
                                when (options[which]) {
                                    getString(R.string.action_edit_post) -> {
                                        val intent = android.content.Intent(this@SysmediaPostDetailActivity, CreatePostActivity::class.java)
                                        intent.putExtra("current_user_id", activeMemberId)
                                        intent.putExtra("edit_post_id", item.id)
                                        startActivity(intent)
                                    }
                                    getString(R.string.delete) -> {
                                        if (item.replyToId != null) {
                                            posts.find { it.id == item.replyToId }?.let { original ->
                                                if (original.replies > 0) original.replies--
                                            }
                                        }
                                        if (item.reblogOfId != null) {
                                            posts.find { it.id == item.reblogOfId }?.let { original ->
                                                if (original.retweets > 0) original.retweets--
                                            }
                                        }
                                        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                                        val notifJson = sharedPref.getString("sysmedia_notifications", "[]")
                                        val typeNotifs = object : TypeToken<MutableList<SysmediaNotification>>() {}.type
                                        val notifications: MutableList<SysmediaNotification> = gson.fromJson(notifJson, typeNotifs) ?: mutableListOf()
                                        notifications.removeAll { it.postId == item.id }
                                        sharedPref.edit { putString("sysmedia_notifications", Gson().toJson(notifications)) }
                                        posts.remove(item)
                                        saveData()
                                        setupInteractions()
                                        renderMainPost()
                                    }
                                }
                            }.show()
                    }
                    true
                }
            } else if (holder is NotificationViewHolder && item is SysmediaNotification) {
                val sender = people.find { it.id == item.senderId }
                val typeStr = when(item.type) { "LIKE" -> "liked this post"; "REBLOG" -> "reblogged this post"; "NEW_POST" -> "posted something new"; else -> "interacted" }
                val timeStr = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp))
                holder.textView.text = "${sender?.name ?: "Someone"} $typeStr ($timeStr)"
            }
        }
        override fun getItemCount(): Int = items.size
        inner class ReplyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val layoutThreadParent: View = view.findViewById(R.id.layoutThreadParent)
            val ivParentAvatar: ImageView = view.findViewById(R.id.ivParentAvatar)
            val tvParentName: TextView = view.findViewById(R.id.tvParentName)
            val tvParentContent: TextView = view.findViewById(R.id.tvParentContent)
            val layoutReblog: View = view.findViewById(R.id.layoutReblogHeader)
            val tvRebloggedBy: TextView = view.findViewById(R.id.tvRebloggedBy)
            val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvHandle: TextView = view.findViewById(R.id.tvHandle)
            val tvContent: TextView = view.findViewById(R.id.tvContent)
            val ivImage: ImageView = view.findViewById(R.id.ivPostImagePreview)
            val btnLike: View = view.findViewById(R.id.btnLike)
            val btnShare: View = view.findViewById(R.id.btnShare)
            val mediaEmbedContainer: LinearLayout = view.findViewById(R.id.mediaEmbedContainer)
            val cardOriginal: MaterialCardView = view.findViewById(R.id.cardOriginalPost)
            val ivOriginalAvatar: ImageView = view.findViewById(R.id.ivOriginalAvatar)
            val tvOriginalName: TextView = view.findViewById(R.id.tvOriginalName)
            val tvOriginalHandle: TextView = view.findViewById(R.id.tvOriginalHandle)
            val tvOriginalContent: TextView = view.findViewById(R.id.tvOriginalContent)
            val ivOriginalPostImage: ImageView = view.findViewById(R.id.ivOriginalPostImage)
            val layoutPoll: LinearLayout = view.findViewById(R.id.layoutPoll)
        }
        inner class NotificationViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    }
}