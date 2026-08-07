package com.interli.plural.features.sysmedia

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.ChatGroup
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.core.DialogHelper
import com.interli.plural.DirectMessage
import com.interli.plural.features.diary.ChatActivity
import com.interli.plural.features.diary.CreatePostActivity
import com.interli.plural.core.MediaEmbedHelper
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.Group
import com.interli.plural.Person
import com.interli.plural.R
import com.interli.plural.SysmediaNotification
import com.interli.plural.SysmediaPost
import com.interli.plural.SysmediaProfile
import java.text.SimpleDateFormat
import java.util.*

class SysmediaActivity : BaseActivity() {
    private lateinit var posts: MutableList<SysmediaPost>
    private lateinit var people: MutableList<Person>
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SysmediaAdapter
    private var currentTab = 0
    private var activeMemberId: String? = null
    private var editingPerson: Person? = null
    private var searchQuery: String = ""
    private lateinit var markwon: io.noties.markwon.Markwon
    private val searchWatcher = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            searchQuery = s.toString()
            performSearch()
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    }
    private val pickImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val file = java.io.File(filesDir, "profile_${System.currentTimeMillis()}.jpg")
                val outputStream = java.io.FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                editingPerson?.sysmediaProfile?.profilePictureUri = android.net.Uri.fromFile(file).toString()
                saveData()
                updateActiveMemberHeader()
                adapter.notifyDataSetChanged()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    private val switchAccountLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedId = result.data?.getStringExtra("selected_id")
            loadData()
            if (selectedId != null) {
                activeMemberId = selectedId
            }
            updateActiveMemberHeader()
            filterTab()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sysmedia)
        ColorHelper.applySettings(this)
        loadData()
        markwon = io.noties.markwon.Markwon.builder(this)
            .usePlugin(io.noties.markwon.linkify.LinkifyPlugin.create())
            .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
            .usePlugin(io.noties.markwon.image.coil.CoilImagesPlugin.create(this))
            .build()
        if (activeMemberId == null) {
            val nonArchived = people.filter { !it.isArchived }
            activeMemberId = nonArchived.find { it.isFront }?.id ?: nonArchived.firstOrNull()?.id
        }

        recyclerView = findViewById(R.id.sysmediaRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SysmediaAdapter(mutableListOf())
        recyclerView.adapter = adapter
        findViewById<FloatingActionButton>(R.id.fabAddPost).setOnClickListener {
            val intent = android.content.Intent(this, CreatePostActivity::class.java)
            intent.putExtra("current_user_id", activeMemberId)
            startActivity(intent)
        }
        findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayoutSysmedia).addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                markTabAsRead(currentTab)
                filterTab()
                updateTabBadges()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                markTabAsRead(tab?.position ?: 0)
                updateTabBadges()
            }
        })
        findViewById<View>(R.id.headerActiveMember).setOnClickListener {
            val member = people.find { it.id == activeMemberId }
            if (member != null) showProfileViewDialog(member)
        }
        setupNavigationDrawer()
        ColorHelper.applySettings(this)
        val textColor = ColorHelper.getTextColor(this)
        val btnColor = ColorHelper.getBtnColor(this)
        findViewById<View>(R.id.sysmediaRoot).setBackgroundColor(ColorHelper.getBgColor(this))
        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayoutSysmedia)
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar.setTitleTextColor(textColor)
        toolbar.setNavigationIconTint(textColor)
        toolbar.inflateMenu(R.menu.menu_sysmedia)
        for (i in 0 until toolbar.menu.size()) {
            toolbar.menu.getItem(i).icon?.setTint(textColor)
        }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sysmedia_new_account -> {
                    showCreateSysmediaAccountDialog()
                    true
                }
                R.id.action_sysmedia_view_profile -> {
                    activeMemberId?.let { openProfile(it) } ?: run {
                        Toast.makeText(this, getString(R.string.no_active_member), Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_sysmedia_member_list -> {
                    val intent = android.content.Intent(this, SysmediaMemberListActivity::class.java)
                    intent.putExtra("active_member_id", activeMemberId)
                    startActivity(intent)
                    true
                }
                R.id.action_sysmedia_scheduled_posts -> {
                    val intent = android.content.Intent(this, SysmediaScheduledPostsActivity::class.java)
                    intent.putExtra("active_member_id", activeMemberId)
                    startActivity(intent)
                    true
                }
                R.id.action_sysmedia_recover_orphaned -> {
                    val intent = android.content.Intent(this, SysmediaGhostActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.action_sysmedia_delete_accounts -> {
                    showDeleteAccountsDialog()
                    true
                }
                R.id.action_sysmedia_switch -> {
                    showSwitchUserFullDialog()
                    true
                }
                else -> false
            }
        }
        updateActiveMemberHeader()
        markTabAsRead(currentTab)
        filterTab()
        updateTabBadges()
    }
    private fun showDeleteAccountsDialog() {
        val sysmediaPeople = people.filter { !it.isArchived && (it.isSysmediaOnly || it.sysmediaProfile?.handle != null || it.sysmediaProfile?.profilePictureUri != null) }
        val names = sysmediaPeople.map { "${it.name}${if (it.sysmediaProfile?.handle != null) " (@${it.sysmediaProfile?.handle})" else ""}" }.toTypedArray()
        if (names.isEmpty()) {
            Toast.makeText(this, "No Sysmedia accounts found to delete.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.action_delete_sysmedia_accounts)
            .setItems(names) { _, which ->
                val person = sysmediaPeople[which]
                var pressCount = 0
                val dialog = AlertDialog.Builder(this)
                    .setTitle("Delete ${person.name}?")
                    .setMessage(getString(R.string.final_confirm_delete_account))
                    .setPositiveButton(getString(R.string.tap_to_confirm, 8)) { _, _ -> }
                    .setNegativeButton(R.string.cancel, null)
                    .create()
                dialog.show()
                val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                btn.setOnClickListener {
                    pressCount++
                    if (pressCount >= 8) {
                        people.remove(person)
                        posts.removeAll { it.senderId == person.id }
                        saveData()
                        updateActiveMemberHeader()
                        filterTab()
                        dialog.dismiss()
                    } else {
                        btn.text = getString(R.string.tap_to_confirm, 8 - pressCount)
                    }
                }
                ColorHelper.styleAlertDialog(dialog, this)
            }
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun updateTabBadges() {
        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayoutSysmedia)
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastTimeline = sharedPref.getLong("last_viewed_sysmedia_timeline", 0L)
        val activeMember = people.find { it.id == activeMemberId }
        val followingIds = activeMember?.sysmediaProfile?.followingIds ?: mutableListOf()
        val unreadTimeline = posts.count {
            it.timestamp > lastTimeline &&
                    (it.senderId == activeMemberId || followingIds.contains(it.senderId)) &&
                    (it.scheduledTime == null || it.scheduledTime!! <= now)
        }
        setTabBadge(tabLayout, 0, unreadTimeline)
        setTabBadge(tabLayout, 1, 0)
        val notifJson = sharedPref.getString("sysmedia_notifications", "[]")
        val notifications: List<SysmediaNotification> = Gson().fromJson(notifJson, object : TypeToken<List<SysmediaNotification>>() {}.type) ?: emptyList()
        val unreadNotifs = notifications.count { it.receiverId == activeMemberId && !it.isRead }
        setTabBadge(tabLayout, 2, unreadNotifs)
        val msgJson = sharedPref.getString("sysmedia_dms", "[]")
        val allMessages: List<DirectMessage> = Gson().fromJson(msgJson, object : TypeToken<List<DirectMessage>>() {}.type) ?: emptyList()
        val groupJson = sharedPref.getString("sysmedia_chat_groups", "[]")
        val chatGroups: List<ChatGroup> = Gson().fromJson(groupJson, object : TypeToken<List<ChatGroup>>() {}.type) ?: emptyList()
        val myGroupIds = chatGroups.filter { it.participantIds.contains(activeMemberId) }.map { it.id }
        val currentId = activeMemberId ?: ""
        val unreadDMs = allMessages.count { msg ->
            (msg.chatId.contains(currentId) || myGroupIds.contains(msg.chatId)) &&
                    msg.senderId != currentId &&
                    currentId.isNotEmpty() &&
                    !msg.isRead
        }
        setTabBadge(tabLayout, 4, unreadDMs)
    }
    private fun setTabBadge(tabLayout: com.google.android.material.tabs.TabLayout, index: Int, count: Int) {
        val tab = tabLayout.getTabAt(index) ?: return
        if (count > 0) {
            tab.getOrCreateBadge().apply {
                number = count
                backgroundColor = android.graphics.Color.RED
                badgeTextColor = android.graphics.Color.WHITE
            }
        } else {
            tab.removeBadge()
        }
    }
    private fun markTabAsRead(index: Int) {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        when (index) {
            0 -> sharedPref.edit { putLong("last_viewed_sysmedia_timeline", now) }
            1 -> sharedPref.edit { putLong("last_viewed_sysmedia_all", now) }
            2 -> {
                val notifJson = sharedPref.getString("sysmedia_notifications", "[]")
                val notifications: MutableList<SysmediaNotification> = Gson().fromJson(notifJson, object : TypeToken<MutableList<SysmediaNotification>>() {}.type) ?: mutableListOf()
                notifications.filter { it.receiverId == activeMemberId && !it.isRead }.forEach { it.isRead = true }
                sharedPref.edit { putString("sysmedia_notifications", Gson().toJson(notifications)) }
            }
        }
    }
    private fun showProfileViewDialog(person: Person) {
        openProfile(person.id)
    }
    private fun updateActiveMemberHeader() {
        val ivActiveAvatar = findViewById<ImageView>(R.id.ivActiveAvatar)
        val tvActiveName = findViewById<TextView>(R.id.tvActiveName)
        val person = people.find { it.id == activeMemberId }
        if (person != null) {
            val profile = person.sysmediaProfile
            tvActiveName.text = profile?.displayName ?: person.name
            tvActiveName.setTextColor(ColorHelper.getTextColor(this))
            val avatarUri = profile?.profilePictureUri ?: person.profilePictureUri
            val userColor = ColorHelper.getUserColor(person.id, person.profileColor)
            val colorDrawable = android.graphics.drawable.ColorDrawable(userColor)
            if (avatarUri != null && avatarUri.isNotEmpty()) {
                ivActiveAvatar.load(avatarUri) {
                    placeholder(colorDrawable)
                    error(colorDrawable)
                }
            } else {
                ivActiveAvatar.setImageDrawable(colorDrawable)
            }
        } else {
            tvActiveName.text = getString(R.string.nobody_fronting)
            tvActiveName.setTextColor(ColorHelper.getTextColor(this))
            ivActiveAvatar.setImageDrawable(android.graphics.drawable.ColorDrawable(ColorHelper.getBtnColor(this)))
        }
    }
    private fun filterTab() {
        val etSearch = findViewById<EditText>(R.id.etSysmediaSearch)
        etSearch.visibility = if (currentTab == 3) View.VISIBLE else View.GONE
        when (currentTab) {
            0, 1 -> {
                recyclerView.adapter = adapter
                filterPosts()
            }
            2 -> {
                val notifJson = getSharedPreferences("my_app", MODE_PRIVATE).getString("sysmedia_notifications", "[]")
                val notifications: List<SysmediaNotification> = Gson().fromJson(notifJson, object : TypeToken<List<SysmediaNotification>>() {}.type) ?: emptyList()
                val myNotifs = notifications.filter { it.receiverId == activeMemberId && it.type != "NEW_POST" }
                recyclerView.adapter = NotificationAdapter(myNotifs)
            }
            3 -> {
                recyclerView.adapter = adapter
                etSearch.removeTextChangedListener(searchWatcher)
                etSearch.addTextChangedListener(searchWatcher)
                performSearch()
            }
            4 -> {
                val dmItems = mutableListOf<Any>()
                dmItems.add("ACTION_BUTTONS")
                val msgJson = getSharedPreferences("my_app", MODE_PRIVATE).getString("sysmedia_dms", "[]")
                val allMessages: List<DirectMessage> = Gson().fromJson(msgJson, object : TypeToken<List<DirectMessage>>() {}.type) ?: emptyList()
                val groupJson = getSharedPreferences("my_app", MODE_PRIVATE).getString("sysmedia_chat_groups", "[]")
                val chatGroups: List<ChatGroup> = Gson().fromJson(groupJson, object : TypeToken<List<ChatGroup>>() {}.type) ?: emptyList()
                val myGroups = chatGroups.filter { group ->
                    group.participantIds.contains(activeMemberId)
                }
                val currentId = activeMemberId
                val activePeople = people.filter { person ->
                    if (currentId == null || person.id == currentId || person.isArchived) return@filter false
                    val chatId = listOf(currentId, person.id).sorted().let { "${it[0]}_${it[1]}" }
                    allMessages.any { it.chatId == chatId }
                }
                dmItems.addAll(myGroups)
                dmItems.addAll(activePeople)
                recyclerView.adapter = DMAdapter(dmItems)
            }
        }
    }
    private fun filterPosts() {
        val filterMemberId = intent.getStringExtra("filter_member_id")
        val now = System.currentTimeMillis()
        val filtered = when (currentTab) {
            0 -> {
                val activeMember = people.find { it.id == activeMemberId }
                val followingIds = activeMember?.sysmediaProfile?.followingIds ?: mutableListOf()
                posts.filter { (it.senderId == activeMemberId || followingIds.contains(it.senderId)) && (it.scheduledTime == null || it.scheduledTime!! <= now) }
            }
            1 -> {
                if (filterMemberId != null) {
                    posts.filter { it.senderId == filterMemberId && (it.scheduledTime == null || it.scheduledTime!! <= now) }
                } else {
                    posts.filter { it.scheduledTime == null || it.scheduledTime!! <= now }
                }
            }
            else -> emptyList()
        }
        adapter.updateItems(filtered)
    }
    private fun performSearch() {
        if (searchQuery.isBlank()) {
            adapter.updateItems(emptyList())
            return
        }
        val query = searchQuery.lowercase()
        val now = System.currentTimeMillis()
        val filtered = posts.filter {
            (it.content.lowercase().contains(query) ||
                    (query.startsWith("@") && people.any { p -> p.id == it.senderId && (p.name.lowercase().contains(query.drop(1)) || p.sysmediaProfile?.handle?.lowercase()?.contains(query.drop(1)) == true) }) ||
                    (query.startsWith("#") && it.content.lowercase().contains(query))) &&
                    (it.scheduledTime == null || it.scheduledTime!! <= now)
        }
        adapter.updateItems(filtered)
    }
    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val postsJson = sharedPref.getString("sysmedia_posts", "[]")
        posts = Gson().fromJson(postsJson, object : TypeToken<MutableList<SysmediaPost>>() {}.type) ?: mutableListOf()
        posts.forEach {
            @Suppress("SENSELESS_COMPARISON")
            if (it.likedByMemberIds == null) it.likedByMemberIds = mutableMapOf()
        }
        posts.sortByDescending { it.timestamp }
        people = MemberHelper.loadAllPeople(this)
    }
    private fun saveData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        sharedPref.edit {
            putString("sysmedia_posts", Gson().toJson(posts))
        }
        MemberHelper.savePeople(this, people)
    }
    private fun openPostDetail(postId: String) {
        val intent = android.content.Intent(this, SysmediaPostDetailActivity::class.java)
        intent.putExtra("post_id", postId)
        intent.putExtra("active_member_id", activeMemberId)
        startActivity(intent)
    }
    private fun openProfile(userId: String) {
        val intent = android.content.Intent(this, SysmediaProfileActivity::class.java)
        intent.putExtra("profile_user_id", userId)
        intent.putExtra("active_member_id", activeMemberId)
        startActivity(intent)
    }
    private fun showCreateSysmediaAccountDialog() {
        val input = EditText(this).apply { hint = "Account Name" }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_create_sysmedia_account))
            .setView(input)
            .setPositiveButton(getString(R.string.action_add)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newPerson = Person(name = name, isSysmediaOnly = true)
                    newPerson.sysmediaProfile = SysmediaProfile()
                    people.add(newPerson)
                    saveData()
                    updateActiveMemberHeader()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    private fun showSwitchUserFullDialog() {
        val intent = android.content.Intent(this, SysmediaSwitchAccountActivity::class.java)
        switchAccountLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        val recyclerViewState = recyclerView.layoutManager?.onSaveInstanceState()
        loadData()
        filterTab()
        updateActiveMemberHeader()
        updateTabBadges()
        recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
    }
    private fun handleLike(post: SysmediaPost, onUpdate: () -> Unit) {
        val memberId = activeMemberId ?: return
        val currentLikes = post.likedByMemberIds[memberId] ?: 0
        if (currentLikes < 3) {
            post.likedByMemberIds[memberId] = currentLikes + 1
            post.likes++
            if (currentLikes == 0 && post.senderId != memberId) {
                val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                val notifJson = sharedPref.getString("sysmedia_notifications", "[]")
                val notifications: MutableList<SysmediaNotification> = Gson().fromJson(notifJson, object : TypeToken<MutableList<SysmediaNotification>>() {}.type) ?: mutableListOf()
                notifications.add(0, SysmediaNotification(receiverId = post.senderId, senderId = memberId, type = "LIKE", postId = post.id))
                sharedPref.edit().putString("sysmedia_notifications", Gson().toJson(notifications)).apply()
            }
        } else {
            post.likes -= 3
            post.likedByMemberIds[memberId] = 0
        }
        saveData()
        onUpdate()
    }
    private inner class SysmediaAdapter(private var items: List<SysmediaPost>) :
        RecyclerView.Adapter<SysmediaAdapter.ViewHolder>() {
        private val sdf = SimpleDateFormat("HH:mm · dd MMM yy", Locale.getDefault())
        fun updateItems(newItems: List<SysmediaPost>) {
            items = newItems
            notifyDataSetChanged()
        }
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val layoutThreadParent: View = view.findViewById(R.id.layoutThreadParent)
            val ivParentAvatar: ImageView = view.findViewById(R.id.ivParentAvatar)
            val tvParentName: TextView = view.findViewById(R.id.tvParentName)
            val tvParentContent: TextView = view.findViewById(R.id.tvParentContent)
            val layoutReblog: View = view.findViewById(R.id.layoutReblogHeader)
            val tvRebloggedBy: TextView = view.findViewById(R.id.tvRebloggedBy)
            val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvHandle: TextView = view.findViewById(R.id.tvHandle)
            val tvTime: TextView = view.findViewById(R.id.tvTimeBottom)
            val tvContent: TextView = view.findViewById(R.id.tvContent)
            val tvLikes: TextView = view.findViewById(R.id.tvLikes)
            val tvRetweets: TextView = view.findViewById(R.id.tvRetweets)
            val tvReplies: TextView = view.findViewById(R.id.tvReplies)
            val btnReblog: View = view.findViewById(R.id.btnReblog)
            val btnLike: View = view.findViewById(R.id.btnLike)
            val btnReply: View = view.findViewById(R.id.btnReply)
            val btnShare: View = view.findViewById(R.id.btnShare)
            val ivPostImage: ImageView = view.findViewById(R.id.ivPostImagePreview)
            val mediaEmbedContainer: LinearLayout = view.findViewById(R.id.mediaEmbedContainer)
            val cardOriginal: MaterialCardView = view.findViewById(R.id.cardOriginalPost)
            val ivOriginalAvatar: ImageView = view.findViewById(R.id.ivOriginalAvatar)
            val tvOriginalName: TextView = view.findViewById(R.id.tvOriginalName)
            val tvOriginalHandle: TextView = view.findViewById(R.id.tvOriginalHandle)
            val tvOriginalContent: TextView = view.findViewById(R.id.tvOriginalContent)
            val ivOriginalPostImage: ImageView = view.findViewById(R.id.ivOriginalPostImage)
            val layoutPoll: LinearLayout = view.findViewById(R.id.layoutPoll)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sysmedia_post, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.contains("LIKE_UPDATE")) {
                updateLikeUi(holder, items[position])
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val post = items[position]
            val sender = people.find { it.id == post.senderId }
            val textColor = ColorHelper.getTextColor(this@SysmediaActivity)
            holder.layoutReblog.visibility = View.GONE
            holder.cardOriginal.visibility = View.GONE
            holder.layoutThreadParent.visibility = View.GONE
            if (post.replyToId != null) {
                val parentPost = posts.find { it.id == post.replyToId }
                if (parentPost != null) {
                    holder.layoutThreadParent.visibility = View.VISIBLE
                    val parentSender = people.find { it.id == parentPost.senderId }
                    val parentProfile = parentSender?.sysmediaProfile
                    holder.tvParentName.text = parentProfile?.displayName ?: parentSender?.name ?: "Unknown"
                    holder.tvParentContent.text = parentPost.content
                    val parentAvatar = parentProfile?.profilePictureUri ?: parentSender?.profilePictureUri
                    if (parentAvatar != null) {
                        holder.ivParentAvatar.load(parentAvatar) {
                            placeholder(android.graphics.drawable.ColorDrawable(ColorHelper.getUserColor(parentSender?.id, parentSender?.profileColor ?: -6934396)))
                            error(android.graphics.drawable.ColorDrawable(ColorHelper.getUserColor(parentSender?.id, parentSender?.profileColor ?: -6934396)))
                        }
                    } else {
                        val color = ColorHelper.getUserColor(parentSender?.id, parentSender?.profileColor ?: -6934396)
                        holder.ivParentAvatar.setImageDrawable(android.graphics.drawable.ColorDrawable(color))
                    }
                    holder.tvParentName.setTextColor(textColor and 0xCCFFFFFF.toInt())
                    holder.tvParentContent.setTextColor(textColor and 0xCCFFFFFF.toInt())
                }
            } else if (post.reblogOfId != null) {
                val originalPost = posts.find { it.id == post.reblogOfId }
                holder.layoutReblog.visibility = View.VISIBLE
                holder.tvRebloggedBy.text = getString(R.string.reblogged_by, sender?.name ?: "Unknown")
                holder.tvRebloggedBy.setTextColor(textColor and 0x88FFFFFF.toInt())
                if (originalPost != null) {
                    holder.cardOriginal.visibility = View.VISIBLE
                    holder.cardOriginal.setCardBackgroundColor(ColorHelper.getBgColor(this@SysmediaActivity))
                    val parentSender = people.find { it.id == originalPost.senderId }
                    val parentProfile = parentSender?.sysmediaProfile
                    holder.tvOriginalName.text = parentProfile?.displayName ?: parentSender?.name ?: "Unknown"
                    val parentHandle = parentProfile?.handle ?: parentSender?.name?.replace(" ", "_")?.lowercase() ?: parentSender?.manualId ?: "unknown"
                    holder.tvOriginalHandle.text = "@$parentHandle"
                    markwon.setMarkdown(holder.tvOriginalContent, originalPost.content)
                    val parentAvatar = parentProfile?.profilePictureUri ?: parentSender?.profilePictureUri
                    if (parentAvatar != null) {
                        holder.ivOriginalAvatar.load(parentAvatar) {
                            placeholder(android.graphics.drawable.ColorDrawable(ColorHelper.getUserColor(parentSender?.id, parentSender?.profileColor ?: -6934396)))
                            error(android.graphics.drawable.ColorDrawable(ColorHelper.getUserColor(parentSender?.id, parentSender?.profileColor ?: -6934396)))
                        }
                    } else {
                        val color = ColorHelper.getUserColor(parentSender?.id, parentSender?.profileColor ?: -6934396)
                        holder.ivOriginalAvatar.setImageDrawable(android.graphics.drawable.ColorDrawable(color))
                    }
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
            renderPoll(holder, post)
            bindPostData(holder, post, sender)
            holder.btnReblog.setOnClickListener { showReblogConfirmDialog(post) }
            holder.btnLike.setOnClickListener { handleLike(post) { notifyItemChanged(position, "LIKE_UPDATE") } }
            holder.btnReply.setOnClickListener {
                val intent = android.content.Intent(this@SysmediaActivity, CreatePostActivity::class.java)
                intent.putExtra("current_user_id", activeMemberId)
                intent.putExtra("reply_to_id", post.id)
                startActivity(intent)
            }
            holder.btnShare.setOnClickListener {
                downloadPostAsImage(holder.itemView, post)
            }
            holder.itemView.setOnClickListener {
                openPostDetail(post.id)
            }
            holder.itemView.setOnLongClickListener {
                showPostOptions(post, position)
                true
            }
        }
        fun updateLikeUi(holder: ViewHolder, post: SysmediaPost) {
            val textColor = ColorHelper.getTextColor(this@SysmediaActivity)
            val activeLikes = activeMemberId?.let { post.likedByMemberIds[it] } ?: 0
            val btnColor = ColorHelper.getBtnColor(this@SysmediaActivity)
            val secondaryColor = (textColor and 0x00FFFFFF) or 0x88000000.toInt()
            val likeIconColor = if (activeLikes > 0) btnColor else secondaryColor
            holder.tvLikes.text = post.likes.toString()
            holder.tvLikes.setTextColor(likeIconColor)
            holder.itemView.findViewById<ImageView>(R.id.ivLikeIcon)?.setColorFilter(likeIconColor)
        }
        fun bindPostData(holder: ViewHolder, post: SysmediaPost, sender: Person?) {
            val profile = sender?.sysmediaProfile
            holder.tvName.text = profile?.displayName ?: sender?.name ?: "Unknown"
            val handle = profile?.handle ?: sender?.name?.replace(" ", "_")?.lowercase()?.replace(Regex("[^a-z0-9_]"), "") ?: sender?.manualId ?: "unknown"
            holder.tvHandle.text = "@$handle"
            holder.tvTime.text = sdf.format(Date(post.timestamp))
            val content = post.content ?: ""
            markwon.setMarkdown(holder.tvContent, content)
            val text = holder.tvContent.text
            val spannable = if (text is android.text.Spannable) text else android.text.SpannableStringBuilder(text)
            val matcher = java.util.regex.Pattern.compile("#([A-Za-z0-9_]+)").matcher(spannable)
            while (matcher.find()) {
                val tag = matcher.group(0) ?: ""
                val start = matcher.start()
                val end = matcher.end()
                spannable.setSpan(object : android.text.style.ClickableSpan() {
                    override fun onClick(view: View) {
                        val activity = this@SysmediaActivity
                        val tabLayout = activity.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayoutSysmedia)
                        val etSearch = activity.findViewById<EditText>(R.id.etSysmediaSearch)
                        searchQuery = tag
                        etSearch?.setText(tag)
                        if (currentTab != 3) {
                            tabLayout?.getTabAt(3)?.select()
                        } else {
                            performSearch()
                        }
                    }
                    override fun updateDrawState(ds: android.text.TextPaint) {
                        super.updateDrawState(ds)
                        ds.isUnderlineText = false
                        ds.color = ColorHelper.getBtnColor(this@SysmediaActivity)
                        ds.isFakeBoldText = true
                    }
                }, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val handleMatcher = java.util.regex.Pattern.compile("@([A-Za-z0-9_]+)").matcher(spannable)
            while (handleMatcher.find()) {
                val fullTag = handleMatcher.group(0) ?: ""
                val handleTag = handleMatcher.group(1)?.lowercase() ?: ""
                val start = handleMatcher.start()
                val end = handleMatcher.end()
                spannable.setSpan(object : android.text.style.ClickableSpan() {
                    override fun onClick(view: View) {
                        val target = people.find {
                            it.sysmediaProfile?.handle?.lowercase() == handleTag ||
                                    it.name.replace(" ", "_").lowercase() == handleTag
                        }
                        if (target != null) {
                            openProfile(target.id)
                        } else {
                            val activity = this@SysmediaActivity
                            val tabLayout = activity.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayoutSysmedia)
                            val etSearch = activity.findViewById<EditText>(R.id.etSysmediaSearch)
                            searchQuery = fullTag
                            etSearch?.setText(fullTag)
                            if (currentTab != 3) {
                                tabLayout?.getTabAt(3)?.select()
                            } else {
                                performSearch()
                            }
                        }
                    }
                    override fun updateDrawState(ds: android.text.TextPaint) {
                        super.updateDrawState(ds)
                        ds.isUnderlineText = false
                        ds.color = ColorHelper.getBtnColor(this@SysmediaActivity)
                        ds.isFakeBoldText = true
                    }
                }, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (text !is android.text.Spannable) holder.tvContent.text = spannable
            holder.tvContent.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            val avatarUri = profile?.profilePictureUri ?: sender?.profilePictureUri
            val userColor = ColorHelper.getUserColor(sender?.id, sender?.profileColor ?: -6934396)
            val colorDrawable = android.graphics.drawable.ColorDrawable(userColor)
            if (avatarUri != null && avatarUri.isNotEmpty()) {
                holder.ivAvatar.load(avatarUri) {
                    placeholder(colorDrawable)
                    error(colorDrawable)
                }
            } else {
                holder.ivAvatar.setImageDrawable(colorDrawable)
            }
            holder.ivAvatar.setOnClickListener { sender?.id?.let { openProfile(it) } }
            holder.tvName.setOnClickListener { sender?.id?.let { openProfile(it) } }
            if (post.imageUri != null && post.imageUri!!.isNotEmpty())
            {
                holder.ivPostImage.visibility = View.VISIBLE
                holder.ivPostImage.load(post.imageUri)
            } else {
                holder.ivPostImage.visibility = View.GONE
            }
            updateLikeUi(holder, post)
            holder.tvRetweets.text = post.retweets.toString()
            holder.tvReplies.text = post.replies.toString()
            val textColor = ColorHelper.getTextColor(this@SysmediaActivity)
            holder.tvName.setTextColor(textColor)
            holder.tvContent.setTextColor(textColor)
            holder.itemView.findViewById<TextView>(R.id.tvDot).setTextColor(textColor and 0x88FFFFFF.toInt())
            val secondaryColor = (textColor and 0x00FFFFFF) or 0x88000000.toInt()
            holder.tvHandle.setTextColor(secondaryColor)
            holder.tvTime.setTextColor(secondaryColor)
            holder.tvRetweets.setTextColor(secondaryColor)
            holder.tvReplies.setTextColor(secondaryColor)
            MediaEmbedHelper.addEmbedsToContainer(holder.mediaEmbedContainer, content)
        }
        override fun getItemCount() = items.size
        fun showReblogConfirmDialog(post: SysmediaPost) {
            val intent = android.content.Intent(this@SysmediaActivity, CreatePostActivity::class.java)
            intent.putExtra("current_user_id", activeMemberId)
            intent.putExtra("reblog_of_id", post.id)
            startActivity(intent)
        }
        fun showPostOptions(post: SysmediaPost, position: Int) {
            val isOwnPost = post.senderId == activeMemberId
            val options = if (isOwnPost) {
                arrayOf(getString(R.string.action_edit_post), getString(R.string.delete), if (post.scheduledTime != null) "Delete Scheduled" else null).filterNotNull().toTypedArray()
            } else {
                val activeMember = people.find { it.id == activeMemberId }
                val isFollowing = activeMember?.sysmediaProfile?.followingIds?.contains(post.senderId) == true
                val followText = if (isFollowing) getString(R.string.action_unfollow) else getString(R.string.action_follow)
                arrayOf(followText, getString(R.string.delete))
            }
            AlertDialog.Builder(this@SysmediaActivity)
                .setItems(options) { _, which ->
                    val selected = options[which]
                    when (selected) {
                        getString(R.string.action_edit_post) -> {
                            val intent = android.content.Intent(this@SysmediaActivity, CreatePostActivity::class.java)
                            intent.putExtra("current_user_id", post.senderId)
                            intent.putExtra("edit_post_id", post.id)
                            startActivity(intent)
                        }
                        getString(R.string.delete), "Delete Scheduled" -> {
                            if (post.replyToId != null) {
                                posts.find { it.id == post.replyToId }?.let { original ->
                                    if (original.replies > 0) original.replies--
                                }
                            }
                            if (post.reblogOfId != null) {
                                posts.find { it.id == post.reblogOfId }?.let { original ->
                                    if (original.retweets > 0) original.retweets--
                                }
                            }
                            val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                            val notifJson = sharedPref.getString("sysmedia_notifications", "[]")
                            val typeNotifs = object : TypeToken<MutableList<SysmediaNotification>>() {}.type
                            val notifications: MutableList<SysmediaNotification> = Gson().fromJson(notifJson, typeNotifs) ?: mutableListOf()
                            notifications.removeAll { it.postId == post.id }
                            sharedPref.edit { putString("sysmedia_notifications", Gson().toJson(notifications)) }
                            posts.remove(post)
                            saveData()
                            filterTab()
                        }
                        getString(R.string.action_follow), getString(R.string.action_unfollow) -> {
                            val activeMember = people.find { it.id == activeMemberId }
                            activeMember?.sysmediaProfile?.let { profile ->
                                if (profile.followingIds.contains(post.senderId)) profile.followingIds.remove(post.senderId)
                                else profile.followingIds.add(post.senderId)
                                saveData()
                                filterTab()
                            }
                        }
                    }
                }.show()
        }
    }
    private fun showNewChatDialog() {
        val otherPeople = people.filter { it.id != activeMemberId && !it.isArchived }
        val names = otherPeople.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("New Chat").setItems(names) { _, which ->
            val target = otherPeople[which]
            val intent = android.content.Intent(this, ChatActivity::class.java)
            intent.putExtra("current_id", activeMemberId)
            intent.putExtra("other_id", target.id)
            startActivity(intent)
        }.show()
    }
    private fun showCreateGroupDialog() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val groupsJson = sharedPref.getString("groups_list", "[]") ?: "[]"
        val groups: List<Group> = try {
            Gson().fromJson(groupsJson, object : TypeToken<List<Group>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        DialogHelper.showMemberSelectionDialog(
            this,
            getString(R.string.select_member),
            people,
            groups,
            mutableListOf(activeMemberId!!),
            includeArchived = true
        ) { selectedIds ->
            if (selectedIds.size > 1) {
                showGroupNameDialog(selectedIds.toMutableList())
            } else {
                Toast.makeText(this, "Select at least one other member", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun showGroupNameDialog(selectedIds: MutableList<String>) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 0)
        }
        val etName = EditText(this).apply {
            hint = "Group Name"
            setTextColor(ColorHelper.getTextColor(this@SysmediaActivity))
        }
        container.addView(etName)
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_new_group_title))
            .setView(container)
            .setPositiveButton(R.string.action_add) { _, _ ->
                val groupName = etName.text.toString().trim()
                if (groupName.isNotEmpty()) {
                    val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                    val chatGroups: MutableList<ChatGroup> = Gson().fromJson(sharedPref.getString("sysmedia_chat_groups", "[]"), object : TypeToken<MutableList<ChatGroup>>() {}.type) ?: mutableListOf()
                    chatGroups.add(ChatGroup(name = groupName, participantIds = selectedIds))
                    sharedPref.edit { putString("sysmedia_chat_groups", Gson().toJson(chatGroups)) }
                    filterTab()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }
    private inner class NotificationAdapter(private val items: List<SysmediaNotification>) :
        RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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
            val tvLikes: TextView = view.findViewById(R.id.tvLikes)
            val tvRetweets: TextView = view.findViewById(R.id.tvRetweets)
            val tvReplies: TextView = view.findViewById(R.id.tvReplies)
            val btnReblog: View = view.findViewById(R.id.btnReblog)
            val btnLike: View = view.findViewById(R.id.btnLike)
            val btnReply: View = view.findViewById(R.id.btnReply)
            val tvTime: TextView = view.findViewById(R.id.tvTimeBottom)
            val ivPostImage: ImageView = view.findViewById(R.id.ivPostImagePreview)
            val cardOriginal: MaterialCardView = view.findViewById(R.id.cardOriginalPost)
            val ivOriginalAvatar: ImageView = view.findViewById(R.id.ivOriginalAvatar)
            val tvOriginalName: TextView = view.findViewById(R.id.tvOriginalName)
            val tvOriginalHandle: TextView = view.findViewById(R.id.tvOriginalHandle)
            val tvOriginalContent: TextView = view.findViewById(R.id.tvOriginalContent)
            val ivOriginalPostImage: ImageView = view.findViewById(R.id.ivOriginalPostImage)
            val layoutPoll: LinearLayout = view.findViewById(R.id.layoutPoll)
            val btnShare: ImageView = view.findViewById(R.id.btnShare)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_sysmedia_post, parent, false))
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.contains("LIKE_UPDATE")) {
                val notif = items[position]
                val post = posts.find { it.id == notif.postId }
                if (post != null) {
                    val sysmediaHolder = adapter.ViewHolder(holder.itemView)
                    adapter.updateLikeUi(sysmediaHolder, post)
                }
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val notif = items[position]
            val sender = people.find { it.id == notif.senderId }
            val post = posts.find { it.id == notif.postId }
            val textColor = ColorHelper.getTextColor(this@SysmediaActivity)
            if (post != null) {
                holder.layoutReblog.visibility = View.VISIBLE

                val headerIcon = holder.itemView.findViewById<ImageView>(R.id.ivHeaderIcon)
                when (notif.type) {
                    "LIKE" -> {
                        headerIcon.setImageResource(android.R.drawable.btn_star_big_on)
                        headerIcon.clearColorFilter()
                    }
                    "REPLY" -> {
                        headerIcon.setImageResource(android.R.drawable.ic_menu_edit)
                        headerIcon.setColorFilter(textColor and 0x88FFFFFF.toInt())
                    }
                    "REBLOG" -> {
                        headerIcon.setImageResource(R.drawable.ic_reblog)
                        headerIcon.setColorFilter(textColor and 0x88FFFFFF.toInt())
                    }
                    else -> {
                        headerIcon.setImageResource(android.R.drawable.ic_dialog_info)
                        headerIcon.setColorFilter(textColor and 0x88FFFFFF.toInt())
                    }
                }
                val actionText = when (notif.type) {
                    "TAG" -> getString(R.string.notif_tagged_you)
                    "EVERYONE" -> getString(R.string.notif_tagged_everyone)
                    "REPLY" -> getString(R.string.notif_replied)
                    "REBLOG" -> getString(R.string.notif_reblogged)
                    "LIKE" -> getString(R.string.notif_liked)
                    "NEW_POST" -> getString(R.string.notif_new_post)
                    else -> getString(R.string.notif_interacted)
                }
                val timeStr = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(notif.timestamp))
                holder.tvRebloggedBy.text = "${sender?.name ?: "Unknown"} $actionText ($timeStr)"
                holder.tvRebloggedBy.setTextColor(textColor)
                holder.tvRebloggedBy.textStyle = android.graphics.Typeface.BOLD
                val sysmediaHolder = adapter.ViewHolder(holder.itemView)
                sysmediaHolder.cardOriginal.visibility = View.GONE
                sysmediaHolder.layoutThreadParent.visibility = View.GONE
                if (post.replyToId != null) {
                    val originalPost = posts.find { it.id == post.replyToId }
                    if (originalPost != null) {
                        sysmediaHolder.layoutThreadParent.visibility = View.VISIBLE
                        val parentSender = people.find { it.id == originalPost.senderId }
                        val parentProfile = parentSender?.sysmediaProfile
                        sysmediaHolder.tvParentName.text = parentProfile?.displayName ?: parentSender?.name ?: "Unknown"
                        sysmediaHolder.tvParentContent.text = originalPost.content
                        val parentAvatar = parentProfile?.profilePictureUri ?: parentSender?.profilePictureUri
                        val pColor = parentSender?.profileColor ?: ColorHelper.getBtnColor(this@SysmediaActivity)
                        val pDrawable = android.graphics.drawable.ColorDrawable(pColor)
                        if (parentAvatar != null && parentAvatar.isNotEmpty()) {
                            sysmediaHolder.ivParentAvatar.load(parentAvatar) {
                                placeholder(pDrawable)
                                error(pDrawable)
                            }
                        } else {
                            sysmediaHolder.ivParentAvatar.setImageDrawable(pDrawable)
                        }
                        sysmediaHolder.tvParentName.setTextColor(textColor and 0xCCFFFFFF.toInt())
                        sysmediaHolder.tvParentContent.setTextColor(textColor and 0xCCFFFFFF.toInt())
                    }
                } else if (post.reblogOfId != null) {
                    val originalPost = posts.find { it.id == post.reblogOfId }
                    if (originalPost != null) {
                        sysmediaHolder.cardOriginal.visibility = View.VISIBLE
                        sysmediaHolder.cardOriginal.setCardBackgroundColor(ColorHelper.getBgColor(this@SysmediaActivity))
                        val originalSender = people.find { it.id == originalPost.senderId }
                        val origProfile = originalSender?.sysmediaProfile
                        sysmediaHolder.tvOriginalName.text = origProfile?.displayName ?: originalSender?.name ?: "Unknown"
                        val origHandle = origProfile?.handle ?: originalSender?.name?.replace(" ", "_")?.lowercase() ?: originalSender?.manualId ?: "unknown"
                        sysmediaHolder.tvOriginalHandle.text = "@$origHandle"
                        markwon.setMarkdown(sysmediaHolder.tvOriginalContent, originalPost.content)
                        val origAvatar = origProfile?.profilePictureUri ?: originalSender?.profilePictureUri
                        val oColor = originalSender?.profileColor ?: ColorHelper.getBtnColor(this@SysmediaActivity)
                        val oDrawable = android.graphics.drawable.ColorDrawable(oColor)
                        if (origAvatar != null && origAvatar.isNotEmpty()) {
                            sysmediaHolder.ivOriginalAvatar.load(origAvatar) {
                                placeholder(oDrawable)
                                error(oDrawable)
                            }
                        } else {
                            sysmediaHolder.ivOriginalAvatar.setImageDrawable(oDrawable)
                        }
                        if (originalPost.imageUri != null) {
                            sysmediaHolder.ivOriginalPostImage.visibility = View.VISIBLE
                            sysmediaHolder.ivOriginalPostImage.load(originalPost.imageUri)
                        } else {
                            sysmediaHolder.ivOriginalPostImage.visibility = View.GONE
                        }
                        sysmediaHolder.tvOriginalName.setTextColor(textColor)
                        sysmediaHolder.tvOriginalContent.setTextColor(textColor)
                        sysmediaHolder.tvOriginalHandle.setTextColor(textColor and 0x88FFFFFF.toInt())
                    }
                }
                val author = people.find { it.id == post.senderId }
                renderPoll(sysmediaHolder, post)
                adapter.bindPostData(sysmediaHolder, post, author)
                holder.btnReblog.setOnClickListener { adapter.showReblogConfirmDialog(post) }
                holder.btnLike.setOnClickListener {
                    handleLike(post) { notifyItemChanged(position, "LIKE_UPDATE") }
                }
                holder.btnReply.setOnClickListener {
                    val intent = android.content.Intent(this@SysmediaActivity, CreatePostActivity::class.java)
                    intent.putExtra("current_user_id", activeMemberId)
                    intent.putExtra("reply_to_id", post.id)
                    startActivity(intent)
                }
                holder.btnShare.setOnClickListener {
                    downloadPostAsImage(holder.itemView, post)
                }
                holder.itemView.setOnClickListener { openPostDetail(post.id) }
                holder.itemView.setOnLongClickListener {
                    adapter.showPostOptions(post, position)
                    true
                }
            } else {
                holder.tvContent.text = "(Post deleted)"
                holder.layoutReblog.visibility = View.GONE
                holder.cardOriginal.visibility = View.GONE
            }
        }
        override fun getItemCount() = items.size
    }
    private inner class DMAdapter(private val items: List<Any>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is String -> 0
            is ChatGroup -> 1
            is Person -> 2
            else -> 0
        }
        inner class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvUnread: TextView = view.findViewById(R.id.tvHandle)
            val tvPreview: TextView = view.findViewById(R.id.tvContent)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                val layout = LinearLayout(parent.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = ViewGroup.LayoutParams(-1, -2)
                    setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                }
                val btnColor = ColorHelper.getBtnColor(this@SysmediaActivity)
                val btnTextColor = ColorHelper.getBtnTextColor(this@SysmediaActivity)
                val btnGroup = MaterialButton(parent.context).apply {
                    text = "+ New Group"
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 8.dpToPx() }
                    setBackgroundColor(btnColor)
                    setTextColor(btnTextColor)
                    cornerRadius = 12.dpToPx()
                    setOnClickListener { showCreateGroupDialog() }
                }
                val btnChat = MaterialButton(parent.context).apply {
                    text = "+ New Chat"
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 8.dpToPx() }
                    setBackgroundColor(btnColor)
                    setTextColor(btnTextColor)
                    cornerRadius = 12.dpToPx()
                    setOnClickListener { showNewChatDialog() }
                }
                layout.addView(btnGroup); layout.addView(btnChat)
                object : RecyclerView.ViewHolder(layout) {}
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sysmedia_post, parent, false)
                view.findViewById<View>(R.id.tvDot).visibility = View.GONE
                view.findViewById<View>(R.id.tvTime).visibility = View.GONE
                view.findViewById<View>(R.id.tvTimeBottom).visibility = View.GONE
                view.findViewById<View>(R.id.btnReply).parent.let { (it as View).visibility = View.GONE }
                view.findViewById<TextView>(R.id.tvContent).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                GroupViewHolder(view)
            }
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            if (holder is GroupViewHolder) {
                holder.ivAvatar.setImageDrawable(null)
                val textColor = ColorHelper.getTextColor(this@SysmediaActivity)
                holder.tvName.setTextColor(textColor)
                holder.tvPreview.setTextColor(textColor and 0x88FFFFFF.toInt())
                val allMessages: List<DirectMessage> = Gson().fromJson(getSharedPreferences("my_app", MODE_PRIVATE).getString("sysmedia_dms", "[]"), object : TypeToken<List<DirectMessage>>() {}.type) ?: emptyList()
                if (item is ChatGroup) {
                    holder.tvName.text = item.name
                    if (item.groupPictureUri != null) {
                        holder.ivAvatar.load(item.groupPictureUri)
                    } else {
                        holder.ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
                    }
                    val unread = allMessages.count { it.chatId == item.id && it.senderId != activeMemberId && !it.isRead }
                    holder.tvUnread.text = if (unread > 0) "● ($unread)" else ""
                    holder.tvUnread.setTextColor(android.graphics.Color.RED)
                    holder.tvPreview.text = (allMessages.filter { it.chatId == item.id }.maxByOrNull { it.timestamp }?.content ?: "").replace("\n", " ")
                    holder.itemView.setOnClickListener { val intent = android.content.Intent(this@SysmediaActivity, ChatActivity::class.java); intent.putExtra("current_id", activeMemberId); intent.putExtra("other_id", item.id); intent.putExtra("is_group", true); startActivity(intent) }
                } else if (item is Person) {
                    holder.tvName.text = item.sysmediaProfile?.displayName ?: item.name
                    val avatar = item.sysmediaProfile?.profilePictureUri ?: item.profilePictureUri
                    if (avatar != null) {
                        holder.ivAvatar.load(avatar) {
                            error(android.graphics.drawable.ColorDrawable(ColorHelper.getUserColor(item.id, item.profileColor)))
                        }
                    } else {
                        val color = ColorHelper.getUserColor(item.id, item.profileColor)
                        holder.ivAvatar.setImageDrawable(android.graphics.drawable.ColorDrawable(color))
                    }
                    val chatId = listOf(activeMemberId!!, item.id).sorted().let { "${it[0]}_${it[1]}" }
                    val unread = allMessages.count { it.chatId == chatId && it.senderId != activeMemberId && !it.isRead }
                    holder.tvUnread.text = if (unread > 0) "● ($unread)" else ""
                    holder.tvUnread.setTextColor(android.graphics.Color.RED)
                    holder.tvPreview.text = (allMessages.filter { it.chatId == chatId }.maxByOrNull { it.timestamp }?.content ?: "").replace("\n", " ")
                    holder.itemView.setOnClickListener { val intent = android.content.Intent(this@SysmediaActivity, ChatActivity::class.java); intent.putExtra("current_id", activeMemberId); intent.putExtra("other_id", item.id); startActivity(intent) }
                }
                holder.itemView.setOnLongClickListener {
                    AlertDialog.Builder(this@SysmediaActivity).setItems(arrayOf(getString(R.string.delete))) { _, _ ->
                        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                        val allDMs: MutableList<DirectMessage> = Gson().fromJson(sharedPref.getString("sysmedia_dms", "[]"), object : TypeToken<MutableList<DirectMessage>>() {}.type) ?: mutableListOf()
                        if (item is ChatGroup) {
                            allDMs.removeAll { it.chatId == item.id }
                            val groups: MutableList<ChatGroup> = Gson().fromJson(sharedPref.getString("sysmedia_chat_groups", "[]"), object : TypeToken<MutableList<ChatGroup>>() {}.type) ?: mutableListOf()
                            groups.removeAll { it.id == item.id }
                            sharedPref.edit { putString("sysmedia_dms", Gson().toJson(allDMs)); putString("sysmedia_chat_groups", Gson().toJson(groups)) }
                        } else if (item is Person) {
                            val chatId = listOf(activeMemberId!!, item.id).sorted().let { "${it[0]}_${it[1]}" }
                            allDMs.removeAll { it.chatId == chatId }
                            sharedPref.edit { putString("sysmedia_dms", Gson().toJson(allDMs)) }
                        }
                        filterTab()
                    }.show()
                    true
                }
            }
        }
        override fun getItemCount() = items.size
    }
    private fun renderPoll(holder: SysmediaAdapter.ViewHolder, post: SysmediaPost) {
        val poll = post.poll
        if (poll == null) {
            holder.layoutPoll.visibility = View.GONE
            return
        }
        holder.layoutPoll.visibility = View.VISIBLE
        holder.layoutPoll.removeAllViews()
        val textColor = ColorHelper.getTextColor(this)
        val btnColor = ColorHelper.getBtnColor(this)
        val activeUserId = activeMemberId ?: ""
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
                        adapter.notifyDataSetChanged()
                    }
                })
            }
            holder.layoutPoll.addView(optionLayout)
        }
        if (totalVotes > 0) {
            holder.layoutPoll.addView(TextView(this).apply {
                text = getString(R.string.poll_votes_count, totalVotes)
                textSize = 12f
                alpha = 0.6f
                setTextColor(textColor)
            })
        }
    }
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private var TextView.textStyle: Int
        get() = typeface?.style ?: android.graphics.Typeface.NORMAL
        set(value) {
            setTypeface(typeface, value)
        }

    private fun downloadPostAsImage(view: View, post: SysmediaPost) {
        val btns = listOf(R.id.btnReply, R.id.btnReblog, R.id.btnLike, R.id.btnShare)
        btns.forEach { view.findViewById<View>(it)?.visibility = View.INVISIBLE }

        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(ColorHelper.getBgColor(this))
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
}