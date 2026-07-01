package com.interli.plural

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WhoAmIActivity : BaseActivity() {

    private val sessionAnswers = mutableMapOf<String, String>() // activityName -> "LIKE"/"NEUTRAL"/"DISLIKE"
    private val nowAnswers = mutableSetOf<String>()
    private lateinit var people: List<Person>
    private lateinit var moodEntries: List<MoodActivity.MoodEntry>
    private val gson = Gson()

    private lateinit var rankingContainer: LinearLayout
    private lateinit var cardsContainer: LinearLayout
    private lateinit var etActivitySearch: EditText

    private var dataSourceMode = "BOTH" // "MOOD", "IDENTITY", "BOTH"

    private val handler = Handler(Looper.getMainLooper())
    private var rankingRunnable: Runnable? = null
    
    private var cachedPersonActivityStats: Map<String, Map<String, Pair<Float, Int>>>? = null
    private var cachedMaxActivityCounts: Map<String, Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_who_am_i)
        setupNavigationDrawer()

        people = MemberHelper.loadAllPeople(this).filter { !it.isArchived }
        moodEntries = loadMoodEntries()

        rankingContainer = findViewById(R.id.rankingContainer)
        cardsContainer = findViewById(R.id.cardsContainer)
        etActivitySearch = findViewById(R.id.etActivitySearch)

        val sharedPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        dataSourceMode = sharedPref.getString("who_am_i_data_source", "BOTH") ?: "BOTH"

        findViewById<Button>(R.id.btnAddItem).setOnClickListener { showAddItemDialog() }
        findViewById<Button>(R.id.btnManageGroups).setOnClickListener { showManageGroupsDialog() }
        findViewById<Button>(R.id.btnDataSource).setOnClickListener { showDataSourceDialog() }
        findViewById<View>(R.id.btnHelp).setOnClickListener { showHelpDialog() }

        etActivitySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderAll(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        renderAll()
        triggerRankingUpdate(immediate = true)
    }

    private fun triggerRankingUpdate(immediate: Boolean = false) {
        rankingRunnable?.let { handler.removeCallbacks(it) }
        rankingRunnable = Runnable { updateRanking() }
        if (immediate) handler.post(rankingRunnable!!)
        else handler.postDelayed(rankingRunnable!!, 300)
    }

    private fun loadMoodEntries(): List<MoodActivity.MoodEntry> {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = prefs.getString("mood_entries", "[]") ?: "[]"
        val type = object : TypeToken<List<MoodActivity.MoodEntry>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }

    private fun loadMoodActivityGroups(): List<MoodActivity.ActivityGroup> {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = prefs.getString("activity_groups", "[]") ?: "[]"
        val type = object : TypeToken<List<MoodActivity.ActivityGroup>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }

    private fun loadIdentityGroups(): List<IdentityGroup> {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = prefs.getString("identity_groups", "[]") ?: "[]"
        val type = object : TypeToken<List<IdentityGroup>>() {}.type
        return try {
            val list: List<IdentityGroup> = gson.fromJson(json, type) ?: emptyList()
            list.sortedBy { it.manualOrder }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveIdentityGroups(groups: List<IdentityGroup>) {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        prefs.edit().putString("identity_groups", gson.toJson(groups)).apply()
    }

    private fun persistMoodActivityGroups(list: List<MoodActivity.ActivityGroup>) {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        prefs.edit().putString("activity_groups", gson.toJson(list)).apply()
    }

    private fun loadCollapsedMoodGroups(): Set<String> {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        return prefs.getStringSet("collapsed_mood_groups", emptySet()) ?: emptySet()
    }

    private fun saveCollapsedMoodGroups(ids: Set<String>) {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        prefs.edit().putStringSet("collapsed_mood_groups", ids).apply()
    }

    private fun renderAll(query: String = "") {
        cardsContainer.removeAllViews()

        if (dataSourceMode == "IDENTITY" || dataSourceMode == "BOTH") {
            val identityGroups = loadIdentityGroups()
            identityGroups.forEach { group ->
                renderGroupCard(group.id, group.name, group.itemNames, query, isMoodGroup = false, isExpanded = group.isExpanded)
            }
        }

        if (dataSourceMode == "MOOD" || dataSourceMode == "BOTH") {
            val moodGroups = loadMoodActivityGroups()
            val collapsedMoodGroups = loadCollapsedMoodGroups()
            moodGroups.forEach { group ->
                val isExpanded = !collapsedMoodGroups.contains(group.id)
                renderGroupCard(group.id, group.name, group.activityNames, query, isMoodGroup = true, isExpanded = isExpanded)
            }
        }
    }

    private fun renderGroupCard(groupId: String, groupName: String, items: List<String>, query: String, isMoodGroup: Boolean, isExpanded: Boolean) {
        val filteredItems = items.filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
        if (filteredItems.isEmpty() && query.isNotEmpty()) return

        val inflater = LayoutInflater.from(this)
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, (16 * resources.displayMetrics.density).toInt())
            }
            radius = 12f * resources.displayMetrics.density
            setCardBackgroundColor(ColorHelper.getBgColor(this@WhoAmIActivity))
            strokeWidth = 2
            strokeColor = (ColorHelper.getTextColor(this@WhoAmIActivity) and 0x33FFFFFF) or 0x33000000
            cardElevation = 0f
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
        }

        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8.dpToPx())
            isClickable = true
            isFocusable = true
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }

        val tvArrow = TextView(this).apply {
            text = if (isExpanded) "▼" else "▶"
            textSize = 14f
            setPadding(0, 0, 8.dpToPx(), 0)
            setTextColor(ColorHelper.getTextColor(this@WhoAmIActivity))
        }
        headerLayout.addView(tvArrow)

        val titleTv = TextView(this).apply {
            text = groupName
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTypeface(null, Typeface.BOLD)
            alpha = 0.7f
            setTextColor(ColorHelper.getTextColor(this@WhoAmIActivity))
        }
        headerLayout.addView(titleTv)

        headerLayout.setOnClickListener {
            if (isMoodGroup) {
                val collapsed = loadCollapsedMoodGroups().toMutableSet()
                if (collapsed.contains(groupId)) collapsed.remove(groupId)
                else collapsed.add(groupId)
                saveCollapsedMoodGroups(collapsed)
                renderAll(etActivitySearch.text.toString())
            } else {
                val groups = loadIdentityGroups()
                groups.find { it.id == groupId }?.let {
                    it.isExpanded = !it.isExpanded
                    saveIdentityGroups(groups)
                    renderAll(etActivitySearch.text.toString())
                }
            }
        }
        headerLayout.setOnLongClickListener {
            if (isMoodGroup) {
                loadMoodActivityGroups().find { it.id == groupId }?.let {
                    showEditMoodGroupDialog(it)
                }
            } else {
                loadIdentityGroups().find { it.id == groupId }?.let {
                    showEditGroupDialog(it)
                }
            }
            true
        }
        contentLayout.addView(headerLayout)

        if (isExpanded || query.isNotEmpty()) {
            filteredItems.forEach { itemName ->
                val row = inflater.inflate(R.layout.item_preference_row, contentLayout, false)
                val tvItemName = row.findViewById<TextView>(R.id.tvItemName)
                tvItemName.apply {
                    text = itemName
                    setTextColor(ColorHelper.getTextColor(this@WhoAmIActivity))
                }

                val btnLike = row.findViewById<View>(R.id.btnLike)
                val btnNeutral = row.findViewById<View>(R.id.btnNeutral)
                val btnDislike = row.findViewById<View>(R.id.btnDislike)
                val btnNow = row.findViewById<View>(R.id.btnNow)

                btnNow.visibility = if (isMoodGroup) View.VISIBLE else View.GONE

                updateSelectionUi(btnLike, btnNeutral, btnDislike, btnNow, sessionAnswers[itemName], nowAnswers.contains(itemName))

                btnLike.setOnClickListener {
                    toggleAnswer(itemName, "LIKE")
                    updateSelectionUi(btnLike, btnNeutral, btnDislike, btnNow, sessionAnswers[itemName], nowAnswers.contains(itemName))
                    triggerRankingUpdate()
                }
                btnNeutral.setOnClickListener {
                    toggleAnswer(itemName, "NEUTRAL")
                    updateSelectionUi(btnLike, btnNeutral, btnDislike, btnNow, sessionAnswers[itemName], nowAnswers.contains(itemName))
                    triggerRankingUpdate()
                }
                btnDislike.setOnClickListener {
                    toggleAnswer(itemName, "DISLIKE")
                    updateSelectionUi(btnLike, btnNeutral, btnDislike, btnNow, sessionAnswers[itemName], nowAnswers.contains(itemName))
                    triggerRankingUpdate()
                }
                btnNow.setOnClickListener {
                    if (nowAnswers.contains(itemName)) nowAnswers.remove(itemName)
                    else nowAnswers.add(itemName)
                    updateSelectionUi(btnLike, btnNeutral, btnDislike, btnNow, sessionAnswers[itemName], nowAnswers.contains(itemName))
                    triggerRankingUpdate()
                }

                if (!isMoodGroup) {
                    val longClickListener = View.OnLongClickListener {
                        val options = arrayOf(
                            getString(R.string.edit),
                            "↑ " + getString(R.string.action_move_up),
                            "↓ " + getString(R.string.action_move_down),
                            getString(R.string.delete)
                        )

                        androidx.appcompat.app.AlertDialog.Builder(this@WhoAmIActivity)
                            .setTitle(itemName)
                            .setItems(options) { _, which ->
                                when (which) {
                                    0 -> showEditItemDialog(groupId, itemName)
                                    1 -> moveItem(groupId, itemName, -1)
                                    2 -> moveItem(groupId, itemName, 1)
                                    3 -> showEditItemDialog(groupId, itemName)
                                }
                            }
                            .show()
                            .let { ColorHelper.styleSupportAlertDialog(it, this@WhoAmIActivity) }
                        true
                    }
                    row.setOnLongClickListener(longClickListener)
                    tvItemName.setOnLongClickListener(longClickListener)
                }

                if (row.parent != null) (row.parent as ViewGroup).removeView(row)
                contentLayout.addView(row)
            }
        }

        if (contentLayout.parent != null) (contentLayout.parent as ViewGroup).removeView(contentLayout)
        card.addView(contentLayout)
        if (card.parent != null) (card.parent as ViewGroup).removeView(card)
        cardsContainer.addView(card)
    }

    private fun toggleAnswer(name: String, type: String) {
        if (sessionAnswers[name] == type) {
            sessionAnswers.remove(name)
        } else {
            sessionAnswers[name] = type
        }
    }

    private fun updateSelectionUi(like: View, neutral: View, dislike: View, now: View, selection: String?, isNow: Boolean) {
        like.alpha = if (selection == "LIKE") 1.0f else 0.3f
        neutral.alpha = if (selection == "NEUTRAL") 1.0f else 0.3f
        dislike.alpha = if (selection == "DISLIKE") 1.0f else 0.3f
        now.alpha = if (isNow) 1.0f else 0.15f
        
        now.findViewById<TextView>(R.id.tvNow)?.setTextColor(if (isNow) Color.GREEN else ColorHelper.getTextColor(this))
        
        listOf(like, neutral, dislike).forEach {
            it.scaleX = if (selection != null && it == when(selection) { "LIKE" -> like; "NEUTRAL" -> neutral; else -> dislike }) 1.2f else 1.0f
            it.scaleY = it.scaleX
        }
    }

    private fun updateRanking() {
        if (rankingContainer.childCount == 0) return
        val titleView = rankingContainer.getChildAt(0)
        rankingContainer.removeAllViews()
        rankingContainer.addView(titleView)

        if (sessionAnswers.isEmpty() && nowAnswers.isEmpty()) {
            val tv = TextView(this).apply {
                text = getString(R.string.no_data_for_matching)
                alpha = 0.5f
                setTextColor(ColorHelper.getTextColor(this@WhoAmIActivity))
            }
            rankingContainer.addView(tv)
            return
        }

        if (cachedPersonActivityStats == null || cachedMaxActivityCounts == null) {
            val personActivityStats = mutableMapOf<String, MutableMap<String, Pair<Float, Int>>>()
            val maxActivityCounts = mutableMapOf<String, Int>()

            moodEntries.forEach { entry ->
                val mIds = entry.memberIds ?: return@forEach
                val acts = entry.activities ?: return@forEach
                
                val moodWeight = when (entry.moodLabel) {
                    "mood_rad" -> 1.0f
                    "mood_good" -> 0.8f
                    "mood_meh" -> 0.5f
                    "mood_bad" -> 0.2f
                    "mood_awful" -> 0.0f
                    else -> 0.5f
                }

                mIds.forEach { mId ->
                    val mStats = personActivityStats.getOrPut(mId) { mutableMapOf() }
                    acts.forEach { act ->
                        val (sum, count) = mStats[act] ?: (0f to 0)
                        val newCount = count + 1
                        mStats[act] = (sum + moodWeight) to newCount
                        
                        if (newCount > (maxActivityCounts[act] ?: 0)) {
                            maxActivityCounts[act] = newCount
                        }
                    }
                }
            }
            cachedPersonActivityStats = personActivityStats
            cachedMaxActivityCounts = maxActivityCounts
        }

        val memberScores = people.map { person ->
            val score = calculateScore(person, cachedPersonActivityStats!![person.id] ?: emptyMap(), cachedMaxActivityCounts!!)
            person to score
        }.filter { it.second != null }
            .sortedByDescending { it.second }

        val themeTextColor = ColorHelper.getTextColor(this)

        memberScores.forEach { (person, score) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
            }
            val nameTv = TextView(this).apply {
                text = person.name
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(themeTextColor)
                setTypeface(null, Typeface.BOLD)
            }
            val scoreTv = TextView(this).apply {
                text = getString(R.string.match_percentage, (score!! * 100).toInt())
                setTextColor(themeTextColor)
            }
            row.addView(nameTv)
            row.addView(scoreTv)
            rankingContainer.addView(row)
        }

        val withoutData = people.filter { p -> memberScores.none { it.first.id == p.id } }
        if (withoutData.isNotEmpty()) {
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                    setMargins(0, 16, 0, 8)
                }
                setBackgroundColor(Color.LTGRAY)
            }
            rankingContainer.addView(divider)

            val noDataTitle = TextView(this).apply {
                text = getString(R.string.label_no_data)
                alpha = 0.6f
                textSize = 12f
                setPadding(0, 0, 0, 4)
                setTextColor(themeTextColor)
            }
            rankingContainer.addView(noDataTitle)

            val noDataNamesTv = TextView(this).apply {
                text = withoutData.joinToString(", ") { it.name }
                alpha = 0.5f
                textSize = 12f
                setPadding(0, 0, 0, 8)
                setTextColor(themeTextColor)
            }
            rankingContainer.addView(noDataNamesTv)
        }
    }

    private fun calculateScore(person: Person, personMoodStats: Map<String, Pair<Float, Int>>, maxActivityCounts: Map<String, Int>): Float? {
        val personManualPrefs = if (dataSourceMode == "IDENTITY" || dataSourceMode == "BOTH") {
            person.safePreferences
        } else emptyList()

        val relevantHistoricalActivities = if (dataSourceMode == "MOOD" || dataSourceMode == "BOTH") {
            (sessionAnswers.keys + nowAnswers).mapNotNull { activityName ->
                val stats = personMoodStats[activityName] ?: return@mapNotNull null
                val avgScore = stats.first / stats.second.toFloat()
                val type = when {
                    avgScore >= 0.7f -> "LIKE"
                    avgScore <= 0.3f -> "DISLIKE"
                    else -> "NEUTRAL"
                }
                activityName to type
            }.toMap()
        } else emptyMap()

        val hasAnyRelevantInfo = (sessionAnswers.keys + nowAnswers).any { key ->
            personManualPrefs.any { it.activityName == key } || personMoodStats.containsKey(key)
        }
        if (!hasAnyRelevantInfo) return null

        var totalPossible = 0f
        var currentScore = 0f

        // 1. Preference Matching
        sessionAnswers.forEach { (activityName, userPref) ->
            val manualPref = personManualPrefs.find { it.activityName == activityName }?.preferenceType
            val historicalPref = relevantHistoricalActivities[activityName]
            val effectivePref = manualPref ?: historicalPref

            totalPossible += 1f
            if (effectivePref == userPref) {
                currentScore += 1f
            } else if (userPref == "NEUTRAL") {
                currentScore += 0.5f
            } else if (effectivePref == "NEUTRAL" || effectivePref == null) {
                currentScore += 0.2f
            } else {
                currentScore -= 0.5f
            }
        }

        // 2. "Now" Matching (Mood Tracker Frequency)
        nowAnswers.forEach { activityName ->
            totalPossible += 2f // Give high weight to "now" activities
            
            val personCount = personMoodStats[activityName]?.second ?: 0
            if (personCount > 0) {
                val maxCount = maxActivityCounts[activityName] ?: 1
                if (maxCount > 0) {
                    val frequencyBoost = (personCount.toFloat() / maxCount.toFloat())
                    currentScore += frequencyBoost * 2f // Up to 2 points if they are the top user
                }
            } else {
                currentScore -= 0.5f
            }
        }

        val userMentioned = (sessionAnswers.keys + nowAnswers)
        val extraTraitsCount = personManualPrefs.count {
            it.activityName !in userMentioned && it.preferenceType != "NEUTRAL"
        }
        if (extraTraitsCount > 0) {
            totalPossible += extraTraitsCount * 0.1f
        }

        return (currentScore / totalPossible).coerceIn(0f, 1f)
    }

    private fun showHelpDialog() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_help_who_am_i, null)

        // Apply colors to all TextViews in the layout
        val textColor = ColorHelper.getTextColor(this)
        view.findViewById<TextView>(R.id.tvHelpLikeIcon).setTextColor(textColor)
        view.findViewById<TextView>(R.id.tvHelpLikeText).setTextColor(textColor)
        view.findViewById<TextView>(R.id.tvHelpNeutralIcon).setTextColor(textColor)
        view.findViewById<TextView>(R.id.tvHelpNeutralText).setTextColor(textColor)
        view.findViewById<TextView>(R.id.tvHelpDislikeIcon).setTextColor(textColor)
        view.findViewById<TextView>(R.id.tvHelpDislikeText).setTextColor(textColor)
        view.findViewById<TextView>(R.id.tvHelpNowIcon).setTextColor(textColor)
        view.findViewById<TextView>(R.id.tvHelpNowText).setTextColor(textColor)
        view.findViewById<TextView>(R.id.tvHelpNote).setTextColor(textColor)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.help_who_am_i_title)
            .setView(view)
            .setPositiveButton(R.string.done, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showDataSourceDialog() {
        val options = arrayOf(
            getString(R.string.source_mood_only),
            getString(R.string.source_identity_only),
            getString(R.string.source_both)
        )
        val values = arrayOf("MOOD", "IDENTITY", "BOTH")
        val currentIndex = values.indexOf(dataSourceMode)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.action_data_source)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                dataSourceMode = values[which]
                getSharedPreferences("settings_prefs", MODE_PRIVATE).edit().putString("who_am_i_data_source", dataSourceMode).apply()
                dialog.dismiss()
                renderAll(etActivitySearch.text.toString())
                updateRanking()
            }
            .show()
            .let { ColorHelper.styleSupportAlertDialog(it, this) }
    }

    private fun showAddItemDialog() {
        val input = EditText(this).apply { hint = getString(R.string.hint_item_name) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
            addView(input)
        }

        val groups = loadIdentityGroups()
        if (groups.isEmpty()) {
            Toast.makeText(this, getString(R.string.create_groups_first), Toast.LENGTH_SHORT).show()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_new_item)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val groupOptions = groups.map { it.name }.toTypedArray()
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(R.string.label_select_group)
                        .setItems(groupOptions) { _, which ->
                            val selectedGroup = groups[which]
                            selectedGroup.itemNames.add(name)
                            saveIdentityGroups(groups)
                            renderAll(etActivitySearch.text.toString())
                        }
                        .show()
                        .let { ColorHelper.styleSupportAlertDialog(it, this) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let { ColorHelper.styleSupportAlertDialog(it, this) }
    }

    private fun showManageGroupsDialog() {
        val options = arrayOf(
            getString(R.string.action_new_group),
            getString(R.string.action_reorder),
            getString(R.string.edit_groups)
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.action_manage_groups)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCreateGroupDialog()
                    1 -> showReorderGroupsDialog()
                    2 -> {
                        val groups = loadIdentityGroups()
                        val names = groups.map { it.name }.toTypedArray()
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle(R.string.edit_groups)
                            .setItems(names) { _, idx -> showEditGroupDialog(groups[idx]) }
                            .show()
                            .let { ColorHelper.styleSupportAlertDialog(it, this) }
                    }
                }
            }
            .show()
            .let { ColorHelper.styleSupportAlertDialog(it, this) }
    }

    private fun showReorderGroupsDialog() {
        val groups = loadIdentityGroups()
        val names = groups.map { it.name }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.action_reorder)
            .setItems(names) { _, which ->
                val group = groups[which]
                val subOptions = arrayOf(getString(R.string.action_move_up), getString(R.string.action_move_down), getString(R.string.edit), getString(R.string.delete))
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(group.name)
                    .setItems(subOptions) { _, subWhich ->
                        when(subWhich) {
                            0 -> { // Up
                                val idx = groups.indexOf(group)
                                if (idx > 0) {
                                    val temp = groups[idx-1].manualOrder
                                    groups[idx-1].manualOrder = groups[idx].manualOrder
                                    groups[idx].manualOrder = temp
                                    saveIdentityGroups(groups)
                                    renderAll(etActivitySearch.text.toString())
                                }
                            }
                            1 -> { // Down
                                val idx = groups.indexOf(group)
                                if (idx < groups.size - 1) {
                                    val temp = groups[idx+1].manualOrder
                                    groups[idx+1].manualOrder = groups[idx].manualOrder
                                    groups[idx].manualOrder = temp
                                    saveIdentityGroups(groups)
                                    renderAll(etActivitySearch.text.toString())
                                }
                            }
                            2 -> showEditGroupDialog(group)
                            3 -> {
                                val newList = groups.toMutableList()
                                newList.remove(group)
                                saveIdentityGroups(newList)
                                renderAll(etActivitySearch.text.toString())
                            }
                        }
                    }
                    .show()
                    .let { ColorHelper.styleSupportAlertDialog(it, this) }
            }
            .show()
            .let { ColorHelper.styleSupportAlertDialog(it, this) }
    }

    private fun showReorderItemsDialog(group: IdentityGroup) {
        // Not used, handled in render
    }

    private fun showCreateGroupDialog() {
        val input = EditText(this).apply { hint = getString(R.string.hint_group_name) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
            addView(input)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.action_new_group)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val groups = loadIdentityGroups().toMutableList()
                    val newGroup = IdentityGroup(name = name, manualOrder = groups.size)
                    groups.add(newGroup)
                    saveIdentityGroups(groups)
                    renderAll(etActivitySearch.text.toString())
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let { ColorHelper.styleSupportAlertDialog(it, this) }
    }

    private fun showEditGroupDialog(group: IdentityGroup) {
        val input = EditText(this).apply { 
            hint = getString(R.string.hint_group_name)
            setText(group.name)
            setTextColor(ColorHelper.getTextColor(this@WhoAmIActivity))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
            addView(input)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_group_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val groups = loadIdentityGroups()
                    groups.find { it.id == group.id }?.name = name
                    saveIdentityGroups(groups)
                    renderAll(etActivitySearch.text.toString())
                }
            }
            .setNeutralButton(R.string.delete, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val btnDelete = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
            var deleteClicks = 0
            btnDelete.setOnClickListener {
                deleteClicks++
                if (deleteClicks >= 8) {
                    val groups = loadIdentityGroups().toMutableList()
                    groups.removeAll { it.id == group.id }
                    saveIdentityGroups(groups)
                    renderAll(etActivitySearch.text.toString())
                    dialog.dismiss()
                } else {
                    btnDelete.text = getString(R.string.delete_group_8x, 8 - deleteClicks)
                }
            }
        }

        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showEditMoodGroupDialog(group: MoodActivity.ActivityGroup) {
        val input = EditText(this).apply { 
            hint = getString(R.string.hint_group_name)
            setText(group.name)
            setTextColor(ColorHelper.getTextColor(this@WhoAmIActivity))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
            addView(input)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_edit_group_title))
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val groups = loadMoodActivityGroups()
                    groups.find { it.id == group.id }?.name = newName
                    persistMoodActivityGroups(groups)
                    renderAll(etActivitySearch.text.toString())
                }
            }
            .setNeutralButton(getString(R.string.delete), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            val btnDelete = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
            var deleteClicks = 0
            btnDelete.setOnClickListener {
                deleteClicks++
                if (deleteClicks >= 8) {
                    val groups = loadMoodActivityGroups().toMutableList()
                    val groupToDelete = groups.find { it.id == group.id }
                    if (groupToDelete != null) {
                        val activitiesToMove = groupToDelete.activityNames.toList()
                        groups.remove(groupToDelete)
                        
                        if (activitiesToMove.isNotEmpty()) {
                            val generalName = getString(R.string.group_general)
                            var fallbackGroup = groups.find { it.name.equals(generalName, ignoreCase = true) }
                            if (fallbackGroup == null && groups.isNotEmpty()) {
                                fallbackGroup = groups[0]
                            }
                            if (fallbackGroup == null) {
                                fallbackGroup = MoodActivity.ActivityGroup(name = generalName)
                                groups.add(fallbackGroup)
                            }
                            
                            activitiesToMove.forEach { act ->
                                if (!fallbackGroup.activityNames.contains(act)) {
                                    fallbackGroup.activityNames.add(act)
                                }
                            }
                        }
                    }
                    persistMoodActivityGroups(groups)
                    renderAll(etActivitySearch.text.toString())
                    dialog.dismiss()
                } else {
                    btnDelete.text = getString(R.string.delete_group_8x, 8 - deleteClicks)
                }
            }
        }
            
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showEditItemDialog(groupId: String, oldName: String) {
        val input = EditText(this).apply { 
            hint = getString(R.string.hint_item_name)
            setText(oldName)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
            addView(input)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_item)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val groups = loadIdentityGroups()
                    groups.find { it.id == groupId }?.let { group ->
                        val idx = group.itemNames.indexOf(oldName)
                        if (idx != -1) group.itemNames[idx] = name
                    }
                    saveIdentityGroups(groups)
                    renderAll(etActivitySearch.text.toString())
                }
            }
            .setNeutralButton(R.string.delete) { _, _ ->
                val groups = loadIdentityGroups()
                groups.find { it.id == groupId }?.itemNames?.remove(oldName)
                saveIdentityGroups(groups)
                renderAll(etActivitySearch.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let { ColorHelper.styleSupportAlertDialog(it, this) }
    }

    private fun moveItem(groupId: String, itemName: String, direction: Int) {
        val groups = loadIdentityGroups()
        groups.find { it.id == groupId }?.let { group ->
            val idx = group.itemNames.indexOf(itemName)
            if (idx != -1) {
                val newIdx = idx + direction
                if (newIdx in 0 until group.itemNames.size) {
                    java.util.Collections.swap(group.itemNames, idx, newIdx)
                    saveIdentityGroups(groups)
                    renderAll(etActivitySearch.text.toString())
                }
            }
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
