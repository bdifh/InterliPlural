package com.interli.plural

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.noties.markwon.Markwon
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : BaseActivity() {

    private lateinit var messages: MutableList<DirectMessage>
    private lateinit var chatGroups: MutableList<ChatGroup>
    private lateinit var people: List<Person>
    private lateinit var currentUser: Person
    private var chatId: String? = null
    private var isGroup: Boolean = false
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var markwon: Markwon
    private var selectedImageUri: android.net.Uri? = null
    private var replyingTo: DirectMessage? = null

    private val pickImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val prefix = if (isChangingGroupPicture) "group_pic_" else "chat_msg_"
                val file = java.io.File(filesDir, "${prefix}${System.currentTimeMillis()}.jpg")
                val outputStream = java.io.FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                
                val newUri = android.net.Uri.fromFile(file)
                if (isChangingGroupPicture) {
                    updateGroupPicture(newUri.toString())
                } else {
                    selectedImageUri = newUri
                    findViewById<View>(R.id.layoutChatImagePreview).visibility = View.VISIBLE
                    findViewById<ImageView>(R.id.ivChatPreview).load(selectedImageUri)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    private var isChangingGroupPicture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val currentId = intent.getStringExtra("current_id")
        chatId = intent.getStringExtra("other_id")
        isGroup = intent.getBooleanExtra("is_group", false)

        loadData()

        currentUser = people.find { it.id == currentId } ?: people.first()
        
        val tvChatName = findViewById<TextView>(R.id.tvChatName)
        val ivChatAvatar = findViewById<ImageView>(R.id.ivChatAvatar)
        
        if (isGroup) {
            val group = chatGroups.find { it.id == chatId }
            tvChatName.text = group?.name ?: "Group"
            if (group?.groupPictureUri != null) {
                ivChatAvatar.load(group.groupPictureUri)
            } else {
                ivChatAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
            }
            
            ivChatAvatar.setOnClickListener {
                isChangingGroupPicture = true
                pickImage.launch("image/*")
            }

            val btnManage = findViewById<ImageButton>(R.id.btnManageGroup)
            btnManage.visibility = View.VISIBLE
            btnManage.setOnClickListener { showManageGroupDialog() }
        } else {
            val otherUser = people.find { it.id == chatId }
            tvChatName.text = otherUser?.sysmediaProfile?.displayName ?: otherUser?.name ?: "User"
            val avatarUri = otherUser?.sysmediaProfile?.profilePictureUri ?: otherUser?.profilePictureUri
            if (avatarUri != null) ivChatAvatar.load(avatarUri) else ivChatAvatar.setImageResource(R.drawable.ic_stat_name)
        }

        recyclerView = findViewById(R.id.chatRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        
        filterMessages()
        adapter = ChatAdapter(messages)
        recyclerView.adapter = adapter

        findViewById<ImageButton>(R.id.btnSendChat).setOnClickListener {
            val content = findViewById<EditText>(R.id.etChatMessage).text.toString()
            if (content.trim().isNotEmpty() || selectedImageUri != null) {
                sendMessage(content.trim())
                findViewById<EditText>(R.id.etChatMessage).setText("")
                selectedImageUri = null
                findViewById<View>(R.id.layoutChatImagePreview).visibility = View.GONE
                cancelReply()
            }
        }

        findViewById<ImageButton>(R.id.btnCancelReply).setOnClickListener {
            cancelReply()
        }

        findViewById<ImageButton>(R.id.btnAddChatMedia).setOnClickListener {
            isChangingGroupPicture = false
            pickImage.launch("image/*")
        }
        findViewById<ImageButton>(R.id.btnRemoveChatImage).setOnClickListener {
            selectedImageUri = null
            findViewById<View>(R.id.layoutChatImagePreview).visibility = View.GONE
        }

        val btnSwitchFront = findViewById<ImageButton>(R.id.btnSwitchFrontChat)
        if (isGroup) {
            btnSwitchFront.visibility = View.VISIBLE
            btnSwitchFront.setOnClickListener {
                showSwitchActiveUserDialog()
            }
        }

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar.setNavigationOnClickListener { finish() }
        
        ColorHelper.applySettings(this)
        val textColor = ColorHelper.getTextColor(this)
        val btnColor = ColorHelper.getBtnColor(this)
        val btnTextColor = ColorHelper.getBtnTextColor(this)
        val bgColor = ColorHelper.getBgColor(this)
        
        toolbar.setBackgroundColor(bgColor)
        tvChatName.setTextColor(textColor)
        toolbar.setNavigationIconTint(textColor)
        findViewById<ImageButton>(R.id.btnSendChat).setColorFilter(btnColor)
        findViewById<ImageButton>(R.id.btnManageGroup).setColorFilter(textColor)
        findViewById<ImageButton>(R.id.btnSwitchFrontChat).setColorFilter(textColor)

        findViewById<View>(R.id.layoutInput).setBackgroundColor(bgColor)
        findViewById<View>(R.id.layoutReplyPreview).setBackgroundColor(bgColor)
        findViewById<View>(R.id.viewReplyIndicator).setBackgroundColor(btnColor)
        findViewById<TextView>(R.id.tvReplySender).setTextColor(btnColor)
        findViewById<TextView>(R.id.tvReplyContent).setTextColor(textColor)

        findViewById<EditText>(R.id.etChatMessage).apply {
            setTextColor(textColor)
            setHintTextColor(textColor and 0x88FFFFFF.toInt())
        }
        findViewById<ImageButton>(R.id.btnAddChatMedia).setColorFilter(textColor)

        markwon = Markwon.builder(this)
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(this))
            .usePlugin(io.noties.markwon.image.coil.CoilImagesPlugin.create(this))
            .usePlugin(io.noties.markwon.linkify.LinkifyPlugin.create())
            .build()
        
        markMessagesAsRead()
    }

    private fun showManageGroupDialog() {
        val group = chatGroups.find { it.id == chatId } ?: return
        val memberNames = people.filter { group.participantIds.contains(it.id) }.map { it.name }.toMutableList()
        
        val options = mutableListOf<String>()
        options.add("+ Add Member")
        options.addAll(memberNames.map { "Remove: $it" })

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(group.name)
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    showAddMemberDialog(group)
                } else {
                    val personToRemove = people.filter { group.participantIds.contains(it.id) }[which - 1]
                    group.participantIds.remove(personToRemove.id)
                    saveGroups()
                    showManageGroupDialog()
                }
            }
            .setPositiveButton(R.string.done, null)
            .show()
    }

    private fun showAddMemberDialog(group: ChatGroup) {
        val nonParticipants = people.filter { !group.participantIds.contains(it.id) }
        if (nonParticipants.isEmpty()) {
            Toast.makeText(this, "No more members to add", Toast.LENGTH_SHORT).show()
            return
        }

        val names = nonParticipants.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add Member")
            .setItems(names) { _, which ->
                group.participantIds.add(nonParticipants[which].id)
                saveGroups()
                showManageGroupDialog()
            }
            .show()
    }

    private fun saveGroups() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        sharedPref.edit { putString("sysmedia_chat_groups", Gson().toJson(chatGroups)) }
    }

    private fun getDayString(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val now = Calendar.getInstance()
        
        return when {
            isSameDay(cal, now) -> getString(R.string.today)
            isSameDay(cal, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }) -> getString(R.string.yesterday)
            else -> SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(cal.time)
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun markMessagesAsRead() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val msgJson = sharedPref.getString("sysmedia_dms", "[]")
        val typeMsg = object : TypeToken<MutableList<DirectMessage>>() {}.type
        val allMessages: MutableList<DirectMessage> = Gson().fromJson(msgJson, typeMsg) ?: mutableListOf()

        val targetChatId = if (isGroup) chatId!! else {
            val ids = listOf(currentUser.id, chatId!!).sorted()
            "${ids[0]}_${ids[1]}"
        }

        var changed = false
        allMessages.filter { it.chatId == targetChatId && it.senderId != currentUser.id && !it.isRead }.forEach {
            it.isRead = true
            changed = true
        }

        if (changed) {
            sharedPref.edit { putString("sysmedia_dms", Gson().toJson(allMessages)) }
        }
    }

    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        people = MemberHelper.loadAllPeople(this)

        val msgJson = sharedPref.getString("sysmedia_dms", "[]")
        val typeMsg = object : TypeToken<MutableList<DirectMessage>>() {}.type
        val loadedMessages: MutableList<DirectMessage> = Gson().fromJson(msgJson, typeMsg) ?: mutableListOf()
        
        loadedMessages.forEach { msg ->
            if (msg.likedByMemberIds == null) msg.likedByMemberIds = mutableMapOf()
        }
        
        val groupJson = sharedPref.getString("sysmedia_chat_groups", "[]")
        val typeGroup = object : TypeToken<MutableList<ChatGroup>>() {}.type
        chatGroups = Gson().fromJson(groupJson, typeGroup) ?: mutableListOf()
        
        messages = loadedMessages
    }

    private fun filterMessages() {
        val targetChatId = if (isGroup) chatId else {
            val ids = listOf(currentUser.id, chatId!!).sorted()
            "${ids[0]}_${ids[1]}"
        }
        messages = messages.filter { it.chatId == targetChatId }.sortedBy { it.timestamp }.toMutableList()
    }

    private fun startReply(msg: DirectMessage) {
        replyingTo = msg
        val layoutPreview = findViewById<View>(R.id.layoutReplyPreview)
        val tvSender = findViewById<TextView>(R.id.tvReplySender)
        val tvContent = findViewById<TextView>(R.id.tvReplyContent)
        
        layoutPreview.visibility = View.VISIBLE
        val sender = people.find { it.id == msg.senderId }
        tvSender.text = sender?.sysmediaProfile?.displayName ?: sender?.name ?: "Unknown"
        tvContent.text = msg.content
        
        findViewById<EditText>(R.id.etChatMessage).requestFocus()
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(findViewById(R.id.etChatMessage), 0)
    }

    private fun cancelReply() {
        replyingTo = null
        findViewById<View>(R.id.layoutReplyPreview).visibility = View.GONE
    }

    private fun sendMessage(content: String) {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val msgJson = sharedPref.getString("sysmedia_dms", "[]")
        val typeMsg = object : TypeToken<MutableList<DirectMessage>>() {}.type
        val allMessages: MutableList<DirectMessage> = Gson().fromJson(msgJson, typeMsg) ?: mutableListOf()

        val targetChatId = if (isGroup) chatId!! else {
            val ids = listOf(currentUser.id, chatId!!).sorted()
            "${ids[0]}_${ids[1]}"
        }

        val newMsg = DirectMessage(
            senderId = currentUser.id, 
            chatId = targetChatId, 
            content = content, 
            isRead = false,
            imageUri = selectedImageUri?.toString(),
            replyToId = replyingTo?.id
        )
        allMessages.add(newMsg)
        sharedPref.edit(commit = true) { putString("sysmedia_dms", Gson().toJson(allMessages)) }

        messages.add(newMsg)
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }

    private fun updateGroupPicture(uri: String) {
        val group = chatGroups.find { it.id == chatId } ?: return
        group.groupPictureUri = uri
        
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        sharedPref.edit(commit = true) {
            putString("sysmedia_chat_groups", Gson().toJson(chatGroups))
        }
        
        findViewById<ImageView>(R.id.ivChatAvatar).load(uri)
    }

    private fun showEditMessageDialog(msg: DirectMessage, position: Int) {
        val input = android.widget.EditText(this).apply {
            setText(msg.content)
            setSelection(msg.content.length)
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padding = 24.dpToPx()
            setPadding(padding, 8.dpToPx(), padding, 8.dpToPx())
            addView(input)
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Edit Message")
            .setView(container)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val newContent = input.text.toString().trim()
                if (newContent.isNotEmpty() && newContent != msg.content) {
                    val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                    val msgJson = sharedPref.getString("sysmedia_dms", "[]")
                    val typeMsg = object : TypeToken<MutableList<DirectMessage>>() {}.type
                    val allMessages: MutableList<DirectMessage> = Gson().fromJson(msgJson, typeMsg) ?: mutableListOf()
                    
                    val target = allMessages.find { it.id == msg.id }
                    if (target != null) {
                        target.content = newContent
                        target.isEdited = true
                        sharedPref.edit().putString("sysmedia_dms", Gson().toJson(allMessages)).apply()
                        
                        msg.content = newContent
                        msg.isEdited = true
                        adapter.notifyItemChanged(position)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSwitchSenderDialog(msg: DirectMessage, position: Int) {
        val group = chatGroups.find { it.id == chatId } ?: return
        val participants = people.filter { group.participantIds.contains(it.id) }
        val names = participants.map { it.sysmediaProfile?.displayName ?: it.name }.toTypedArray()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.action_switch_account)
            .setItems(names) { _, which ->
                val newSender = participants[which]
                if (newSender.id != msg.senderId) {
                    val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                    val msgJson = sharedPref.getString("sysmedia_dms", "[]")
                    val typeMsg = object : TypeToken<MutableList<DirectMessage>>() {}.type
                    val allMessages: MutableList<DirectMessage> = Gson().fromJson(msgJson, typeMsg) ?: mutableListOf()
                    
                    val target = allMessages.find { it.id == msg.id }
                    if (target != null) {
                        target.senderId = newSender.id
                        sharedPref.edit().putString("sysmedia_dms", Gson().toJson(allMessages)).apply()
                        
                        msg.senderId = newSender.id
                        adapter.notifyItemChanged(position)
                    }
                }
            }
            .show()
    }

    private fun showSwitchActiveUserDialog() {
        val group = chatGroups.find { it.id == chatId } ?: return
        val participants = people.filter { group.participantIds.contains(it.id) }
        val names = participants.map { it.sysmediaProfile?.displayName ?: it.name }.toTypedArray()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.action_switch_account)
            .setItems(names) { _, which ->
                currentUser = participants[which]
                adapter.notifyDataSetChanged()
            }
            .show()
    }

    private fun handleLikeClick(msg: DirectMessage, position: Int) {
        val activeMemberId = currentUser.id
        val currentLikes = msg.safeLikedByMemberIds[activeMemberId] ?: 0

        if (currentLikes < 3) {
            if (msg.likedByMemberIds == null) msg.likedByMemberIds = mutableMapOf()
            msg.safeLikedByMemberIds[activeMemberId] = currentLikes + 1
            msg.likes++
        } else {
            msg.likes -= 3
            msg.safeLikedByMemberIds[activeMemberId] = 0
        }

        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val msgJson = sharedPref.getString("sysmedia_dms", "[]")
        val typeMsg = object : TypeToken<MutableList<DirectMessage>>() {}.type
        val allMessages: MutableList<DirectMessage> = Gson().fromJson(msgJson, typeMsg) ?: mutableListOf()

        val idx = allMessages.indexOfFirst { it.id == msg.id }
        if (idx != -1) {
            allMessages[idx] = msg
            sharedPref.edit { putString("sysmedia_dms", Gson().toJson(allMessages)) }
        }
        adapter.notifyItemChanged(position)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()


    private inner class ChatAdapter(private val items: List<DirectMessage>) :
        RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

        private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val layoutBubbleRow: LinearLayout = view.findViewById(R.id.layoutBubbleRow)
            val btnLikeLeft: View = view.findViewById(R.id.btnLikeChatLeft)
            val ivLikeLeft: ImageView = view.findViewById(R.id.ivLikeIconChatLeft)
            val tvLikesLeft: TextView = view.findViewById(R.id.tvLikesChatLeft)

            val btnLikeRight: View = view.findViewById(R.id.btnLikeChatRight)
            val ivLikeRight: ImageView = view.findViewById(R.id.ivLikeIconChatRight)
            val tvLikesRight: TextView = view.findViewById(R.id.tvLikesChatRight)

            val daySeparator: View = view.findViewById(R.id.daySeparator)
            val tvDayHeader: TextView = view.findViewById(R.id.tvDayHeader)
            val cardBubble: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.cardBubble)
            val layoutBubble: android.widget.LinearLayout = view.findViewById(R.id.layoutBubble)
            val tvSender: TextView = view.findViewById(R.id.tvSender)
            val tvMessage: TextView = view.findViewById(R.id.tvMessage)
            val tvTime: TextView = view.findViewById(R.id.tvTime)
            val ivImage: ImageView = view.findViewById(R.id.ivChatMessageImage)
            val mediaEmbedContainer: LinearLayout = view.findViewById(R.id.mediaEmbedContainerChat)
            val layoutReplyContext: View = view.findViewById(R.id.layoutReplyContext)
            val tvReplyContextSender: TextView = view.findViewById(R.id.tvReplyContextSender)
            val tvReplyContextContent: TextView = view.findViewById(R.id.tvReplyContextContent)
            val viewColorLineTop: View = view.findViewById(R.id.viewColorLineTop)
            val viewColorLineSide: View = view.findViewById(R.id.viewColorLineSide)
            val viewColorLineSideRight: View = view.findViewById(R.id.viewColorLineSideRight)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_bubble, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val msg = items[position]
            val prevMsg = if (position > 0) items[position - 1] else null
            
            val currentDay = getDayString(msg.timestamp)
            val prevDay = prevMsg?.let { getDayString(it.timestamp) }
            
            if (prevDay == null || currentDay != prevDay) {
                holder.daySeparator.visibility = View.VISIBLE
                holder.tvDayHeader.text = currentDay
                val textColor = ColorHelper.getTextColor(this@ChatActivity)
                holder.tvDayHeader.setTextColor(textColor)
                holder.itemView.findViewById<View>(R.id.lineLeft).setBackgroundColor(textColor and 0x33FFFFFF or 0x33000000)
                holder.itemView.findViewById<View>(R.id.lineRight).setBackgroundColor(textColor and 0x33FFFFFF or 0x33000000)
            } else {
                holder.daySeparator.visibility = View.GONE
            }

            holder.tvMessage.visibility = if (msg.content.isEmpty()) View.GONE else View.VISIBLE
            if (msg.content.isNotEmpty()) {
                val displayContent = if (msg.isEdited) "${msg.content} *(edited)*" else msg.content
                val processedContent = displayContent.replace("\n", "  \n")
                markwon.setMarkdown(holder.tvMessage, processedContent)
            }
            
            if (msg.imageUri != null) {
                holder.ivImage.visibility = View.VISIBLE
                holder.ivImage.load(msg.imageUri)
            } else {
                holder.ivImage.visibility = View.GONE
            }

            holder.tvTime.text = sdf.format(Date(msg.timestamp))

            val displayMetrics = resources.displayMetrics
            val density = displayMetrics.density
            val maxBubbleWidth = (displayMetrics.widthPixels * 0.8).toInt()
            
            holder.cardBubble.layoutParams.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            holder.tvMessage.maxWidth = maxBubbleWidth - (32 * density).toInt()
            holder.ivImage.maxWidth = maxBubbleWidth - (32 * density).toInt()

            val textColor = ColorHelper.getTextColor(this@ChatActivity)
            val frontColor = ColorHelper.getFrontColor(this@ChatActivity)
            val sender = people.find { it.id == msg.senderId }
            val senderColor = sender?.profileColor ?: Color.TRANSPARENT

            holder.cardBubble.setCardBackgroundColor(frontColor)
            holder.tvMessage.setTextColor(textColor)
            holder.tvTime.setTextColor(textColor)
            holder.tvTime.alpha = 0.7f
            holder.tvMessage.typeface = android.graphics.Typeface.DEFAULT
            
            val userLikes = msg.safeLikedByMemberIds[currentUser.id] ?: 0
            val likeColor = if (userLikes > 0) ColorHelper.getBtnColor(this@ChatActivity) else (textColor and 0x66FFFFFF.toInt())

            val lpRow = holder.layoutBubbleRow.layoutParams as android.widget.LinearLayout.LayoutParams
            val lpSender = holder.tvSender.layoutParams as android.widget.LinearLayout.LayoutParams

            if (msg.senderId == currentUser.id) {
                holder.layoutBubbleRow.gravity = android.view.Gravity.END
                lpSender.gravity = android.view.Gravity.END
                holder.tvSender.visibility = View.GONE

                holder.btnLikeLeft.visibility = if (msg.likes > 0) View.VISIBLE else View.GONE
                holder.btnLikeRight.visibility = View.GONE
                
                holder.tvLikesLeft.text = if (msg.likes > 0) msg.likes.toString() else ""
                holder.tvLikesLeft.setTextColor(likeColor)
                holder.ivLikeLeft.setColorFilter(likeColor)
                
                holder.btnLikeLeft.setOnClickListener { handleLikeClick(msg, position) }

                holder.viewColorLineTop.visibility = View.VISIBLE
                holder.viewColorLineTop.setBackgroundColor(senderColor)
                holder.viewColorLineSideRight.visibility = View.VISIBLE
                holder.viewColorLineSideRight.setBackgroundColor(senderColor)
                holder.viewColorLineSide.visibility = View.GONE
            } else {
                holder.layoutBubbleRow.gravity = android.view.Gravity.START
                lpSender.gravity = android.view.Gravity.START
                
                holder.tvSender.visibility = if (isGroup) View.VISIBLE else View.GONE
                holder.tvSender.text = sender?.sysmediaProfile?.displayName ?: sender?.name ?: "Unknown"
                holder.tvSender.setTextColor(textColor)
                holder.tvSender.alpha = 0.9f

                holder.btnLikeLeft.visibility = View.GONE
                holder.btnLikeRight.visibility = if (msg.likes > 0) View.VISIBLE else View.GONE
                
                holder.tvLikesRight.text = if (msg.likes > 0) msg.likes.toString() else " "
                holder.tvLikesRight.setTextColor(likeColor)
                holder.ivLikeRight.setColorFilter(likeColor)
                
                holder.btnLikeRight.setOnClickListener { handleLikeClick(msg, position) }

                holder.viewColorLineTop.visibility = View.VISIBLE
                holder.viewColorLineTop.setBackgroundColor(senderColor)
                holder.viewColorLineSide.visibility = View.VISIBLE
                holder.viewColorLineSide.setBackgroundColor(senderColor)
                holder.viewColorLineSideRight.visibility = View.GONE
            }
            holder.layoutBubbleRow.layoutParams = lpRow
            holder.cardBubble.radius = (16 * density)
            holder.tvSender.layoutParams = lpSender

            MediaEmbedHelper.addEmbedsToContainer(holder.mediaEmbedContainer, msg.content)
            
            if (msg.replyToId != null) {
                val allDMsJson = getSharedPreferences("my_app", MODE_PRIVATE).getString("sysmedia_dms", "[]")
                val allDMs: List<DirectMessage> = Gson().fromJson(allDMsJson, object : TypeToken<List<DirectMessage>>() {}.type) ?: emptyList()
                val original = allDMs.find { it.id == msg.replyToId }
                
                if (original != null) {
                    holder.layoutReplyContext.visibility = View.VISIBLE
                    val replySender = people.find { it.id == original.senderId }
                    holder.tvReplyContextSender.text = replySender?.sysmediaProfile?.displayName ?: replySender?.name ?: "Unknown"
                    holder.tvReplyContextContent.text = original.content
                    holder.tvReplyContextSender.maxWidth = maxBubbleWidth - (48 * density).toInt()
                    holder.tvReplyContextContent.maxWidth = maxBubbleWidth - (48 * density).toInt()
                    
                    val btnColor = ColorHelper.getBtnColor(this@ChatActivity)
                    holder.tvReplyContextSender.setTextColor(btnColor)
                    holder.tvReplyContextContent.setTextColor(textColor and 0xCCFFFFFF.toInt())
                    holder.itemView.findViewById<View>(R.id.viewReplyContextIndicator).setBackgroundColor(btnColor)
                    
                    holder.layoutReplyContext.setOnClickListener {
                        val originalPos = items.indexOfFirst { it.id == original.id }
                        if (originalPos != -1) recyclerView.smoothScrollToPosition(originalPos)
                    }
                } else {
                    holder.layoutReplyContext.visibility = View.GONE
                }
            } else {
                holder.layoutReplyContext.visibility = View.GONE
            }

            val longClickListener = View.OnLongClickListener { holder.itemView.performLongClick() }
            holder.tvMessage.setOnLongClickListener(longClickListener)
            holder.cardBubble.setOnLongClickListener(longClickListener)

            holder.itemView.setOnLongClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val m = items[pos]
                    val options = mutableListOf<String>()
                    options.add("Like")
                    options.add(getString(R.string.reply))
                    
                    if (isGroup) {
                        options.add(getString(R.string.action_switch_account))
                    }

                    if (m.senderId == currentUser.id) {
                        options.add(getString(R.string.edit))
                    }
                    options.add(getString(R.string.delete))
                    
                    androidx.appcompat.app.AlertDialog.Builder(this@ChatActivity)
                        .setItems(options.toTypedArray()) { _, which ->
                            val selectedOption = options[which]
                            when (selectedOption) {
                                "Like" -> handleLikeClick(m, pos)
                                getString(R.string.reply) -> startReply(m)
                                getString(R.string.reply) -> startReply(m)
                                getString(R.string.action_switch_account) -> showSwitchSenderDialog(m, pos)
                                getString(R.string.edit) -> showEditMessageDialog(m, pos)
                                getString(R.string.delete) -> {
                                    val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                                    val msgJson = sharedPref.getString("sysmedia_dms", "[]")
                                    val typeMsg = object : TypeToken<MutableList<DirectMessage>>() {}.type
                                    val allMessages: MutableList<DirectMessage> = Gson().fromJson(msgJson, typeMsg) ?: mutableListOf()
                                    
                                    allMessages.removeAll { it.id == m.id }
                                    sharedPref.edit { putString("sysmedia_dms", Gson().toJson(allMessages)) }
                                    
                                    messages.removeAt(pos)
                                    notifyItemRemoved(pos)
                                }
                            }
                        }.show()
                }
                true
            }
        }

        override fun getItemCount() = items.size
    }
}
