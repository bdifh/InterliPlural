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

class MemberPreferencesActivity : BaseActivity() {

    private lateinit var personId: String
    private lateinit var people: MutableList<Person>
    private lateinit var person: Person
    private val gson = Gson()
    private lateinit var cardsContainer: LinearLayout
    private lateinit var etActivitySearch: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_member_preferences)

        ColorHelper.applySettings(this)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true

        personId = intent.getStringExtra("person_id") ?: run { finish(); return }
        people = MemberHelper.loadAllPeople(this)
        person = people.find { it.id == personId } ?: run { finish(); return }

        cardsContainer = findViewById(R.id.cardsContainer)
        etActivitySearch = findViewById(R.id.etActivitySearch)
        findViewById<View>(R.id.topAppBar).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnAddItem).setOnClickListener { showAddItemDialog() }
        findViewById<Button>(R.id.btnManageGroups).setOnClickListener { showManageGroupsDialog() }

        etActivitySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderAll(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        renderAll()
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
        val moodGroups = loadMoodActivityGroups()
        val identityGroups = loadIdentityGroups()
        val collapsedMoodGroups = loadCollapsedMoodGroups()

        moodGroups.forEach { group ->
            val isExpanded = !collapsedMoodGroups.contains(group.id)
            renderGroupCard(group.id, group.name, group.activityNames, query, isMoodGroup = true, isExpanded = isExpanded)
        }
        identityGroups.forEach { group ->
            renderGroupCard(group.id, group.name, group.itemNames, query, isMoodGroup = false, isExpanded = group.isExpanded)
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
            setCardBackgroundColor(ColorHelper.getBgColor(this@MemberPreferencesActivity))
            strokeWidth = 2
            strokeColor = (ColorHelper.getTextColor(this@MemberPreferencesActivity) and 0x33FFFFFF) or 0x33000000
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
            setTextColor(ColorHelper.getTextColor(this@MemberPreferencesActivity))
        }
        headerLayout.addView(tvArrow)

        val titleTv = TextView(this).apply {
            text = groupName
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTypeface(null, Typeface.BOLD)
            alpha = 0.7f
            setTextColor(ColorHelper.getTextColor(this@MemberPreferencesActivity))
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
                val rowView = inflater.inflate(R.layout.item_preference_row, contentLayout, false)
                val tvItemName = rowView.findViewById<TextView>(R.id.tvItemName)
                tvItemName.apply {
                    text = itemName
                    setTextColor(ColorHelper.getTextColor(this@MemberPreferencesActivity))
                }

                val btnLike = rowView.findViewById<View>(R.id.btnLike)
                val btnNeutral = rowView.findViewById<View>(R.id.btnNeutral)
                val btnDislike = rowView.findViewById<View>(R.id.btnDislike)

                val currentPref = person.safePreferences.find { it.activityName == itemName }
                updateSelectionUi(btnLike, btnNeutral, btnDislike, currentPref?.preferenceType)

                val onPrefClick = { type: String ->
                    var pref = person.safePreferences.find { it.activityName == itemName }
                    if (pref == null) {
                        pref = MemberPreference(itemName, type)
                        person.safePreferences.add(pref)
                        updateSelectionUi(btnLike, btnNeutral, btnDislike, type)
                    } else {
                        if (pref.preferenceType == type) {
                            person.safePreferences.remove(pref)
                            updateSelectionUi(btnLike, btnNeutral, btnDislike, null)
                        } else {
                            val newPref = MemberPreference(itemName, type)
                            person.safePreferences[person.safePreferences.indexOf(pref)] = newPref
                            updateSelectionUi(btnLike, btnNeutral, btnDislike, type)
                        }
                    }
                    saveData()
                }

                btnLike.setOnClickListener { onPrefClick("LIKE") }
                btnNeutral.setOnClickListener { onPrefClick("NEUTRAL") }
                btnDislike.setOnClickListener { onPrefClick("DISLIKE") }

                if (!isMoodGroup) {
                    val longClickListener = View.OnLongClickListener {
                        val options = arrayOf(
                            getString(R.string.edit),
                            "↑ " + getString(R.string.action_move_up),
                            "↓ " + getString(R.string.action_move_down),
                            getString(R.string.delete)
                        )

                        androidx.appcompat.app.AlertDialog.Builder(this@MemberPreferencesActivity)
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
                            .let { ColorHelper.styleSupportAlertDialog(it, this@MemberPreferencesActivity) }
                        true
                    }
                    rowView.setOnLongClickListener(longClickListener)
                    tvItemName.setOnLongClickListener(longClickListener)
                }

                contentLayout.addView(rowView)
            }
        }

        card.addView(contentLayout)
        cardsContainer.addView(card)
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

    private fun saveData() {
        MemberHelper.savePeople(this, people)
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
                val idx = spinner.selectedItemPosition
                if (name.isNotEmpty() && idx >= 0) {
                    groups[idx].itemNames.add(name)
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
