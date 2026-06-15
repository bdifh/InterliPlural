package com.interli.plural

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

class ThemesActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ThemesAdapter
    private var themes: MutableList<AppTheme> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_themes)

        recyclerView = findViewById(R.id.recyclerViewThemes)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadThemes()

        adapter = ThemesAdapter(themes,
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
                Toast.makeText(this, "Theme duplicated", Toast.LENGTH_SHORT).show()
            },
            onSetDefault = { theme ->
                val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
                val current = sp.getString("default_theme_id", null)
                sp.edit(commit = true) {
                    if (current == theme.id) remove("default_theme_id")
                    else putString("default_theme_id", theme.id)
                }
                adapter.notifyDataSetChanged()
                recreate()
            },
            onSetMulti = { theme ->
                val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
                val current = sp.getString("multi_front_theme_id", null)
                sp.edit(commit = true) {
                    if (current == theme.id) remove("multi_front_theme_id")
                    else putString("multi_front_theme_id", theme.id)
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

        setupNavigationDrawer()
        ColorHelper.applySettings(this)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar.setTitleTextColor(ColorHelper.getTextColor(this))
        findViewById<TextView>(R.id.tvTitle).setTextColor(ColorHelper.getTextColor(this))
    }

    private fun loadThemes() {
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val themesJson = sp.getString("saved_themes", "[]") ?: "[]"
        val type = object : TypeToken<MutableList<AppTheme>>() {}.type
        themes = Gson().fromJson(themesJson, type) ?: mutableListOf()
    }

    private fun saveThemes() {
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        sp.edit(commit = true) {
            putString("saved_themes", Gson().toJson(themes))
        }
    }

    private fun showRenameDialog(theme: AppTheme) {
        val input = EditText(this).apply {
            setText(theme.name)
            hint = getString(R.string.hint_theme_name)
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
                    ColorHelper.applySettings(this@ThemesActivity)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun showColorPicker(theme: AppTheme, colorIndex: Int) {
        val currentHex = when(colorIndex) {
            1 -> theme.bgColor
            2 -> theme.btnColor
            3 -> theme.btnTextColor
            4 -> theme.frontColor
            else -> theme.textColor
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
        container.addView(TextView(this).apply { text = "Hue"; setPadding(0, 8, 0, 0); setTextColor(textColor) })
        container.addView(hueSeek)
        container.addView(TextView(this).apply { text = "Saturation"; setPadding(0, 8, 0, 0); setTextColor(textColor) })
        container.addView(satSeek)
        container.addView(TextView(this).apply { text = "Brightness"; setPadding(0, 8, 0, 0); setTextColor(textColor) })
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

        val titleRes = when(colorIndex) {
            1 -> R.string.hint_bg_color
            2 -> R.string.hint_btn_color
            3 -> R.string.hint_btn_text_color
            4 -> R.string.hint_front_color
            5 -> R.string.hint_text_color
            else -> R.string.choose_color_hex
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val newHex = input.text.toString().trim()
                if (newHex.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$"))) {
                    when(colorIndex) {
                        1 -> theme.bgColor = newHex
                        2 -> theme.btnColor = newHex
                        3 -> theme.btnTextColor = newHex
                        4 -> theme.frontColor = newHex
                        5 -> theme.textColor = newHex
                    }
                    saveThemes()
                    adapter.notifyDataSetChanged()
                    ColorHelper.applySettings(this@ThemesActivity)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun showMemberLinkDialog(theme: AppTheme) {
        val people = MemberHelper.loadAllPeople(this)

        val sp = getSharedPreferences("my_app", MODE_PRIVATE)
        val groupsJson = sp.getString("groups_list", "[]")
        val groupsType = object : TypeToken<MutableList<Group>>() {}.type
        val groups: List<Group> = Gson().fromJson(groupsJson, groupsType) ?: emptyList()

        DialogHelper.showMemberSelectionDialog(
            this,
            getString(R.string.label_link_theme),
            people,
            groups,
            people.filter { it.linkedThemeId == theme.id }.map { it.id },
            isMultiSelect = true,
            includeArchived = false
        ) { selectedIds ->
            people.forEach { person ->
                if (selectedIds.contains(person.id)) {
                    person.linkedThemeId = theme.id
                } else if (person.linkedThemeId == theme.id) {
                    person.linkedThemeId = null
                }
            }
            MemberHelper.savePeople(this, people)
            adapter.notifyDataSetChanged()
        }
    }

    private fun showManageThemeLinksDialog(theme: AppTheme) {
        val spSettings = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val coFrontThemesJson = spSettings.getString("co_front_themes", "[]") ?: "[]"
        val coFrontThemes: MutableList<CoFrontTheme> = Gson().fromJson(coFrontThemesJson, object : TypeToken<MutableList<CoFrontTheme>>() {}.type) ?: mutableListOf()

        val people = MemberHelper.loadAllPeople(this)

        val myCombos = coFrontThemes.filter { it.themeId == theme.id }
        val myPeople = people.filter { it.linkedThemeId == theme.id }

        if (myCombos.isEmpty() && myPeople.isEmpty()) return

        val items = mutableListOf<String>()
        myPeople.forEach { items.add("${getString(R.string.label_person)}: ${it.name}") }
        myCombos.forEach { combo ->
            val names = combo.memberIds.map { id -> people.find { it.id == id }?.name ?: getString(R.string.deleted_member) }.joinToString(" + ")
            items.add("${getString(R.string.stats_co_fronting)}: $names")
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.label_link_theme)
            .setItems(items.toTypedArray()) { _, which ->
                if (which < myPeople.size) {
                    myPeople[which].linkedThemeId = null
                    MemberHelper.savePeople(this, people)
                } else {
                    val comboToRemove = myCombos[which - myPeople.size]
                    coFrontThemes.remove(comboToRemove)
                    spSettings.edit().putString("co_front_themes", Gson().toJson(coFrontThemes)).commit()
                }
                adapter.notifyDataSetChanged()
                android.widget.Toast.makeText(this, getString(R.string.toast_unlinked), android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCoFrontLinkDialog(theme: AppTheme) {
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
            val coFrontThemesJson = settingsSp.getString("co_front_themes", "[]") ?: "[]"
            val coFrontThemes: MutableList<CoFrontTheme> = Gson().fromJson(coFrontThemesJson, object : TypeToken<MutableList<CoFrontTheme>>() {}.type) ?: mutableListOf()

            val sortedIds = selectedIds.sorted()
            val existingCombo = coFrontThemes.find { it.memberIds.sorted() == sortedIds && it.themeId == theme.id }

            if (existingCombo != null) {
                coFrontThemes.remove(existingCombo)
                android.widget.Toast.makeText(this, getString(R.string.toast_combo_removed), android.widget.Toast.LENGTH_SHORT).show()
            } else {
                coFrontThemes.removeAll { it.memberIds.sorted() == sortedIds }
                coFrontThemes.add(CoFrontTheme(memberIds = sortedIds, themeId = theme.id))
                android.widget.Toast.makeText(this, getString(R.string.toast_combo_linked), android.widget.Toast.LENGTH_SHORT).show()
            }

            settingsSp.edit(commit = true) { putString("co_front_themes", Gson().toJson(coFrontThemes)) }
            adapter.notifyDataSetChanged()
        }
    }

    inner class ThemesAdapter(
        private val items: List<AppTheme>,
        private val onDelete: (AppTheme) -> Unit,
        private val onDuplicate: (AppTheme) -> Unit,
        private val onSetDefault: (AppTheme) -> Unit,
        private val onSetMulti: (AppTheme) -> Unit,
        private val onLinkMember: (AppTheme) -> Unit,
        private val onLinkCoFront: (AppTheme) -> Unit
    ) : RecyclerView.Adapter<ThemesAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_theme, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val theme = items[position]
            val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
            val defaultId = sp.getString("default_theme_id", null)
            val multiId = sp.getString("multi_front_theme_id", null)

            val currentAppBg = ColorHelper.getBgColor(this@ThemesActivity)
            (holder.itemView as? com.google.android.material.card.MaterialCardView)?.setCardBackgroundColor(currentAppBg)

            holder.tvThemeName.text = theme.name
            holder.tvThemeName.setOnClickListener { showRenameDialog(theme) }

            holder.viewColor1.setBackgroundColor(theme.bgColor.toColorInt())
            holder.viewColor2.setBackgroundColor(theme.btnColor.toColorInt())
            holder.viewColor3.setBackgroundColor(theme.btnTextColor.toColorInt())
            holder.viewColor4.setBackgroundColor(theme.frontColor.toColorInt())
            holder.viewColor5.setBackgroundColor(theme.textColor.toColorInt())

            holder.tvHex1.text = theme.bgColor
            holder.tvHex2.text = theme.btnColor
            holder.tvHex3.text = theme.btnTextColor
            holder.tvHex4.text = theme.frontColor
            holder.tvHex5.text = theme.textColor

            holder.tvHex1.setOnClickListener { copyToClipboard(theme.bgColor) }
            holder.tvHex2.setOnClickListener { copyToClipboard(theme.btnColor) }
            holder.tvHex3.setOnClickListener { copyToClipboard(theme.btnTextColor) }
            holder.tvHex4.setOnClickListener { copyToClipboard(theme.frontColor) }
            holder.tvHex5.setOnClickListener { copyToClipboard(theme.textColor) }

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

            val textColor = ColorHelper.getTextColor(this@ThemesActivity)
            val btnColor = ColorHelper.getBtnColor(this@ThemesActivity)

            holder.tvThemeName.setTextColor(textColor)
            holder.tvHex1.setTextColor(textColor)
            holder.tvHex2.setTextColor(textColor)
            holder.tvHex3.setTextColor(textColor)
            holder.tvHex4.setTextColor(textColor)
            holder.tvHex5.setTextColor(textColor)

            holder.btnSetDefault.setTextColor(if (theme.id == defaultId) btnColor else textColor)
            holder.btnSetMulti.setTextColor(if (theme.id == multiId) btnColor else textColor)
            holder.btnSetDefault.iconTint = android.content.res.ColorStateList.valueOf(if (theme.id == defaultId) btnColor else textColor)
            holder.btnSetMulti.iconTint = android.content.res.ColorStateList.valueOf(if (theme.id == defaultId) btnColor else textColor)
            holder.btnLinkMember.iconTint = android.content.res.ColorStateList.valueOf(textColor)
            holder.btnLinkMember.setTextColor(textColor)
            holder.btnLinkCoFront.iconTint = android.content.res.ColorStateList.valueOf(textColor)
            holder.btnLinkCoFront.setTextColor(textColor)
            holder.btnDelete.imageTintList = android.content.res.ColorStateList.valueOf(textColor)
            holder.btnDuplicate.imageTintList = android.content.res.ColorStateList.valueOf(textColor)

            val statusParts = mutableListOf<String>()
            if (theme.id == defaultId) statusParts.add(getString(R.string.theme_none))
            if (theme.id == multiId) statusParts.add(getString(R.string.label_multi))

            val coFrontThemesJson = sp.getString("co_front_themes", "[]") ?: "[]"
            val coFrontThemes: List<CoFrontTheme> = Gson().fromJson(coFrontThemesJson, object : TypeToken<List<CoFrontTheme>>() {}.type) ?: emptyList()
            val comboCount = coFrontThemes.count { it.themeId == theme.id }
            if (comboCount > 0) statusParts.add("${getString(R.string.stats_co_fronting)}: $comboCount")

            holder.tvStatus.visibility = if (statusParts.isNotEmpty()) View.VISIBLE else View.GONE
            holder.tvStatus.text = statusParts.joinToString(" | ")
            holder.tvStatus.setTextColor(textColor)

            val appSp = getSharedPreferences("my_app", MODE_PRIVATE)
            val peopleJson = appSp.getString("people_list", "[]")
            val people: List<Person> = Gson().fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type) ?: emptyList()

            val linkedMembers = people.filter { it.linkedThemeId == theme.id }.map { it.name }
            val linkedCombos = coFrontThemes.filter { it.themeId == theme.id }.map { combo ->
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
            android.widget.Toast.makeText(this@ThemesActivity, getString(R.string.toast_copied, hex), android.widget.Toast.LENGTH_SHORT).show()
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
