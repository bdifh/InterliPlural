package com.interli.plural.features.mood

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.CoFrontMoodTheme
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.core.DialogHelper
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.Group
import com.interli.plural.MoodTheme
import com.interli.plural.Person
import com.interli.plural.R

class MoodThemesActivity : BaseActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MoodThemesAdapter
    private var themes: MutableList<MoodTheme> = mutableListOf()
    private val moodColors = mutableListOf("#fffa94", "#54bd44", "#8844bd", "#4446bd", "#3a3a47")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood_themes)
        recyclerView = findViewById(R.id.recyclerViewThemes)
        recyclerView.layoutManager = LinearLayoutManager(this)
        loadThemes()
        adapter = MoodThemesAdapter(themes,
            onDelete = { theme ->
                themes.remove(theme)
                saveThemes()
                adapter.notifyDataSetChanged()
            },
            onDuplicate = { theme ->
                val newTheme = theme.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "${theme.name} (Copy)"
                )
                themes.add(newTheme)
                saveThemes()
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "Mood Theme duplicated", Toast.LENGTH_SHORT).show()
            },
            onSetDefault = { theme ->
                val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
                val current = sp.getString("default_mood_theme_id", null)
                sp.edit(commit = true) {
                    if (current == theme.id) remove("default_mood_theme_id")
                    else putString("default_mood_theme_id", theme.id)
                }
                adapter.notifyDataSetChanged()
                recreate()
            },
            onSetMulti = { theme ->
                val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
                val current = sp.getString("multi_front_mood_theme_id", null)
                sp.edit(commit = true) {
                    if (current == theme.id) remove("multi_front_mood_theme_id")
                    else putString("multi_front_mood_theme_id", theme.id)
                }
                adapter.notifyDataSetChanged()
                recreate()
            },
            onLinkMember = { theme ->
                showMemberLinkDialog(theme)
            },
            onLinkCoFront = { theme ->
                showCoFrontLinkDialog(theme)
            }
        )
        recyclerView.adapter = adapter
        val sharedPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        moodColors[0] = sharedPref.getString("mood_color_1", "#fffa94") ?: "#fffa94"
        moodColors[1] = sharedPref.getString("mood_color_2", "#54bd44") ?: "#54bd44"
        moodColors[2] = sharedPref.getString("mood_color_3", "#8844bd") ?: "#8844bd"
        moodColors[3] = sharedPref.getString("mood_color_4", "#4446bd") ?: "#4446bd"
        moodColors[4] = sharedPref.getString("mood_color_5", "#3a3a47") ?: "#3a3a47"
        updatePreviews()
        findViewById<View>(R.id.cardMood1).setOnClickListener {
            showSimpleColorPicker("Mood 1 (Rad)", moodColors[0]) { moodColors[0] = it; updatePreviews() }
        }
        findViewById<View>(R.id.cardMood2).setOnClickListener {
            showSimpleColorPicker("Mood 2 (Good)", moodColors[1]) { moodColors[1] = it; updatePreviews() }
        }
        findViewById<View>(R.id.cardMood3).setOnClickListener {
            showSimpleColorPicker("Mood 3 (Meh)", moodColors[2]) { moodColors[2] = it; updatePreviews() }
        }
        findViewById<View>(R.id.cardMood4).setOnClickListener {
            showSimpleColorPicker("Mood 4 (Bad)", moodColors[3]) { moodColors[3] = it; updatePreviews() }
        }
        findViewById<View>(R.id.cardMood5).setOnClickListener {
            showSimpleColorPicker("Mood 5 (Awful)", moodColors[4]) { moodColors[4] = it; updatePreviews() }
        }
        findViewById<Button>(R.id.btnSaveAsTheme).setOnClickListener {
            val newTheme = MoodTheme(
                name = "Mood Theme ${themes.size + 1}",
                mood1 = moodColors[0],
                mood2 = moodColors[1],
                mood3 = moodColors[2],
                mood4 = moodColors[3],
                mood5 = moodColors[4]
            )
            themes.add(newTheme)
            saveThemes()
            adapter.notifyDataSetChanged()
            Toast.makeText(this, getString(R.string.mood_theme_saved_toast), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnExportHex).setOnClickListener {
            val hexString = moodColors.joinToString(",")
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("mood_theme_hex", hexString)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Mood Theme Hex copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnImportHex).setOnClickListener {
            showImportHexDialog()
        }
        setupNavigationDrawer()
        ColorHelper.applySettings(this)
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar.setTitleTextColor(ColorHelper.getTextColor(this))
        findViewById<TextView>(R.id.tvTitle).setTextColor(ColorHelper.getTextColor(this))
    }
    private fun loadThemes() {
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val themesJson = sp.getString("saved_mood_themes", "[]") ?: "[]"
        val type = object : TypeToken<MutableList<MoodTheme>>() {}.type
        themes = Gson().fromJson(themesJson, type) ?: mutableListOf()
    }
    private fun saveThemes() {
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        sp.edit(commit = true) {
            putString("saved_mood_themes", Gson().toJson(themes))
        }
    }
    private fun showRenameDialog(theme: MoodTheme) {
        val input = EditText(this).apply {
            setText(theme.name)
            hint = getString(R.string.hint_mood_theme_name)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, 0)
            addView(input)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.edit)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    theme.name = newName
                    saveThemes()
                    adapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let { ColorHelper.styleAlertDialog(it, this) }
    }
    private fun showColorPicker(theme: MoodTheme, moodIndex: Int, onComplete: ((String) -> Unit)? = null) {
        val currentHex = when(moodIndex) {
            1 -> theme.mood1
            2 -> theme.mood2
            3 -> theme.mood3
            4 -> theme.mood4
            else -> theme.mood5
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, 0)
        }
        val dialogPreview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (40 * resources.displayMetrics.density).toInt()).apply {
                bottomMargin = (16 * resources.displayMetrics.density).toInt()
            }
            try { setBackgroundColor(android.graphics.Color.parseColor(currentHex)) } catch (_: Exception) { setBackgroundColor(android.graphics.Color.GRAY) }
        }
        container.addView(dialogPreview)
        val input = EditText(this).apply {
            setText(currentHex)
            hint = "#RRGGBB"
            isSingleLine = true
        }
        container.addView(input)
        val hsv = FloatArray(3)
        try { android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(currentHex), hsv) } catch (_: Exception) {}
        val hueSeek = SeekBar(this).apply { max = 360; progress = hsv[0].toInt() }
        val satSeek = SeekBar(this).apply { max = 100; progress = (hsv[1] * 100).toInt() }
        val valSeek = SeekBar(this).apply { max = 100; progress = (hsv[2] * 100).toInt() }
        val textColor = ColorHelper.getTextColor(this)
        container.addView(TextView(this).apply { text = getString(R.string.label_hue); setPadding(0, 8, 0, 0); setTextColor(textColor) })
        container.addView(hueSeek)
        container.addView(TextView(this).apply { text = getString(R.string.label_saturation); setPadding(0, 8, 0, 0); setTextColor(textColor) })
        container.addView(satSeek)
        container.addView(TextView(this).apply { text = getString(R.string.label_brightness); setPadding(0, 8, 0, 0); setTextColor(textColor) })
        container.addView(valSeek)
        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val hex = s.toString().trim()
                if (hex.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$"))) {
                    try {
                        val color = android.graphics.Color.parseColor(hex)
                        dialogPreview.setBackgroundColor(color)
                        android.graphics.Color.colorToHSV(color, hsv)
                        hueSeek.progress = hsv[0].toInt()
                        satSeek.progress = (hsv[1] * 100).toInt()
                        valSeek.progress = (hsv[2] * 100).toInt()
                    } catch (_: Exception) {}
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        input.addTextChangedListener(watcher)
        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    hsv[0] = hueSeek.progress.toFloat()
                    hsv[1] = satSeek.progress / 100f
                    hsv[2] = valSeek.progress / 100f
                    val color = android.graphics.Color.HSVToColor(hsv)
                    dialogPreview.setBackgroundColor(color)
                    val hex = String.format("#%06X", (0xFFFFFF and color))
                    input.removeTextChangedListener(watcher)
                    input.setText(hex)
                    input.addTextChangedListener(watcher)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }
        hueSeek.setOnSeekBarChangeListener(seekListener)
        satSeek.setOnSeekBarChangeListener(seekListener)
        valSeek.setOnSeekBarChangeListener(seekListener)
        val title = "Mood $moodIndex"
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val newHex = input.text.toString().trim()
                if (newHex.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$"))) {
                    if (onComplete != null) {
                        onComplete(newHex)
                    } else {
                        when(moodIndex) {
                            1 -> theme.mood1 = newHex
                            2 -> theme.mood2 = newHex
                            3 -> theme.mood3 = newHex
                            4 -> theme.mood4 = newHex
                            5 -> theme.mood5 = newHex
                        }
                        saveThemes()
                        adapter.notifyDataSetChanged()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let { ColorHelper.styleAlertDialog(it, this) }
    }
    private fun showMemberLinkDialog(theme: MoodTheme) {
        val people = MemberHelper.loadAllPeople(this)
        val sp = getSharedPreferences("my_app", MODE_PRIVATE)
        val groupsJson = sp.getString("groups_list", "[]")
        val groupsType = object : TypeToken<MutableList<Group>>() {}.type
        val groups: List<Group> = Gson().fromJson(groupsJson, groupsType) ?: emptyList()
        DialogHelper.showMemberSelectionDialog(
            this,
            getString(R.string.label_link_mood_theme),
            people,
            groups,
            people.filter { it.linkedMoodThemeId == theme.id }.map { it.id },
            isMultiSelect = true,
            includeArchived = false
        ) { selectedIds ->
            people.forEach { person ->
                if (selectedIds.contains(person.id)) {
                    person.linkedMoodThemeId = theme.id
                } else if (person.linkedMoodThemeId == theme.id) {
                    person.linkedMoodThemeId = null
                }
            }
            MemberHelper.savePeople(this, people)
            adapter.notifyDataSetChanged()
        }
    }
    private fun showManageThemeLinksDialog(theme: MoodTheme) {
        val spSettings = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val coFrontThemesJson = spSettings.getString("co_front_mood_themes", "[]") ?: "[]"
        val coFrontThemes: MutableList<CoFrontMoodTheme> = Gson().fromJson(coFrontThemesJson, object : TypeToken<MutableList<CoFrontMoodTheme>>() {}.type) ?: mutableListOf()
        val people = MemberHelper.loadAllPeople(this)
        val myCombos = coFrontThemes.filter { it.moodThemeId == theme.id }
        val myPeople = people.filter { it.linkedMoodThemeId == theme.id }
        if (myCombos.isEmpty() && myPeople.isEmpty()) return
        val items = mutableListOf<String>()
        myPeople.forEach { items.add("${getString(R.string.label_person)}: ${it.name}") }
        myCombos.forEach { combo ->
            val names = combo.memberIds.map { id -> people.find { it.id == id }?.name ?: getString(R.string.deleted_member) }.joinToString(" + ")
            items.add("${getString(R.string.stats_co_fronting)}: $names")
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.label_link_mood_theme)
            .setItems(items.toTypedArray()) { _, which ->
                if (which < myPeople.size) {
                    myPeople[which].linkedMoodThemeId = null
                    MemberHelper.savePeople(this, people)
                } else {
                    val comboToRemove = myCombos[which - myPeople.size]
                    coFrontThemes.remove(comboToRemove)
                    spSettings.edit().putString("co_front_mood_themes", Gson().toJson(coFrontThemes)).commit()
                }
                adapter.notifyDataSetChanged()
                android.widget.Toast.makeText(this, getString(R.string.toast_unlinked), android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    private fun showCoFrontLinkDialog(theme: MoodTheme) {
        val sp = getSharedPreferences("my_app", MODE_PRIVATE)
        val peopleJson = sp.getString("people_list", "[]")
        val people: List<Person> = Gson().fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type) ?: emptyList()
        val groupsJson = sp.getString("groups_list", "[]")
        val groups: List<Group> = Gson().fromJson(groupsJson, object : TypeToken<List<Group>>() {}.type) ?: emptyList()
        DialogHelper.showMemberSelectionDialog(
            this,
            getString(R.string.label_link_co_front),
            people,
            groups,
            people.filter { it.isFront }.map { it.id },
            isMultiSelect = true,
            includeArchived = false
        ) { selectedIds ->
            if (selectedIds.size < 2) {
                android.widget.Toast.makeText(this, getString(R.string.error_select_two_members), android.widget.Toast.LENGTH_SHORT).show()
                return@showMemberSelectionDialog
            }
            val settingsSp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
            val coFrontThemesJson = settingsSp.getString("co_front_mood_themes", "[]") ?: "[]"
            val coFrontThemes: MutableList<CoFrontMoodTheme> = Gson().fromJson(coFrontThemesJson, object : TypeToken<MutableList<CoFrontMoodTheme>>() {}.type) ?: mutableListOf()
            val sortedIds = selectedIds.sorted()
            val existingCombo = coFrontThemes.find { it.memberIds.sorted() == sortedIds && it.moodThemeId == theme.id }
            if (existingCombo != null) {
                coFrontThemes.remove(existingCombo)
                android.widget.Toast.makeText(this, getString(R.string.toast_combo_removed), android.widget.Toast.LENGTH_SHORT).show()
            } else {
                coFrontThemes.removeAll { it.memberIds.sorted() == sortedIds }
                coFrontThemes.add(CoFrontMoodTheme(memberIds = sortedIds, moodThemeId = theme.id))
                android.widget.Toast.makeText(this, getString(R.string.toast_combo_linked), android.widget.Toast.LENGTH_SHORT).show()
            }
            settingsSp.edit(commit = true) { putString("co_front_mood_themes", Gson().toJson(coFrontThemes)) }
            adapter.notifyDataSetChanged()
        }
    }
    private fun updatePreviews() {
        findViewById<View>(R.id.mood1Preview).setBackgroundColor(android.graphics.Color.parseColor(moodColors[0]))
        findViewById<View>(R.id.mood2Preview).setBackgroundColor(android.graphics.Color.parseColor(moodColors[1]))
        findViewById<View>(R.id.mood3Preview).setBackgroundColor(android.graphics.Color.parseColor(moodColors[2]))
        findViewById<View>(R.id.mood4Preview).setBackgroundColor(android.graphics.Color.parseColor(moodColors[3]))
        findViewById<View>(R.id.mood5Preview).setBackgroundColor(android.graphics.Color.parseColor(moodColors[4]))
    }
    private fun showSimpleColorPicker(title: String, currentHex: String, onColorChosen: (String) -> Unit) {
        val tempTheme = MoodTheme(name = "", mood1 = currentHex, mood2 = "", mood3 = "", mood4 = "", mood5 = "")
        showColorPicker(tempTheme, 1) { newHex ->
            onColorChosen(newHex)
        }
    }
    private fun showImportHexDialog() {
        val input = EditText(this).apply {
            hint = "#RRGGBB,#RRGGBB,..."
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, 0)
            addView(input)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Import Mood Theme Hex")
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val hexString = input.text.toString().trim()
                val parts = hexString.split(",").map { it.trim() }
                if (parts.size >= 5) {
                    for (i in 0 until 5) moodColors[i] = parts[i]
                    updatePreviews()
                    Toast.makeText(this, "Mood colors imported", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid hex string (needs 5 colors)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let { ColorHelper.styleAlertDialog(it, this) }
    }
    inner class MoodThemesAdapter(
        private val items: List<MoodTheme>,
        private val onDelete: (MoodTheme) -> Unit,
        private val onDuplicate: (MoodTheme) -> Unit,
        private val onSetDefault: (MoodTheme) -> Unit,
        private val onSetMulti: (MoodTheme) -> Unit,
        private val onLinkMember: (MoodTheme) -> Unit,
        private val onLinkCoFront: (MoodTheme) -> Unit
    ) : RecyclerView.Adapter<MoodThemesAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mood_theme, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val theme = items[position]
            val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
            val defaultId = sp.getString("default_mood_theme_id", null)
            val multiId = sp.getString("multi_front_mood_theme_id", null)
            val currentAppBg = ColorHelper.getBgColor(this@MoodThemesActivity)
            (holder.itemView as? com.google.android.material.card.MaterialCardView)?.setCardBackgroundColor(currentAppBg)
            holder.tvThemeName.text = theme.name
            holder.tvThemeName.setOnClickListener { showRenameDialog(theme) }
            holder.viewColor1.setBackgroundColor(theme.mood1.toColorInt())
            holder.viewColor2.setBackgroundColor(theme.mood2.toColorInt())
            holder.viewColor3.setBackgroundColor(theme.mood3.toColorInt())
            holder.viewColor4.setBackgroundColor(theme.mood4.toColorInt())
            holder.viewColor5.setBackgroundColor(theme.mood5.toColorInt())
            holder.tvHex1.text = theme.mood1
            holder.tvHex2.text = theme.mood2
            holder.tvHex3.text = theme.mood3
            holder.tvHex4.text = theme.mood4
            holder.tvHex5.text = theme.mood5
            holder.tvHex1.setOnClickListener { copyToClipboard(theme.mood1) }
            holder.tvHex2.setOnClickListener { copyToClipboard(theme.mood2) }
            holder.tvHex3.setOnClickListener { copyToClipboard(theme.mood3) }
            holder.tvHex4.setOnClickListener { copyToClipboard(theme.mood4) }
            holder.tvHex5.setOnClickListener { copyToClipboard(theme.mood5) }
            holder.viewColor1.setOnClickListener { showColorPicker(theme, 1) }
            holder.viewColor2.setOnClickListener { showColorPicker(theme, 2) }
            holder.viewColor3.setOnClickListener { showColorPicker(theme, 3) }
            holder.viewColor4.setOnClickListener { showColorPicker(theme, 4) }
            holder.viewColor5.setOnClickListener { showColorPicker(theme, 5) }
            holder.btnDuplicate.setOnClickListener { onDuplicate(theme) }
            holder.btnDelete.setOnClickListener { onDelete(theme) }
            holder.btnSetDefault.setOnClickListener { onSetDefault(theme) }
            holder.btnSetMulti.setOnClickListener { onSetMulti(theme) }
            holder.btnLinkMember.setOnClickListener { onLinkMember(theme) }
            holder.btnLinkCoFront.setOnClickListener { onLinkCoFront(theme) }
            val textColor = ColorHelper.getTextColor(this@MoodThemesActivity)
            val btnColor = ColorHelper.getBtnColor(this@MoodThemesActivity)
            holder.tvThemeName.setTextColor(textColor)
            holder.tvHex1.setTextColor(textColor)
            holder.tvHex2.setTextColor(textColor)
            holder.tvHex3.setTextColor(textColor)
            holder.tvHex4.setTextColor(textColor)
            holder.tvHex5.setTextColor(textColor)
            holder.btnSetDefault.setTextColor(if (theme.id == defaultId) btnColor else textColor)
            holder.btnSetMulti.setTextColor(if (theme.id == multiId) btnColor else textColor)
            holder.btnSetDefault.iconTint = android.content.res.ColorStateList.valueOf(if (theme.id == defaultId) btnColor else textColor)
            holder.btnSetMulti.iconTint = android.content.res.ColorStateList.valueOf(if (theme.id == multiId) btnColor else textColor)
            holder.btnLinkMember.iconTint = android.content.res.ColorStateList.valueOf(textColor)
            holder.btnLinkMember.setTextColor(textColor)
            holder.btnLinkCoFront.iconTint = android.content.res.ColorStateList.valueOf(textColor)
            holder.btnLinkCoFront.setTextColor(textColor)
            holder.btnDelete.imageTintList = android.content.res.ColorStateList.valueOf(textColor)
            holder.btnDuplicate.imageTintList = android.content.res.ColorStateList.valueOf(textColor)
            val statusParts = mutableListOf<String>()
            if (theme.id == defaultId) statusParts.add(getString(R.string.theme_none))
            if (theme.id == multiId) statusParts.add(getString(R.string.label_multi))
            val coFrontThemesJson = sp.getString("co_front_mood_themes", "[]") ?: "[]"
            val coFrontThemes: List<CoFrontMoodTheme> = Gson().fromJson(coFrontThemesJson, object : TypeToken<List<CoFrontMoodTheme>>() {}.type) ?: emptyList()
            val comboCount = coFrontThemes.count { it.moodThemeId == theme.id }
            if (comboCount > 0) statusParts.add("${getString(R.string.stats_co_fronting)}: $comboCount")
            holder.tvStatus.visibility = if (statusParts.isNotEmpty()) View.VISIBLE else View.GONE
            holder.tvStatus.text = statusParts.joinToString(" | ")
            holder.tvStatus.setTextColor(textColor)
            val appSp = getSharedPreferences("my_app", MODE_PRIVATE)
            val peopleJson = appSp.getString("people_list", "[]")
            val people: List<Person> = Gson().fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type) ?: emptyList()
            val linkedMembers = people.filter { it.linkedMoodThemeId == theme.id }.map { it.name }
            val linkedCombos = coFrontThemes.filter { it.moodThemeId == theme.id }.map { combo ->
                combo.memberIds.map { id -> people.find { it.id == id }?.name ?: getString(R.string.deleted_member) }.joinToString(" + ")
            }
            val details = mutableListOf<String>()
            if (linkedMembers.isNotEmpty()) details.add("${getString(R.string.label_person)}: ${linkedMembers.joinToString(", ")}")
            if (linkedCombos.isNotEmpty()) details.add("${getString(R.string.stats_co_fronting)}: ${linkedCombos.joinToString(" | ")}")
            if (details.isNotEmpty()) {
                holder.tvLinkedDetails.visibility = View.VISIBLE
                holder.tvLinkedDetails.text = details.joinToString("\n")
                holder.tvLinkedDetails.setTextColor(textColor)
                holder.tvLinkedDetails.alpha = 0.8f
                holder.tvLinkedDetails.setOnClickListener { showManageThemeLinksDialog(theme) }
            } else {
                holder.tvLinkedDetails.visibility = View.GONE
            }
        }
        private fun copyToClipboard(hex: String) {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("hex_color", hex)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this@MoodThemesActivity, getString(R.string.toast_copied, hex), android.widget.Toast.LENGTH_SHORT).show()
        }
        override fun getItemCount(): Int = items.size
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvThemeName: TextView = view.findViewById(R.id.tvThemeName)
            val viewColor1: View = view.findViewById(R.id.viewColor1)
            val viewColor2: View = view.findViewById(R.id.viewColor2)
            val viewColor3: View = view.findViewById(R.id.viewColor3)
            val viewColor4: View = view.findViewById(R.id.viewColor4)
            val viewColor5: View = view.findViewById(R.id.viewColor5)
            val tvHex1: TextView = view.findViewById(R.id.tvHex1)
            val tvHex2: TextView = view.findViewById(R.id.tvHex2)
            val tvHex3: TextView = view.findViewById(R.id.tvHex3)
            val tvHex4: TextView = view.findViewById(R.id.tvHex4)
            val tvHex5: TextView = view.findViewById(R.id.tvHex5)
            val btnDuplicate: ImageView = view.findViewById(R.id.btnDuplicate)
            val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
            val btnSetDefault: MaterialButton = view.findViewById(R.id.btnSetDefault)
            val btnSetMulti: MaterialButton = view.findViewById(R.id.btnSetMulti)
            val btnLinkMember: MaterialButton = view.findViewById(R.id.btnLinkMember)
            val btnLinkCoFront: MaterialButton = view.findViewById(R.id.btnLinkCoFront)
            val tvStatus: TextView = view.findViewById(R.id.tvStatus)
            val tvLinkedDetails: TextView = view.findViewById(R.id.tvLinkedDetails)
        }
    }
}
