package com.interli.plural

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WhoAmIActivity : BaseActivity() {

    private val sessionAnswers = mutableMapOf<String, String>() // activityName -> "LIKE"/"NEUTRAL"/"DISLIKE"
    private lateinit var people: List<Person>
    private lateinit var moodEntries: List<MoodActivity.MoodEntry>
    private val gson = Gson()

    private lateinit var rankingContainer: LinearLayout
    private lateinit var cardsContainer: LinearLayout
    private lateinit var etActivitySearch: EditText

    private var dataSourceMode = "BOTH" // "MOOD", "IDENTITY", "BOTH"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_who_am_i)
        setupNavigationDrawer()

        people = MemberHelper.loadAllPeople(this).filter { !it.isArchived }
        moodEntries = loadMoodEntries()

        rankingContainer = findViewById(R.id.rankingContainer)
        cardsContainer = findViewById(R.id.cardsContainer)
        etActivitySearch = findViewById(R.id.etActivitySearch)

        findViewById<Button>(R.id.btnAddItem).setOnClickListener { showAddItemDialog() }
        findViewById<Button>(R.id.btnManageGroups).setOnClickListener { showManageGroupsDialog() }
        findViewById<Button>(R.id.btnDataSource).setOnClickListener { showDataSourceDialog() }

        etActivitySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderAll(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        renderAll()
        updateRanking()
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

                updateSelectionUi(btnLike, btnNeutral, btnDislike, sessionAnswers[itemName])

                btnLike.setOnClickListener {
                    toggleAnswer(itemName, "LIKE")
                    updateSelectionUi(btnLike, btnNeutral, btnDislike, sessionAnswers[itemName])
                    updateRanking()
                }
                btnNeutral.setOnClickListener {
                    toggleAnswer(itemName, "NEUTRAL")
                    updateSelectionUi(btnLike, btnNeutral, btnDislike, sessionAnswers[itemName])
                    updateRanking()
                }
                btnDislike.setOnClickListener {
                    toggleAnswer(itemName, "DISLIKE")
                    updateSelectionUi(btnLike, btnNeutral, btnDislike, sessionAnswers[itemName])
                    updateRanking()
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

    private fun updateSelectionUi(like: View, neutral: View, dislike: View, selectedType: String?) {
        like.alpha = if (selectedType == "LIKE") 1.0f else 0.3f
        neutral.alpha = if (selectedType == "NEUTRAL") 1.0f else 0.3f
        dislike.alpha = if (selectedType == "DISLIKE") 1.0f else 0.3f

        like.scaleX = if (selectedType == "LIKE") 1.2f else 1.0f
        like.scaleY = if (selectedType == "LIKE") 1.2f else 1.0f
        neutral.scaleX = if (selectedType == "NEUTRAL") 1.2f else 1.0f
        neutral.scaleY = if (selectedType == "NEUTRAL") 1.2f else 1.0f
        dislike.scaleX = if (selectedType == "DISLIKE") 1.2f else 1.0f
        dislike.scaleY = if (selectedType == "DISLIKE") 1.2f else 1.0f
    }

    private fun updateRanking() {
        if (rankingContainer.childCount == 0) return
        val titleView = rankingContainer.getChildAt(0)
        rankingContainer.removeAllViews()
        rankingContainer.addView(titleView)

        if (sessionAnswers.isEmpty()) {
            val tv = TextView(this).apply {
                text = getString(R.string.no_data_for_matching)
                alpha = 0.5f
                setTextColor(ColorHelper.getTextColor(this@WhoAmIActivity))
            }
            rankingContainer.addView(tv)
            return
        }

        val memberScores = people.map { person ->
            val score = calculateScore(person)
            person to score
        }.filter { it.second != null }
            .sortedByDescending { it.second }

        val withData = memberScores.filter { it.second!! >= -1f }
        val withoutData = people.filter { p -> memberScores.none { it.first.id == p.id } }
        val themeTextColor = ColorHelper.getTextColor(this)

        withData.forEach { (person, score) ->
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
            if (row.parent != null) (row.parent as ViewGroup).removeView(row)
            rankingContainer.addView(row)
        }

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

    private fun calculateScore(person: Person): Float? {
        val personManualPrefs = if (dataSourceMode == "IDENTITY" || dataSourceMode == "BOTH") {
            person.safePreferences
        } else emptyList()

        val personHistoricalData = if (dataSourceMode == "MOOD" || dataSourceMode == "BOTH") {
            val personMoods = moodEntries.filter { it.memberIds.contains(person.id) }
            sessionAnswers.keys.mapNotNull { activityName ->
                val moodsWithActivity = personMoods.filter { it.activities.contains(activityName) }
                if (moodsWithActivity.isEmpty()) null
                else {
                    val avgScore = moodsWithActivity.map { entry ->
                        when (entry.moodLabel) {
                            "mood_rad" -> 1.0f
                            "mood_good" -> 0.8f
                            "mood_meh" -> 0.5f
                            "mood_bad" -> 0.2f
                            "mood_awful" -> 0.0f
                            else -> 0.5f
                        }
                    }.average().toFloat()

                    val type = when {
                        avgScore >= 0.7f -> "LIKE"
                        avgScore <= 0.3f -> "DISLIKE"
                        else -> "NEUTRAL"
                    }
                    activityName to type
                }
            }.toMap()
        } else emptyMap()

        val hasAnyRelevantInfo = sessionAnswers.keys.any { key ->
            personManualPrefs.any { it.activityName == key } || personHistoricalData.containsKey(key)
        }
        if (!hasAnyRelevantInfo) return null

        var totalPossible = 0f
        var currentScore = 0f

        sessionAnswers.forEach { (activityName, userPref) ->
            val manualPref = personManualPrefs.find { it.activityName == activityName }?.preferenceType
            val historicalPref = personHistoricalData[activityName]
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

        val userMentioned = sessionAnswers.keys
        val extraTraitsCount = personManualPrefs.count {
            it.activityName !in userMentioned && it.preferenceType != "NEUTRAL"
        }
        if (extraTraitsCount > 0) {
            totalPossible += extraTraitsCount * 0.1f
        }

        return (currentScore / totalPossible).coerceIn(0f, 1f)
    }

    private fun showDataSourceDialog() {
        val options = arrayOf(
            getString(R.string.source_identity_only),
            getString(R.string.source_mood_only),
            getString(R.string.source_both)
        )
        val currentIdx = when(dataSourceMode) {
            "IDENTITY" -> 0
            "MOOD" -> 1
            else -> 2
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.action_data_source)
            .setSingleChoiceItems(options, currentIdx) { d, which ->
                dataSourceMode = when(which) {
                    0 -> "IDENTITY"
                    1 -> "MOOD"
                    else -> "BOTH"
                }
                renderAll(etActivitySearch.text.toString())
                updateRanking()
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showAddItemDialog() {
        val groups = loadIdentityGroups()
        if (groups.isEmpty()) {
            showManageGroupsDialog()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 0)
        }

        val input = EditText(this).apply { hint = getString(R.string.hint_item_name) }
        container.addView(input)

        val spinner = Spinner(this)
        spinner.adapter = ColorHelper.createThemedAdapter(this, groups.map { it.name })
        container.addView(spinner)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_new_item)
            .setView(container)
            .setPositiveButton(R.string.action_add) { _, _ ->
                val name = input.text.toString().trim()
                val groupIdx = spinner.selectedItemPosition
                if (name.isNotEmpty() && groupIdx >= 0) {
                    groups[groupIdx].itemNames.add(name)
                    saveIdentityGroups(groups)
                    renderAll(etActivitySearch.text.toString())
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showManageGroupsDialog() {
        val groups = loadIdentityGroups().toMutableList()
        val names = groups.map { it.name }.toMutableList()
        names.add("+ " + getString(R.string.dialog_new_group_title))
        names.add("⇅ " + getString(R.string.action_reorder))

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.label_groups)
            .setItems(names.toTypedArray()) { _, which ->
                when {
                    which == names.size - 2 -> showCreateGroupDialog()
                    which == names.size - 1 -> showReorderGroupsDialog()
                    else -> {
                        val group = groups[which]
                        val subOptions = arrayOf(getString(R.string.edit), getString(R.string.action_reorder))
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle(group.name)
                            .setItems(subOptions) { _, optIdx ->
                                if (optIdx == 0) showEditGroupDialog(group)
                                else showReorderItemsDialog(group)
                            }
                            .show()
                            .let { ColorHelper.styleSupportAlertDialog(it, this) }
                    }
                }
            }
            .setNegativeButton(R.string.close, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showReorderGroupsDialog() {
        val groups = loadIdentityGroups().toMutableList()
        val names = groups.map { it.name }.toTypedArray()

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.action_reorder)
            .setItems(names) { _, which ->
                val options = arrayOf("↑ Move Up", "↓ Move Down")
                val optDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setItems(options) { _, opt ->
                        if (opt == 0 && which > 0) {
                            val item = groups.removeAt(which)
                            groups.add(which - 1, item)
                        } else if (opt == 1 && which < groups.size - 1) {
                            val item = groups.removeAt(which)
                            groups.add(which + 1, item)
                        }
                        groups.forEachIndexed { index, identityGroup -> index.also { identityGroup.manualOrder = it } }
                        saveIdentityGroups(groups)
                        renderAll(etActivitySearch.text.toString())
                        showReorderGroupsDialog()
                    }
                    .create()
                optDialog.show()
                ColorHelper.styleSupportAlertDialog(optDialog, this)
            }
            .setNegativeButton(R.string.back, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showReorderItemsDialog(group: IdentityGroup) {
        val items = group.itemNames.toMutableList()
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_reorder) + ": " + group.name)
            .setItems(items.toTypedArray()) { _, which ->
                val options = arrayOf("↑ Move Up", "↓ Move Down")
                val optDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setItems(options) { _, opt ->
                        if (opt == 0 && which > 0) {
                            val item = items.removeAt(which)
                            items.add(which - 1, item)
                        } else if (opt == 1 && which < items.size - 1) {
                            val item = items.removeAt(which)
                            items.add(which + 1, item)
                        }
                        val allGroups = loadIdentityGroups()
                        allGroups.find { it.id == group.id }?.itemNames?.apply { clear(); addAll(items) }
                        saveIdentityGroups(allGroups)
                        renderAll(etActivitySearch.text.toString())
                        showReorderItemsDialog(allGroups.find { it.id == group.id }!!)
                    }
                    .create()
                optDialog.show()
                ColorHelper.styleSupportAlertDialog(optDialog, this)
            }
            .setNegativeButton(R.string.back, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showCreateGroupDialog() {
        val input = EditText(this).apply { hint = getString(R.string.hint_group_name) }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_new_group_title)
            .setView(input)
            .setPositiveButton(R.string.action_add) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val groups = loadIdentityGroups().toMutableList()
                    groups.add(IdentityGroup(name = name, manualOrder = groups.size))
                    saveIdentityGroups(groups)
                    renderAll(etActivitySearch.text.toString())
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showEditGroupDialog(group: IdentityGroup) {
        val input = EditText(this).apply { setText(group.name) }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_group_title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val groups = loadIdentityGroups()
                    groups.find { it.id == group.id }?.name = name
                    saveIdentityGroups(groups)
                    renderAll()
                }
            }
            .setNeutralButton(R.string.delete, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val btnDel = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
            var clicks = 0
            btnDel.setOnClickListener {
                clicks++
                if (clicks >= 8) {
                    val groups = loadIdentityGroups().toMutableList()
                    groups.removeAll { it.id == group.id }
                    saveIdentityGroups(groups)
                    renderAll()
                    dialog.dismiss()
                } else {
                    btnDel.text = getString(R.string.delete_group_8x, 8 - clicks)
                }
            }
        }
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showEditItemDialog(groupId: String, itemName: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 0)
        }
        val input = EditText(this).apply { setText(itemName) }
        container.addView(input)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_item)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val groups = loadIdentityGroups()
                    groups.find { it.id == groupId }?.let { g ->
                        val idx = g.itemNames.indexOf(itemName)
                        if (idx != -1) g.itemNames[idx] = newName
                    }
                    saveIdentityGroups(groups)
                    renderAll()
                }
            }
            .setNeutralButton(R.string.delete, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val btnDel = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
            var clicks = 0
            btnDel.setOnClickListener {
                clicks++
                if (clicks >= 8) {
                    val groups = loadIdentityGroups()
                    groups.find { it.id == groupId }?.itemNames?.remove(itemName)
                    saveIdentityGroups(groups)
                    renderAll()
                    dialog.dismiss()
                } else {
                    btnDel.text = getString(R.string.delete_item_8x, 8 - clicks)
                }
            }
        }
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun moveItem(groupId: String, itemName: String, direction: Int) {
        val allGroups = loadIdentityGroups()
        val group = allGroups.find { it.id == groupId } ?: return
        val items = group.itemNames
        val currentIndex = items.indexOf(itemName)

        val newIndex = currentIndex + direction
        if (newIndex in 0 until items.size) {
            val temp = items[currentIndex]
            items[currentIndex] = items[newIndex]
            items[newIndex] = temp

            saveIdentityGroups(allGroups)
            renderAll(etActivitySearch.text.toString())
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}