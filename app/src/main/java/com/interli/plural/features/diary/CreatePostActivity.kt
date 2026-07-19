package com.interli.plural.features.diary

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.features.sysmedia.SysmediaNotificationHelper
import com.interli.plural.Group
import com.interli.plural.Person
import com.interli.plural.R
import com.interli.plural.SysmediaNotification
import com.interli.plural.SysmediaPoll
import com.interli.plural.SysmediaPost
import com.interli.plural.SysmediaProfile
import java.util.*

class CreatePostActivity : BaseActivity() {
    data class SuggestionItem(val display: String, val value: String, val type: String)
    private lateinit var people: List<Person>
    private lateinit var currentUser: Person
    private var reblogOfId: String? = null
    private var replyToId: String? = null
    private var editPostId: String? = null
    private lateinit var rvSuggestions: RecyclerView
    private lateinit var suggestionAdapter: SuggestionAdapter
    private var selectedImageUri: Uri? = null
    private var scheduledTimestamp: Long? = null
    private var pollOptions = mutableListOf<String>()
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val file = java.io.File(filesDir, "post_img_${System.currentTimeMillis()}.jpg")
                val outputStream = java.io.FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                selectedImageUri = Uri.fromFile(file)
                findViewById<ImageView>(R.id.ivPostImage).apply {
                    visibility = View.VISIBLE
                    load(selectedImageUri)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)
        loadData()
        val currentUserId = intent.getStringExtra("current_user_id")
        currentUser = people.find { it.id == currentUserId } ?: people.firstOrNull { it.isFront } ?: people.first()
        reblogOfId = intent.getStringExtra("reblog_of_id")
        replyToId = intent.getStringExtra("reply_to_id")
        editPostId = intent.getStringExtra("edit_post_id")
        val ivAvatar = findViewById<ImageView>(R.id.ivAvatar)
        val etContent = findViewById<EditText>(R.id.etContent)
        val btnPost = findViewById<Button>(R.id.btnPostAction)
        val btnSwitch = findViewById<android.widget.ImageButton>(R.id.btnSwitchUser)
        updateAvatar(ivAvatar)
        btnSwitch.setOnClickListener {
            showAccountSwitchDialog(ivAvatar)
        }
        ColorHelper.applySettings(this)
        val textColor = ColorHelper.getTextColor(this)
        btnSwitch.setColorFilter(textColor)
        findViewById<android.widget.ImageButton>(R.id.btnAddImage).setOnClickListener {
            pickImage.launch("image/*")
        }
        findViewById<android.widget.ImageButton>(R.id.btnAddImage).setColorFilter(ColorHelper.getBtnColor(this))
        findViewById<android.widget.ImageButton>(R.id.btnSchedule).setOnClickListener {
            showDateTimePicker()
        }
        findViewById<android.widget.ImageButton>(R.id.btnSchedule).setColorFilter(ColorHelper.getBtnColor(this))
        findViewById<android.widget.ImageButton>(R.id.btnPoll).setOnClickListener {
            togglePollLayout()
        }
        findViewById<android.widget.ImageButton>(R.id.btnPoll).setColorFilter(ColorHelper.getBtnColor(this))
        findViewById<Button>(R.id.btnAddPollOption).setOnClickListener {
            addPollOptionRow("")
        }
        rvSuggestions = findViewById(R.id.rvSuggestions)
        rvSuggestions.layoutManager = LinearLayoutManager(this)
        suggestionAdapter = SuggestionAdapter { selectedItem ->
            insertTag(selectedItem)
        }
        rvSuggestions.adapter = suggestionAdapter
        etContent.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s.toString()
                val cursor = etContent.selectionStart
                if (cursor > 0) {
                    val part = text.substring(0, cursor)
                    val lastSpace = part.lastIndexOf(" ").coerceAtLeast(part.lastIndexOf("\n"))
                    val currentWord = if (lastSpace == -1) part else part.substring(lastSpace + 1)
                    if (currentWord.startsWith("@")) {
                        val query = currentWord.substring(1).lowercase()
                        val filtered = people.filter { 
                            it.name.lowercase().contains(query) || 
                            it.sysmediaProfile?.handle?.lowercase()?.contains(query) == true 
                        }.map { 
                            val handle = it.sysmediaProfile?.handle ?: it.name.replace(" ", "_").lowercase()
                            SuggestionItem(it.name + " (@$handle)", handle, "USER") 
                        }.toMutableList()
                        if ("everyone".contains(query) || query.isEmpty()) {
                            filtered.add(0, SuggestionItem("@everyone (Notify all)", "everyone", "USER"))
                        }
                        suggestionAdapter.updateItems(filtered)
                        rvSuggestions.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
                    } else if (currentWord.startsWith("#")) {
                        val query = currentWord.substring(1).lowercase()
                        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                        val hashtags = sharedPref.getStringSet("sysmedia_hashtags", emptySet()) ?: emptySet()
                        val filtered = hashtags.filter { it.lowercase().contains(query) }
                            .map { SuggestionItem("#$it", it, "TAG") }
                        suggestionAdapter.updateItems(filtered)
                        rvSuggestions.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
                    } else {
                        rvSuggestions.visibility = View.GONE
                    }
                } else {
                    rvSuggestions.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        if (editPostId != null) {
            val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
            val postsJson = sharedPref.getString("sysmedia_posts", "[]")
            val typePosts = object : TypeToken<List<SysmediaPost>>() {}.type
            val posts: List<SysmediaPost> = Gson().fromJson(postsJson, typePosts) ?: emptyList()
            val post = posts.find { it.id == editPostId }
            etContent.setText(post?.content ?: "")
            btnPost.text = getString(R.string.save)
            post?.imageUri?.let {
                selectedImageUri = Uri.parse(it)
                findViewById<ImageView>(R.id.ivPostImage).apply {
                    visibility = View.VISIBLE
                    load(selectedImageUri)
                }
            }
        } else if (reblogOfId != null) {
            btnPost.text = getString(R.string.action_reblog)
            showReblogPreview()
        } else if (replyToId != null) {
            btnPost.text = getString(R.string.action_reply)
        }
        btnPost.setOnClickListener {
            val content = etContent.text.toString().trim()
            if (content.isNotEmpty() || selectedImageUri != null || reblogOfId != null) {
                val tags = mutableSetOf<String>()
                val matcher = java.util.regex.Pattern.compile("#([A-Za-z0-9_]+)").matcher(content)
                while (matcher.find()) {
                    matcher.group(1)?.let { tags.add(it) }
                }
                if (tags.isNotEmpty()) {
                    val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                    val existing = sharedPref.getStringSet("sysmedia_hashtags", emptySet()) ?: emptySet()
                    val newSet = existing.toMutableSet()
                    newSet.addAll(tags)
                    sharedPref.edit(commit = true) { putStringSet("sysmedia_hashtags", newSet) }
                }
                saveNewPost(content)
                finish()
            }
        }
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener {
            finish()
        }
        ColorHelper.applySettings(this)
        findViewById<View>(R.id.topAppBar).setBackgroundColor(ColorHelper.getBgColor(this))
        etContent.setTextColor(ColorHelper.getTextColor(this))
        etContent.setHintTextColor(ColorHelper.getTextColor(this) and 0x88FFFFFF.toInt())
    }
    private fun showReblogPreview() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val postsJson = sharedPref.getString("sysmedia_posts", "[]")
        val posts: List<SysmediaPost> = Gson().fromJson(postsJson, object : TypeToken<List<SysmediaPost>>() {}.type)
        val original = posts.find { it.id == reblogOfId } ?: return
        findViewById<View>(R.id.cardOriginalPost).visibility = View.VISIBLE
        val sender = people.find { it.id == original.senderId }
        findViewById<TextView>(R.id.tvOriginalName).text = sender?.sysmediaProfile?.displayName ?: sender?.name ?: "Unknown"
        findViewById<TextView>(R.id.tvOriginalContent).text = original.content
        val avatar = sender?.sysmediaProfile?.profilePictureUri ?: sender?.profilePictureUri
        if (avatar != null) findViewById<ImageView>(R.id.ivOriginalAvatar).load(avatar)
    }
    private fun updateAvatar(iv: ImageView) {
        val avatarUri = currentUser.sysmediaProfile?.profilePictureUri ?: currentUser.profilePictureUri
        if (avatarUri != null) iv.load(avatarUri)
        else iv.setImageDrawable(android.graphics.drawable.ColorDrawable(if (currentUser.profileColor == -6934396) ColorHelper.getBtnColor(this) else currentUser.profileColor))
    }
    private fun showAccountSwitchDialog(iv: ImageView) {
        val accounts = people.filter { !it.isArchived }
        val names = accounts.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Switch Account")
            .setItems(names) { _, which ->
                currentUser = accounts[which]
                updateAvatar(iv)
            }.show()
    }
    private fun showDateTimePicker() {
        val cal = Calendar.getInstance()
        android.app.DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            android.app.TimePickerDialog(this, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, min)
                scheduledTimestamp = cal.timeInMillis
                Toast.makeText(this, "Scheduled for: " + java.text.SimpleDateFormat("dd/MM HH:mm").format(cal.time), Toast.LENGTH_SHORT).show()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
    private fun togglePollLayout() {
        val layout = findViewById<View>(R.id.layoutPollOptions)
        if (layout.visibility == View.VISIBLE) {
            layout.visibility = View.GONE
        } else {
            layout.visibility = View.VISIBLE
            val container = findViewById<LinearLayout>(R.id.pollOptionsContainer)
            if (container.childCount == 0) {
                addPollOptionRow("")
                addPollOptionRow("")
            }
        }
    }
    private fun addPollOptionRow(initialText: String) {
        val container = findViewById<LinearLayout>(R.id.pollOptionsContainer)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { 
                setMargins(0, 0, 0, 8.dpToPx()) 
            }
        }
        val et = EditText(this).apply {
            hint = getString(R.string.poll_option_hint, container.childCount + 1)
            setText(initialText)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(ColorHelper.getTextColor(this@CreatePostActivity))
        }
        val btnRemove = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            alpha = 0.5f
            setOnClickListener { container.removeView(row) }
        }
        row.addView(et)
        row.addView(btnRemove)
        container.addView(row)
    }
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private fun insertTag(item: SuggestionItem) {
        val etContent = findViewById<EditText>(R.id.etContent)
        val text = etContent.text.toString()
        val cursor = etContent.selectionStart
        val part = text.substring(0, cursor)
        val lastSpace = part.lastIndexOf(" ").coerceAtLeast(part.lastIndexOf("\n"))
        val prefix = when (item.type) {
            "USER" -> "@"
            "TAG" -> "#"
            else -> ""
        }
        val newText = if (lastSpace == -1) {
            prefix + item.value + " " + text.substring(cursor)
        } else {
            text.substring(0, lastSpace + 1) + prefix + item.value + " " + text.substring(cursor)
        }
        etContent.setText(newText)
        etContent.setSelection(newText.indexOf(" ", lastSpace + 1) + 1)
        rvSuggestions.visibility = View.GONE
    }
    private inner class SuggestionAdapter(val onClick: (SuggestionItem) -> Unit) : RecyclerView.Adapter<SuggestionAdapter.ViewHolder>() {
        private var items: List<SuggestionItem> = listOf()
        fun updateItems(newItems: List<SuggestionItem>) {
            items = newItems
            notifyDataSetChanged()
        }
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(android.R.id.text1)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.display
            holder.tvName.setTextColor(ColorHelper.getTextColor(this@CreatePostActivity))
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = items.size
    }
    private fun loadData() {
        people = MemberHelper.loadAllPeople(this)
    }
    private fun saveImageToInternalStorage(uri: Uri, fileName: String): Uri? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = java.io.File(filesDir, "$fileName.png")
            java.io.FileOutputStream(file).use { output -> inputStream.use { input -> input.copyTo(output) } }
            Uri.fromFile(file)
        } catch (e: Exception) { null }
    }
    private fun saveNewPost(content: String) {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val postsJson = sharedPref.getString("sysmedia_posts", "[]")
        val typePosts = object : TypeToken<MutableList<SysmediaPost>>() {}.type
        val posts: MutableList<SysmediaPost> = Gson().fromJson(postsJson, typePosts) ?: mutableListOf()
        posts.forEach { 
            @Suppress("SENSELESS_COMPARISON")
            if (it.likedByMemberIds == null) it.likedByMemberIds = mutableMapOf() 
        }
        val notifJson = sharedPref.getString("sysmedia_notifications", "[]")
        val typeNotifs = object : TypeToken<MutableList<SysmediaNotification>>() {}.type
        val notifications: MutableList<SysmediaNotification> = Gson().fromJson(notifJson, typeNotifs) ?: mutableListOf()
        val pollContainer = findViewById<LinearLayout>(R.id.pollOptionsContainer)
        val options = mutableListOf<String>()
        for (i in 0 until pollContainer.childCount) {
            val row = pollContainer.getChildAt(i) as? LinearLayout
            val et = row?.getChildAt(0) as? EditText
            val opt = et?.text?.toString()?.trim() ?: ""
            if (opt.isNotEmpty()) options.add(opt)
        }
        val poll = if (findViewById<View>(R.id.layoutPollOptions).visibility == View.VISIBLE && options.size >= 2) {
            SysmediaPoll(options = options)
        } else null
        if (editPostId != null) {
            val post = posts.find { it.id == editPostId }
            if (post != null) {
                post.content = content
                post.imageUri = selectedImageUri?.toString()
                post.senderId = currentUser.id
                post.poll = poll
            } else {
                val newPost = SysmediaPost(
                    senderId = currentUser.id,
                    content = content,
                    imageUri = selectedImageUri?.let { uri ->
                        saveImageToInternalStorage(uri, "post_${System.currentTimeMillis()}")?.toString()
                    },
                    poll = poll
                )
                posts.add(0, newPost)
            }
        } else {
            val newPost = SysmediaPost(
                senderId = currentUser.id,
                content = content,
                reblogOfId = reblogOfId,
                replyToId = replyToId,
                imageUri = selectedImageUri?.let { uri ->
                    saveImageToInternalStorage(uri, "post_${System.currentTimeMillis()}")?.toString()
                },
                scheduledTime = scheduledTimestamp,
                poll = poll
            )
            posts.add(0, newPost)
            val words = content.split(" ", "\n")
            val taggedIds = mutableSetOf<String>()
            if (content.contains("@everyone", ignoreCase = true)) {
                people.forEach { person ->
                    if (person.id != currentUser.id) {
                        taggedIds.add(person.id)
                        notifications.add(0, SysmediaNotification(
                            receiverId = person.id,
                            senderId = currentUser.id,
                            type = "EVERYONE",
                            postId = newPost.id
                        ))
                        SysmediaNotificationHelper.checkAndNotify(this@CreatePostActivity, person.id)
                    }
                }
            }
            words.filter { it.startsWith("@") && it.length > 1 }.forEach { tag ->
                val handle = tag.substring(1).lowercase().replace(Regex("[^a-z0-9_]"), "")
                val taggedPerson = people.find { 
                    it.sysmediaProfile?.handle?.lowercase() == handle || 
                    it.name.replace(" ", "_").lowercase() == handle 
                }
                if (taggedPerson != null && taggedPerson.id != currentUser.id && !taggedIds.contains(taggedPerson.id)) {
                    taggedIds.add(taggedPerson.id)
                    notifications.add(0, SysmediaNotification(
                        receiverId = taggedPerson.id,
                        senderId = currentUser.id,
                        type = "TAG",
                        postId = newPost.id
                    ))
                    SysmediaNotificationHelper.checkAndNotify(this@CreatePostActivity, taggedPerson.id)
                }
            }
            if (reblogOfId != null) {
                val original = posts.find { it.id == reblogOfId }
                original?.retweets = (original?.retweets ?: 0) + 1
                if (original != null && original.senderId != currentUser.id) {
                    notifications.add(0, SysmediaNotification(
                        receiverId = original.senderId,
                        senderId = currentUser.id,
                        type = "REBLOG",
                        postId = newPost.id
                    ))
                }
            }
            if (replyToId != null) {
                val original = posts.find { it.id == replyToId }
                original?.replies = (original?.replies ?: 0) + 1
                if (original != null && original.senderId != currentUser.id) {
                    notifications.add(0, SysmediaNotification(
                        receiverId = original.senderId,
                        senderId = currentUser.id,
                        type = "REPLY",
                        postId = newPost.id
                    ))
                    SysmediaNotificationHelper.checkAndNotify(this@CreatePostActivity, original.senderId)
                }
            }
        }
        sharedPref.edit(commit = true) {
            putString("sysmedia_posts", Gson().toJson(posts))
            putString("sysmedia_notifications", Gson().toJson(notifications))
        }
    }
}
