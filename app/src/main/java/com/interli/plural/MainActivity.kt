package com.interli.plural

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.android.material.navigation.NavigationView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

data class IdentityGroup(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String,
    val itemNames: MutableList<String> = mutableListOf(),
    var isExpanded: Boolean = true,
    var manualOrder: Int = 0
)

data class MemberPreference(
    val activityName: String,
    val preferenceType: String // "LIKE", "DISLIKE", "NEUTRAL"
)

data class Person(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String = "",
    var manualId: String? = null,
    var isFront: Boolean = false,
    var profilePictureUri: String? = null,
    var profileColor: Int = -6934396,
    var profileInfo: String = "",
    var groupIds: MutableList<String>? = mutableListOf(),
    var customFields: MutableMap<String, String>? = mutableMapOf(),
    var hiddenFields: MutableList<String>? = mutableListOf(),
    var excludeFromStats: Boolean = false,
    var frontMessage: String? = null,
    var messageRead: Boolean = false,
    var sysmediaProfile: SysmediaProfile? = null,
    var isSysmediaOnly: Boolean = false,
    var isArchived: Boolean = false,
    var linkedThemeId: String? = null,
    var linkedMoodThemeId: String? = null,
    var preferences: MutableList<MemberPreference>? = mutableListOf(),
    var sourcePictureUri: String? = null
) {
    val safeGroupIds: MutableList<String> get() = groupIds ?: mutableListOf()
    val safeCustomFields: MutableMap<String, String> get() = customFields ?: mutableMapOf()
    val safeHiddenFields: MutableList<String> get() = hiddenFields ?: mutableListOf()
    val safePreferences: MutableList<MemberPreference> get() = preferences ?: mutableListOf()
}

data class Group(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String,
    var isExpanded: Boolean = true,
    var parentGroupId: String? = null,
    var color: Int = -3355444
)

data class FrontSession(
    var personName: String,
    var startTime: Long,
    var endTime: Long? = null,
    var personId: String? = null,
    var note: String? = null
)

data class AppTheme(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String,
    var bgColor: String,
    var btnColor: String,
    var btnTextColor: String,
    var frontColor: String,
    var textColor: String
)

data class CoFrontTheme(
    val id: String = java.util.UUID.randomUUID().toString(),
    val memberIds: List<String>,
    var themeId: String
)

data class MoodTheme(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String,
    var mood1: String, // Rad
    var mood2: String, // Good
    var mood3: String, // Meh
    var mood4: String, // Bad
    var mood5: String  // Awful
)

data class CoFrontMoodTheme(
    val id: String = java.util.UUID.randomUUID().toString(),
    val memberIds: List<String>,
    var moodThemeId: String
)

data class CustomField(
    var id: String? = null,
    var name: String,
    var template: String = ""
) {
    fun getUniqueId(): String {
        if (id == null) id = java.util.UUID.randomUUID().toString()
        return id!!
    }
}

data class NoteBundle(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String,
    var isExpanded: Boolean = true,
    var manualOrder: Int = 0
)

data class DiaryNote(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String,
    var content: String,
    val timestamp: Long = System.currentTimeMillis(),
    var linkedMemberIds: MutableList<String> = mutableListOf(),
    var senderId: String? = null,
    var bundleId: String? = null,
    var bundleName: String? = null,
    var linkedTodoListId: String? = null,
    var isProfileOnly: Boolean = false,
    var nextFronterRecipient: String? = null,
    var parentNoteId: String? = null
)

data class TodoTask(
    var id: String = java.util.UUID.randomUUID().toString(),
    var title: String = "",
    var status: String = "EMPTY",
    var linkedMemberIds: MutableList<String> = mutableListOf(),
    var deadline: Long? = null,
    var recurrence: String? = null,
    var recurrenceDays: List<Int>? = null,
    var indentLevel: Int = 0,
    var resetType: String = "NEXT_DAY", // "NEXT_DAY", "DELAYED"
    var resetHour: Int = 0,
    var resetMinute: Int = 0
)

data class TodoBundle(
    var id: String = java.util.UUID.randomUUID().toString(),
    var name: String = "",
    var isExpanded: Boolean = true,
    var manualOrder: Int = 0
)

data class TodoList(
    var id: String = java.util.UUID.randomUUID().toString(),
    var title: String = "",
    var tasks: MutableList<TodoTask> = mutableListOf(),
    var linkedMemberIds: MutableList<String> = mutableListOf(),
    var timestamp: Long = System.currentTimeMillis(),
    var deadline: Long? = null,
    var reminderTime: Long? = null,
    var bundleId: String? = null,
    var manualOrder: Int = 0,
    var linkedNoteId: String? = null
)

data class CalendarEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String,
    var description: String? = null,
    var startTime: Long,
    var endTime: Long,
    var linkedMemberIds: MutableList<String> = mutableListOf(),
    var color: Int? = null,
    var location: String? = null,
    var isAllDay: Boolean = false,
    var recurrence: String? = null, // "DAILY", "WEEKLY", "MONTHLY", "YEARLY"
    var recurrenceDays: List<Int>? = null,
    var linkedNoteId: String? = null,
    var linkedTodoListId: String? = null,
    var hideInOverview: Boolean = false,
    var hideInDay: Boolean = false,
    var hideInWeek: Boolean = false,
    var hideInMonth: Boolean = false,
    var hideInYear: Boolean = false,
    var excludedDates: MutableList<Long>? = null,
    var recurrenceUntil: Long? = null
)

data class SysmediaPost(
    val id: String = java.util.UUID.randomUUID().toString(),
    var senderId: String,
    var content: String,
    val timestamp: Long = System.currentTimeMillis(),
    var likes: Int = 0,
    var likedByMemberIds: MutableMap<String, Int> = mutableMapOf(),
    var retweets: Int = 0,
    var replies: Int = 0,
    var reblogOfId: String? = null,
    var replyToId: String? = null,
    var imageUri: String? = null,
    var scheduledTime: Long? = null,
    var poll: SysmediaPoll? = null
)

data class SysmediaPoll(
    var options: MutableList<String> = mutableListOf(),
    var votes: MutableMap<String, Int> = mutableMapOf()
)

data class SysmediaProfile(
    var handle: String? = null,
    var bio: String? = null,
    var displayName: String? = null,
    var profilePictureUri: String? = null,
    var followingIds: MutableList<String> = mutableListOf(),
    var sourcePictureUri: String? = null
)

data class SysmediaNotification(
    val id: String = java.util.UUID.randomUUID().toString(),
    val receiverId: String,
    val senderId: String,
    val type: String,
    val postId: String,
    val timestamp: Long = System.currentTimeMillis(),
    var isRead: Boolean = false
)

data class DirectMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    var senderId: String,
    val chatId: String,
    var content: String,
    val timestamp: Long = System.currentTimeMillis(),
    var isRead: Boolean = false,
    var imageUri: String? = null,
    var isEdited: Boolean = false,
    var replyToId: String? = null,
    var likes: Int = 0,
    var likedByMemberIds: MutableMap<String, Int>? = mutableMapOf()
) {
    val safeLikedByMemberIds: MutableMap<String, Int> get() = likedByMemberIds ?: mutableMapOf()
}

data class ChatGroup(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String,
    var participantIds: MutableList<String> = mutableListOf(),
    var groupPictureUri: String? = null
)

class MainActivity : BaseActivity() {

    private var people = java.util.concurrent.CopyOnWriteArrayList<Person>()
    private var groups = java.util.concurrent.CopyOnWriteArrayList<Group>()
    private var sessions = java.util.concurrent.CopyOnWriteArrayList<FrontSession>()
    private var currentLanguage: String? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        currentLanguage = LocaleHelper.getLocale(this)
        super.onCreate(savedInstanceState)

        val settingsPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)

        val rawStartPage = settingsPref.getString("start_page", "members") ?: "members"
        val startPage = when(rawStartPage.uppercase()) {
            "FRONT" -> "members"
            "STATS" -> "stats"
            "DIARY" -> "diary"
            "TODO" -> "todo"
            "MOOD" -> "mood"
            else -> rawStartPage.lowercase()
        }
        val ignoreRedirect = intent.getBooleanExtra("ignore_redirect", false) || intent.hasExtra("SHOW_DIALOG")

        if (savedInstanceState == null && !ignoreRedirect) {
            val redirectIntent: android.content.Intent? = when (startPage) {
                "mood" -> {
                    val master = settingsPref.getBoolean("module_mood_enabled", true)
                    val sub = settingsPref.getBoolean("sub_mood_log_enabled", true)
                    if (master && sub) android.content.Intent(this, MoodActivity::class.java) else null
                }
                "diary" -> if (settingsPref.getBoolean("module_notes_enabled", true)) android.content.Intent(this, DiaryActivity::class.java) else null
                "calendar" -> if (settingsPref.getBoolean("module_calendar_enabled", true)) android.content.Intent(this, CalendarActivity::class.java) else null
                "sysmedia" -> {
                    val master = settingsPref.getBoolean("module_fronting_enabled", true)
                    val sub = settingsPref.getBoolean("module_sysmedia_enabled", true)
                    if (master && sub) android.content.Intent(this, SysmediaActivity::class.java) else null
                }
                "todo" -> if (settingsPref.getBoolean("module_todo_enabled", true)) android.content.Intent(this, TodoActivity::class.java) else null
                "stats" -> {
                    val master = settingsPref.getBoolean("module_fronting_enabled", true)
                    val sub = settingsPref.getBoolean("sub_fronting_enabled", true)
                    if (master && sub) android.content.Intent(this, StatisticsActivity::class.java) else null
                }
                else -> null
            }
            redirectIntent?.let {
                startActivity(it)
                finish()
                return
            }
        }

        setContentView(R.layout.activity_main)

        loadData()
        healDataIntegrity()

        var migrationNeeded = false
        sessions.forEach { s ->
            if (s.personId == null) {
                val p = people.find { it.name == s.personName }
                if (p != null) {
                    s.personId = p.id
                    migrationNeeded = true
                }
            }
        }
        if (migrationNeeded) savePeople()

        if (!settingsPref.getBoolean("fix_sysmedia_members_v2", false)) {
            people.forEach {
                val profile = it.sysmediaProfile
                if (profile == null || (profile.handle == null && profile.profilePictureUri == null)) {
                    it.isSysmediaOnly = false
                }
            }
            savePeople()
            settingsPref.edit().putBoolean("fix_sysmedia_members_v2", true).apply()
        }

        val text = findViewById<TextView>(R.id.myText)
        val cardInfo = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardInfo)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)

        val adapter = PersonAdapter(
            this,
            groups,
            people,
            onFrontClicked = { person ->
                toggleFront(person)
            },
            onProfileClicked = { person, index ->
                person.messageRead = true
                savePeople()
                val intent = android.content.Intent(this, ProfileActivity::class.java)
                intent.putExtra("person_index", index)
                startActivity(intent)
            },
            onGroupToggled = { group ->
                group.isExpanded = !group.isExpanded
                savePeople()
            },
            onGroupLongClicked = { group ->
                showEditGroupDialog(group)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val etSearchMain = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSearchMain)
        etSearchMain.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        ColorHelper.applySettings(this)
        cardInfo.setCardBackgroundColor(ColorHelper.getFrontColor(this))
        cardInfo.setOnClickListener {
            showQuickUnfrontDialog()
        }

        val frontPeople = people.filter { it.isFront && !it.isArchived && !it.isSysmediaOnly }
        text.text = if (frontPeople.isEmpty()) {
            getString(R.string.nobody_fronting)
        } else {
            frontPeople.joinToString { it.name }
        }

        setupNavigationDrawer()

        if (savedInstanceState == null) {
            handleIntentDialogs(intent)
        }

        setupNotificationChannel()
        updateFrontNotification()
        setupNavigationDrawer()
        updateMenuVisibility()

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.POST_NOTIFICATIONS,
                    android.Manifest.permission.INTERNET
                )
            )
        } else {
            requestPermissionLauncher.launch(arrayOf(android.Manifest.permission.INTERNET))
        }

        BackupHelper.updateAutoBackupSchedule(this)
        migrateProfilePictures()
    }

    private fun healDataIntegrity() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        var healingNeeded = false

        sessions.forEach { session ->
            val currentPerson = people.find { it.id == session.personId }
            if (currentPerson == null) {
                val replacement = people.find {
                    it.name == session.personName || (it.manualId != null && it.manualId == session.personId)
                }
                if (replacement != null) {
                    session.personId = replacement.id
                    session.personName = replacement.name
                    healingNeeded = true
                }
            }
        }

        val moodJson = sharedPref.getString("mood_entries", "[]")
        val moodType = object : TypeToken<MutableList<MoodActivity.MoodEntry>>() {}.type
        val moodEntries: MutableList<MoodActivity.MoodEntry> = Gson().fromJson(moodJson, moodType) ?: mutableListOf()

        val updatedMoodEntries = moodEntries.map { entry ->
            var entryChanged = false
            val newMemberIds = entry.memberIds.map { mId ->
                if (people.any { it.id == mId }) {
                    mId
                } else {
                    val replacement = people.find { it.manualId == mId }
                    if (replacement != null) {
                        healingNeeded = true
                        entryChanged = true
                        replacement.id
                    } else {
                        mId
                    }
                }
            }
            if (entryChanged) entry.copy(memberIds = newMemberIds) else entry
        }

        if (healingNeeded) {
            savePeople()
            sharedPref.edit().putString("mood_entries", Gson().toJson(updatedMoodEntries)).apply()
        }
    }

    private fun migrateProfilePictures() {
        lifecycleScope.launch {
            val peopleToProcess = ArrayList(people)
            var anyChanged = false
            peopleToProcess.forEach { person ->
                val uri = person.profilePictureUri
                if (!uri.isNullOrBlank() && (uri.startsWith("http://") || uri.startsWith("https://"))) {
                    val localUri = ImageHelper.downloadAndSaveProfilePicture(this@MainActivity, uri, person.id)
                    if (localUri != null) {
                        person.profilePictureUri = localUri
                        people.find { it.id == person.id }?.profilePictureUri = localUri
                        anyChanged = true
                    }
                }
            }
            if (anyChanged) {
                savePeople()
                (findViewById<RecyclerView>(R.id.recyclerView)?.adapter as? PersonAdapter)?.updateItems()
            }
        }
    }

    private fun setupNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = android.app.NotificationManager.IMPORTANCE_LOW
            val channel = android.app.NotificationChannel("FRONT_CHANNEL_V2", name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)

            notificationManager.deleteNotificationChannel("FRONT_CHANNEL")

            notificationManager.createNotificationChannel(channel)

            val todoName = getString(R.string.todo)
            val todoChannel = android.app.NotificationChannel("TODO_CHANNEL", todoName, android.app.NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(todoChannel)

            val sysmediaName = getString(R.string.notification_channel_sysmedia_name)
            val sysmediaDesc = getString(R.string.notification_channel_sysmedia_description)
            val sysmediaChannel = android.app.NotificationChannel("SYSMEDIA_CHANNEL", sysmediaName, android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                description = sysmediaDesc
            }
            notificationManager.createNotificationChannel(sysmediaChannel)
        }
    }

    private fun updateFrontNotification() {
        val sharedPrefSettings = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val enabled = sharedPrefSettings.getBoolean("front_notif_enabled", true)

        if (!enabled) {
            NotificationManagerCompat.from(this).cancel(1)
            return
        }

        val frontPeople = people.filter { it.isFront && !it.isArchived && !it.isSysmediaOnly }
        val statusText = if (frontPeople.isEmpty()) getString(R.string.nobody_fronting_notification)
        else frontPeople.joinToString { it.name }

        val intent = android.content.Intent(this, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, "FRONT_CHANNEL_V2")
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(getString(R.string.front_status))
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .setSilent(true)

        try {
            NotificationManagerCompat.from(this).notify(1, builder.build())
        } catch (_: SecurityException) { }
    }

    private fun updateMenuVisibility() {
        val sharedPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val menu = navigationView.menu

        // Master switches
        val pluralMaster = sharedPref.getBoolean("module_fronting_enabled", true)
        val moodMaster = sharedPref.getBoolean("module_mood_enabled", true)
        val notesEnabled = sharedPref.getBoolean("module_notes_enabled", true)
        val todoEnabled = sharedPref.getBoolean("module_todo_enabled", true)
        val calendarEnabled = sharedPref.getBoolean("module_calendar_enabled", true)

        // Sub switches
        val frontSub = sharedPref.getBoolean("sub_front_page", true) && pluralMaster
        val statsSub = sharedPref.getBoolean("sub_statistics", true) && pluralMaster
        val whoAmISub = sharedPref.getBoolean("sub_who_am_i", true) && pluralMaster
        val sysmediaSub = sharedPref.getBoolean("module_sysmedia_enabled", true) && pluralMaster

        val moodLogSub = sharedPref.getBoolean("sub_mood_log_enabled", true) && moodMaster
        val moodStatsSub = sharedPref.getBoolean("sub_mood_stats_enabled", true) && moodMaster

        menu.findItem(R.id.action_front_page)?.isVisible = frontSub
        menu.findItem(R.id.action_statistics)?.isVisible = statsSub
        menu.findItem(R.id.action_who_am_i)?.isVisible = whoAmISub

        menu.findItem(R.id.action_mood_tracker)?.isVisible = moodLogSub
        menu.findItem(R.id.action_mood_stats)?.isVisible = moodStatsSub

        menu.findItem(R.id.action_diary)?.isVisible = notesEnabled
        menu.findItem(R.id.action_sysmedia)?.isVisible = sysmediaSub
        menu.findItem(R.id.action_todo)?.isVisible = todoEnabled
        menu.findItem(R.id.action_calendar)?.isVisible = calendarEnabled

        val header = navigationView.getHeaderView(0)
        header?.findViewById<View>(R.id.btnNavAddMember)?.visibility = if (frontSub) View.VISIBLE else View.GONE
        header?.findViewById<View>(R.id.btnNavAddGroup)?.visibility = if (frontSub) View.VISIBLE else View.GONE

        findViewById<View>(R.id.cardInfo)?.visibility = if (frontSub) View.VISIBLE else View.GONE
        findViewById<View>(R.id.recyclerView)?.visibility = if (frontSub) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvModuleDisabled)?.visibility = if (frontSub) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        val savedLang = LocaleHelper.getLocale(this)
        if (savedLang != currentLanguage) {
            recreate()
            return
        }

        val etSearchMain = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSearchMain)
        etSearchMain?.setText("")
        etSearchMain?.clearFocus()

        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(etSearchMain?.windowToken, 0)

        loadData()
        updateMenuVisibility()
        updateUI()
    }

    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)

        val loadedPeople = MemberHelper.loadAllPeople(this)

        val groupsJson = sharedPref.getString("groups_list", null)
        val loadedGroups = if (groupsJson != null) {
            val type = object : TypeToken<MutableList<Group>>() {}.type
            Gson().fromJson<MutableList<Group>>(groupsJson, type) ?: mutableListOf()
        } else mutableListOf()

        val sessionsJson = sharedPref.getString("sessions_list", null)
        val loadedSessions = if (sessionsJson != null) {
            val type = object : TypeToken<MutableList<FrontSession>>() {}.type
            Gson().fromJson<MutableList<FrontSession>>(sessionsJson, type) ?: mutableListOf()
        } else mutableListOf()

        people.clear(); people.addAll(loadedPeople)
        groups.clear(); groups.addAll(loadedGroups)
        sessions.clear(); sessions.addAll(loadedSessions)
    }

    private fun updateUI() {
        ColorHelper.applySettings(this)
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardInfo)
            ?.setCardBackgroundColor(ColorHelper.getFrontColor(this))

        val textColor = ColorHelper.getTextColor(this)
        val searchLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.searchLayout)
        searchLayout?.setStartIconTintList(android.content.res.ColorStateList.valueOf(textColor))
        searchLayout?.hintTextColor = android.content.res.ColorStateList.valueOf(textColor)
        searchLayout?.defaultHintTextColor = android.content.res.ColorStateList.valueOf(textColor)
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSearchMain)?.setTextColor(textColor)

        val text = findViewById<TextView>(R.id.myText)
        val frontPeople = people.filter { it.isFront && !it.isArchived && !it.isSysmediaOnly }
        text?.text = if (frontPeople.isEmpty()) {
            getString(R.string.nobody_fronting)
        } else {
            frontPeople.joinToString { it.name }
        }

        val adapter = findViewById<RecyclerView>(R.id.recyclerView).adapter as? PersonAdapter
        adapter?.updateItems()
        updateFrontNotification()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentDialogs(intent)
    }

    private fun handleIntentDialogs(intent: android.content.Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra("OPEN_SYSMEDIA", false)) {
            val sysmediaIntent = android.content.Intent(this, SysmediaActivity::class.java)
            startActivity(sysmediaIntent)
            intent.removeExtra("OPEN_SYSMEDIA")
            return
        }

        if ((intent.flags and android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            intent.removeExtra("SHOW_DIALOG")
            return
        }

        val showDialog = intent.getIntExtra("SHOW_DIALOG", 0)
        if (showDialog != 0) {
            intent.removeExtra("SHOW_DIALOG")
            findViewById<RecyclerView>(R.id.recyclerView)?.post {
                val adapter = findViewById<RecyclerView>(R.id.recyclerView).adapter as? PersonAdapter
                if (adapter != null) {
                    when (showDialog) {
                        R.id.action_add_person -> showAddPersonDialog(adapter)
                        R.id.action_add_group -> showAddGroupDialog(adapter)
                    }
                }
            }
        }
    }

    private fun showAddPersonDialog(adapter: PersonAdapter) {
        val input = EditText(this)
        input.hint = getString(R.string.hint_enter_name)
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val margin = (24 * resources.displayMetrics.density).toInt()
        lp.setMargins(margin, 20, margin, 0)
        input.layoutParams = lp
        container.addView(input)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_member_title))
            .setView(container)
            .setPositiveButton(getString(R.string.action_add)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val settingsPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
                    val fieldsJson = settingsPref.getString("custom_fields", "[]")
                    val type = object : TypeToken<List<CustomField>>() {}.type
                    val customFieldsList: List<CustomField> = Gson().fromJson(fieldsJson, type)

                    val hiddenFields = customFieldsList.asSequence().map { it.name }.toMutableList()

                    people.add(Person(name = name, hiddenFields = hiddenFields))
                    savePeople()
                    adapter.updateItems()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        input.setTextColor(ColorHelper.getTextColor(this))
        input.setHintTextColor(ColorHelper.getTextColor(this) and 0x88FFFFFF.toInt())
    }

    private fun showAddGroupDialog(adapter: PersonAdapter) {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(60, 20, 60, 0)

        val input = EditText(this)
        input.hint = getString(R.string.hint_group_name)
        container.addView(input)

        val parentLabel = TextView(this)
        parentLabel.text = getString(R.string.within_group_optional)
        parentLabel.setPadding(0, 20, 0, 0)
        container.addView(parentLabel)

        val parentSpinner = Spinner(this)
        val groupNames = mutableListOf(getString(R.string.group_none_main))
        groupNames.addAll(groups.asSequence().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }).map { it.name })
        val spinnerAdapter = ColorHelper.createThemedAdapter(this, groupNames)
        parentSpinner.adapter = spinnerAdapter
        container.addView(parentSpinner)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_new_group_title))
            .setView(container)
            .setPositiveButton(getString(R.string.action_add)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val parentIdx = parentSpinner.selectedItemPosition
                    val sortedGroups = groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    val parentId = if (parentIdx == 0) null else sortedGroups[parentIdx - 1].id
                    groups.add(Group(name = name, parentGroupId = parentId))
                    savePeople()
                    adapter.updateItems()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        input.setTextColor(ColorHelper.getTextColor(this))
        parentLabel.setTextColor(ColorHelper.getTextColor(this))
    }

    private fun showEditGroupDialog(group: Group) {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(60, 20, 60, 0)

        val input = EditText(this)
        input.hint = getString(R.string.hint_group_name)
        input.setText(group.name)
        container.addView(input)

        val parentLabel = TextView(this)
        parentLabel.text = getString(R.string.within_group_optional)
        parentLabel.setPadding(0, 20, 0, 0)
        container.addView(parentLabel)

        val parentSpinner = Spinner(this)

        val availableParents = groups.filter { potentialParent ->
            if (potentialParent.id == group.id) return@filter false
            var curr: Group? = potentialParent
            val visited = mutableSetOf<String>()
            while (curr != null && curr.id !in visited) {
                visited.add(curr.id)
                if (curr.parentGroupId == group.id) return@filter false
                curr = groups.find { it.id == curr?.parentGroupId }
            }
            true
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        val groupNames = mutableListOf(getString(R.string.group_none_main))
        groupNames.addAll(availableParents.map { it.name })
        val spinnerAdapter = ColorHelper.createThemedAdapter(this, groupNames)
        parentSpinner.adapter = spinnerAdapter

        val currentParentId = group.parentGroupId
        if (currentParentId == null) {
            parentSpinner.setSelection(0)
        } else {
            val idx = availableParents.indexOfFirst { it.id == currentParentId }
            if (idx != -1) parentSpinner.setSelection(idx + 1)
        }
        container.addView(parentSpinner)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_edit_group_title))
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    group.name = newName
                    val parentIdx = parentSpinner.selectedItemPosition
                    group.parentGroupId = if (parentIdx == 0) null else availableParents[parentIdx - 1].id
                    savePeople()
                    (findViewById<RecyclerView>(R.id.recyclerView).adapter as? PersonAdapter)?.updateItems()
                }
            }
            .setNeutralButton(R.string.delete) { _, _ ->
                showDeleteGroupDialog(group)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        input.setTextColor(ColorHelper.getTextColor(this))
        parentLabel.setTextColor(ColorHelper.getTextColor(this))
    }

    private fun showDeleteGroupDialog(group: Group) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_delete_group_title))
            .setMessage(getString(R.string.dialog_delete_group_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                people.forEach { it.groupIds?.remove(group.id) }
                groups.filter { it.parentGroupId == group.id }.forEach { it.parentGroupId = null }
                groups.remove(group)
                savePeople()
                (findViewById<RecyclerView>(R.id.recyclerView).adapter as? PersonAdapter)?.updateItems()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private var quickUnfrontDialog: androidx.appcompat.app.AlertDialog? = null

    private fun toggleFront(person: Person) {
        if (person.isArchived) return
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        person.isFront = !person.isFront

        if (person.isFront) {
            val pendingMsg = sharedPref.getString("pending_next_fronter_message", null)
            val pendingNoteId = sharedPref.getString("pending_next_fronter_note_id", null)
            
            if (pendingMsg != null) {
                person.frontMessage = pendingMsg
                person.messageRead = false

                if (pendingNoteId != null) {
                    val notesJson = sharedPref.getString("diary_notes", "[]") ?: "[]"
                    val type = object : TypeToken<MutableList<DiaryNote>>() {}.type
                    val allNotes: MutableList<DiaryNote> = try { 
                        Gson().fromJson(notesJson, type) 
                    } catch (_: Exception) { mutableListOf() }
                    
                    val note = allNotes.find { it.id == pendingNoteId }
                    if (note != null) {
                        note.nextFronterRecipient = person.name
                        if (note.linkedMemberIds == null) note.linkedMemberIds = mutableListOf()
                        if (note.linkedMemberIds?.contains(person.id) == false) {
                            note.linkedMemberIds?.add(person.id)
                            sharedPref.edit().putString("diary_notes", Gson().toJson(allNotes)).apply()
                        }
                    }
                }
                
                sharedPref.edit().remove("pending_next_fronter_message").remove("pending_next_fronter_note_id").apply()
                showFrontMessageNotification(person)
            }
            
            sessions.add(FrontSession(person.name, System.currentTimeMillis(), personId = person.id))
            savePeople()
            updateUI()

            if (!person.frontMessage.isNullOrBlank() && !person.messageRead) {
                showFrontMessageNotification(person)
            }
            SysmediaNotificationHelper.checkAndNotify(this, person.id)
        } else {
            sessions.filter { (it.personId == person.id || (it.personId == null && it.personName == person.name)) && it.endTime == null }
                .forEach { it.endTime = System.currentTimeMillis() }
            savePeople()
            updateUI()
        }
    }

    private fun showQuickUnfrontDialog() {
        val frontPeople = people.filter { it.isFront && !it.isArchived && !it.isSysmediaOnly }
            .distinctBy { it.id }
            .let { MemberHelper.getSortedPeople(it, groups) }

        if (frontPeople.isEmpty()) {
            quickUnfrontDialog?.dismiss()
            quickUnfrontDialog = null
            return
        }

        val names = frontPeople.map { it.name }.toMutableList()
        val filteredNames = names.toMutableList()

        val textColor = ColorHelper.getTextColor(this)
        val bgColor = ColorHelper.getBgColor(this)
        val btnColor = ColorHelper.getBtnColor(this)
        val btnTextColor = ColorHelper.getBtnTextColor(this)

        val adapter = object : ArrayAdapter<String>(this, R.layout.item_unfront_dialog, R.id.textName, filteredNames) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val nameTxt = v.findViewById<TextView>(R.id.textName)
                val noteTxt = v.findViewById<TextView>(R.id.textNote)
                val llNameContainer = v.findViewById<LinearLayout>(R.id.llNameContainer)
                val arrowTxt = v.findViewById<TextView>(R.id.textArrow)
                val ivNote = v.findViewById<ImageView>(R.id.ivNote)

                val currentName = filteredNames[position]
                val person = frontPeople.find { it.name == currentName }
                val session = sessions.find { (it.personId == person?.id || (it.personId == null && it.personName == currentName)) && it.endTime == null }

                nameTxt.setTextColor(textColor)
                noteTxt.setTextColor(textColor)
                arrowTxt.setTextColor(btnTextColor)
                arrowTxt.setBackgroundColor(btnColor)
                v.setBackgroundColor(bgColor)

                if (session?.note.isNullOrBlank()) {
                    noteTxt.visibility = View.GONE
                } else {
                    noteTxt.visibility = View.VISIBLE
                    noteTxt.text = session?.note
                }

                val editNoteAction = View.OnClickListener {
                    if (person != null && session != null) {
                        val input = EditText(this@MainActivity).apply {
                            setText(session.note ?: "")
                            hint = getString(R.string.note)
                            setTextColor(textColor)
                        }
                        val d = androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle(person.name)
                            .setMessage(getString(R.string.note))
                            .setView(input)
                            .setPositiveButton(R.string.save) { _, _ ->
                                session.note = input.text.toString().ifBlank { null }
                                savePeople()
                                notifyDataSetChanged()
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .create()
                        d.show()
                        ColorHelper.styleSupportAlertDialog(d, this@MainActivity)
                    }
                }

                ivNote.setColorFilter(textColor)
                ivNote.setOnClickListener(editNoteAction)
                llNameContainer.setOnClickListener(editNoteAction)

                arrowTxt.setOnClickListener {
                    if (person != null) {
                        toggleFront(person)
                        showQuickUnfrontDialog()
                    }
                }

                return v
            }
        }

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(32, 16, 32, 0)

        val etSearch = EditText(this).apply {
            hint = getString(R.string.action_search)
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0)
            compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(textColor)
            setTextColor(textColor)
            setHintTextColor(textColor and 0x88FFFFFF.toInt())
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filteredNames.clear()
                    if (s.isNullOrBlank()) {
                        filteredNames.addAll(names)
                    } else {
                        filteredNames.addAll(names.filter { it.contains(s, ignoreCase = true) })
                    }
                    adapter.notifyDataSetChanged()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        container.addView(etSearch)

        val listView = ListView(this)
        listView.adapter = adapter
        listView.divider = null
        container.addView(listView)

        quickUnfrontDialog?.dismiss()
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_quick_unfront_title))
            .setView(container)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        quickUnfrontDialog = dialog
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun savePeople() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val gson = com.google.gson.GsonBuilder().disableHtmlEscaping().create()

        val normalPeople = people.filter { !it.isSysmediaOnly }
        val sysmediaOnly = people.filter { it.isSysmediaOnly }

        sharedPref.edit(commit = true) {
            putString("people_list", gson.toJson(normalPeople))
            putString("sysmedia_people_list", gson.toJson(sysmediaOnly))
            putString("sessions_list", gson.toJson(sessions))
            putString("groups_list", gson.toJson(groups))
        }
    }
}