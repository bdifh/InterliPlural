package com.interli.plural

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.Collections
import com.interli.plural.BuildConfig

class SettingsActivity : BaseActivity() {

    private var bgHex = "#FFFDF0"
    private var btnHex = "#7D4EBA"
    private var btnTextHex = "#D2B8F5"
    private var frontHex = "#FCF09F"
    private var textHex = "#1A1811"
    
    private val moodColors = mutableListOf("#fffa94", "#54bd44", "#8844bd", "#4446bd", "#3a3a47")

    private var selectedLangCode = "en"
    private var selectedStartPage = "members"

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { performExport(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { performImport(it) }
    }

    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { performPdfExport(it) }
    }

    private val daylioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importFromDaylio(it) }
    }

    private val spJsonLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importFromSpJson(it) }
    }

    private val pkJsonLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importFromPkJson(it) }
    }

    private var initialBg = ""
    private var initialBtn = ""
    private var initialBtnText = ""
    private var initialFront = ""
    private var initialText = ""
    private var initialMoods = listOf<String>()
    private var initialFieldsJson = ""
    private var initialStartPage = ""
    private var initialLang = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val sharedPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        
        bgHex = sharedPref.getString("bg_color", "#FFFDF0") ?: "#FFFDF0"
        btnHex = sharedPref.getString("btn_color", "#7D4EBA") ?: "#7D4EBA"
        btnTextHex = sharedPref.getString("btn_text_color", "#D2B8F5") ?: "#D2B8F5"
        frontHex = sharedPref.getString("front_color", "#FCF09F") ?: "#FCF09F"
        textHex = sharedPref.getString("text_color", "#1A1811") ?: "#1A1811"
        
        moodColors[0] = sharedPref.getString("mood_color_1", "#fffa94") ?: "#fffa94"
        moodColors[1] = sharedPref.getString("mood_color_2", "#54bd44") ?: "#54bd44"
        moodColors[2] = sharedPref.getString("mood_color_3", "#8844bd") ?: "#8844bd"
        moodColors[3] = sharedPref.getString("mood_color_4", "#4446bd") ?: "#4446bd"
        moodColors[4] = sharedPref.getString("mood_color_5", "#3a3a47") ?: "#3a3a47"

        selectedLangCode = sharedPref.getString("app_language", "en") ?: "en"
        val rawStartPage = sharedPref.getString("start_page", "members") ?: "members"
        selectedStartPage = when(rawStartPage.uppercase()) {
            "FRONT" -> "members"
            "STATS" -> "stats"
            "DIARY" -> "diary"
            "TODO" -> "todo"
            "MOOD" -> "mood"
            "CALENDAR" -> "calendar"
            else -> rawStartPage.lowercase()
        }
        
        captureInitialState()
        updatePreviews()

        findViewById<MaterialCardView>(R.id.cardBgColor).setOnClickListener {
            showColorDialog(getString(R.string.hint_bg_color), bgHex, "#FFFDF0") { bgHex = it; updatePreviews() }
        }
        findViewById<MaterialCardView>(R.id.cardBtnColor).setOnClickListener {
            showColorDialog(getString(R.string.hint_btn_color), btnHex, "#7D4EBA") { btnHex = it; updatePreviews() }
        }
        findViewById<MaterialCardView>(R.id.cardBtnTextColor).setOnClickListener {
            showColorDialog(getString(R.string.hint_btn_text_color), btnTextHex, "#D2B8F5") { btnTextHex = it; updatePreviews() }
        }
        findViewById<MaterialCardView>(R.id.cardFrontColor).setOnClickListener {
            showColorDialog(getString(R.string.hint_front_color), frontHex, "#FCF09F") { frontHex = it; updatePreviews() }
        }
        findViewById<MaterialCardView>(R.id.cardTextColor).setOnClickListener {
            showColorDialog(getString(R.string.hint_text_color), textHex, "#1A1811") { textHex = it; updatePreviews() }
        }

        findViewById<MaterialCardView>(R.id.cardMood1).setOnClickListener { showColorDialog("Mood 1 (Rad)", moodColors[0], "#fffa94") { moodColors[0] = it; updatePreviews() } }
        findViewById<MaterialCardView>(R.id.cardMood2).setOnClickListener { showColorDialog("Mood 2 (Good)", moodColors[1], "#54bd44") { moodColors[1] = it; updatePreviews() } }
        findViewById<MaterialCardView>(R.id.cardMood3).setOnClickListener { showColorDialog("Mood 3 (Meh)", moodColors[2], "#8844bd") { moodColors[2] = it; updatePreviews() } }
        findViewById<MaterialCardView>(R.id.cardMood4).setOnClickListener { showColorDialog("Mood 4 (Bad)", moodColors[3], "#4446bd") { moodColors[3] = it; updatePreviews() } }
        findViewById<MaterialCardView>(R.id.cardMood5).setOnClickListener { showColorDialog("Mood 5 (Awful)", moodColors[4], "#3a3a47") { moodColors[4] = it; updatePreviews() } }

        findViewById<Button>(R.id.btnChangeLanguage).setOnClickListener { showLanguageDialog() }
        findViewById<Button>(R.id.btnChangeStartPage).setOnClickListener { showStartPageDialog() }
        findViewById<Button>(R.id.btnManagePages).setOnClickListener { showManagePagesDialog() }
        findViewById<Button>(R.id.btnManageNotifications).setOnClickListener { showNotificationSettingsDialog() }

        findViewById<Button>(R.id.btnManageThemes).setOnClickListener {
            startActivity(android.content.Intent(this, ThemesActivity::class.java))
        }

        findViewById<Button>(R.id.btnThemePriority).setOnClickListener {
            showThemePriorityDialog()
        }

        findViewById<Button>(R.id.btnSaveAsTheme).setOnClickListener {
            val themesJson = sharedPref.getString("saved_themes", "[]") ?: "[]"
            val themes: MutableList<AppTheme> = Gson().fromJson(themesJson, object : TypeToken<MutableList<AppTheme>>() {}.type) ?: mutableListOf()
            val newTheme = AppTheme(
                name = "Theme ${themes.size + 1}",
                bgColor = bgHex,
                btnColor = btnHex,
                btnTextColor = btnTextHex,
                frontColor = frontHex,
                textColor = textHex
            )
            themes.add(newTheme)
            sharedPref.edit(commit = true) { putString("saved_themes", Gson().toJson(themes)) }
            Toast.makeText(this, getString(R.string.theme_saved_toast), Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnManageCustomFields).setOnClickListener {
            startActivity(android.content.Intent(this, CustomFieldsActivity::class.java))
        }

        findViewById<Button>(R.id.btnConfigAutoBackup).setOnClickListener {
            showBackupManagementDialog()
        }

        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            val langChanged = selectedLangCode != initialLang
            sharedPref.edit(commit = true) {
                putString("bg_color", bgHex)
                putString("btn_color", btnHex)
                putString("btn_text_color", btnTextHex)
                putString("front_color", frontHex)
                putString("text_color", textHex)
                putString("mood_color_1", moodColors[0])
                putString("mood_color_2", moodColors[1])
                putString("mood_color_3", moodColors[2])
                putString("mood_color_4", moodColors[3])
                putString("mood_color_5", moodColors[4])
                putString("app_language", selectedLangCode)
                putString("start_page", selectedStartPage)
            }
            BackupHelper.updateAutoBackupSchedule(this)
            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
            if (langChanged) {
                LocaleHelper.applyLocale(this)
                val intent = intent
                finish()
                startActivity(intent)
            } else {
                navigateToStartPage()
            }
        }

        findViewById<Button>(R.id.btnExport).setOnClickListener { exportLauncher.launch("plural_app_backup.json") }
        findViewById<Button>(R.id.btnImport).setOnClickListener { importLauncher.launch(arrayOf("application/json")) }
        findViewById<Button>(R.id.btnOpenImportDialog).setOnClickListener { showImportDialog() }
        
        findViewById<Button>(R.id.btnBulkMove).setOnClickListener { showBulkMoveDialog() }
        findViewById<Button>(R.id.btnViewArchived).setOnClickListener { showArchivedMembersDialog() }
        findViewById<Button>(R.id.btnPdfExport).setOnClickListener { showPdfExportDialog() }

        val btnDeleteData = Button(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = R.id.btnBulkMove + 100 // Temporaire ou on l'ajoute au layout
            text = getString(R.string.action_delete_all, 8)
            setTextColor(Color.RED)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { 
                topMargin = 16.dpToPx()
                bottomMargin = 24.dpToPx()
            }
        }
        var deleteClicks = 0
        btnDeleteData.setOnClickListener {
            deleteClicks++
            if (deleteClicks >= 8) {
                showDeleteAllDataDialog()
                deleteClicks = 0
                btnDeleteData.text = getString(R.string.action_delete_all, 8)
            } else {
                btnDeleteData.text = getString(R.string.action_delete_all, 8 - deleteClicks)
            }
        }
        (findViewById<View>(R.id.btnSaveSettings).parent as ViewGroup).addView(btnDeleteData, (findViewById<View>(R.id.btnSaveSettings).parent as ViewGroup).indexOfChild(findViewById(R.id.btnSaveSettings)))

        setupNavigationDrawer()
        ColorHelper.applySettings(this)

        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        val versionName = BuildConfig.VERSION_DISPLAYED
        tvVersion.text = getString(R.string.version_label, versionName)
        tvVersion.text = getString(R.string.version_label, versionName)
        tvVersion.setTextColor(ColorHelper.getTextColor(this))

        val discordUrl = "https://discord.gg/kkUf7zjTYr"
        val btnColor = ColorHelper.getBtnColor(this)

        val discordListener = View.OnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(discordUrl))
            startActivity(intent)
        }

        findViewById<TextView>(R.id.tvDiscordLink2).apply {
            setOnClickListener(discordListener)
            setTextColor(btnColor)
        }
    }

    private fun captureInitialState() {
        initialBg = bgHex
        initialBtn = btnHex
        initialBtnText = btnTextHex
        initialFront = frontHex
        initialText = textHex
        initialMoods = moodColors.toList()
        initialStartPage = selectedStartPage
        initialLang = selectedLangCode
    }

    private fun updatePreviews() {
        findViewById<View>(R.id.bgPreview).setBackgroundColor(Color.parseColor(bgHex))
        findViewById<View>(R.id.btnPreview).setBackgroundColor(Color.parseColor(btnHex))
        findViewById<View>(R.id.btnTextPreview).setBackgroundColor(Color.parseColor(btnTextHex))
        findViewById<View>(R.id.frontPreview).setBackgroundColor(Color.parseColor(frontHex))
        findViewById<View>(R.id.textPreview).setBackgroundColor(Color.parseColor(textHex))
        
        findViewById<View>(R.id.mood1Preview).setBackgroundColor(Color.parseColor(moodColors[0]))
        findViewById<View>(R.id.mood2Preview).setBackgroundColor(Color.parseColor(moodColors[1]))
        findViewById<View>(R.id.mood3Preview).setBackgroundColor(Color.parseColor(moodColors[2]))
        findViewById<View>(R.id.mood4Preview).setBackgroundColor(Color.parseColor(moodColors[3]))
        findViewById<View>(R.id.mood5Preview).setBackgroundColor(Color.parseColor(moodColors[4]))
    }

    private fun showColorDialog(title: String, currentHex: String, defaultHex: String, onColorChosen: (String) -> Unit) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, 0)
        }

        val dialogPreview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (40 * resources.displayMetrics.density).toInt()).apply {
                bottomMargin = (16 * resources.displayMetrics.density).toInt()
            }
            try { setBackgroundColor(Color.parseColor(currentHex)) } catch (_: Exception) { setBackgroundColor(Color.GRAY) }
        }
        container.addView(dialogPreview)

        val input = EditText(this).apply {
            setText(currentHex)
            hint = "#RRGGBB"
            isSingleLine = true
        }
        container.addView(input)

        val hsv = FloatArray(3)
        try { Color.colorToHSV(Color.parseColor(currentHex), hsv) } catch (_: Exception) {}

        val hueSeek = SeekBar(this).apply { max = 360; progress = hsv[0].toInt() }
        val satSeek = SeekBar(this).apply { max = 100; progress = (hsv[1] * 100).toInt() }
        val valSeek = SeekBar(this).apply { max = 100; progress = (hsv[2] * 100).toInt() }

        container.addView(TextView(this).apply { text = "Hue"; setPadding(0, 8.dpToPx(), 0, 0); setTextColor(ColorHelper.getTextColor(this@SettingsActivity)) })
        container.addView(hueSeek)
        container.addView(TextView(this).apply { text = "Saturation"; setPadding(0, 8.dpToPx(), 0, 0); setTextColor(ColorHelper.getTextColor(this@SettingsActivity)) })
        container.addView(satSeek)
        container.addView(TextView(this).apply { text = "Brightness"; setPadding(0, 8.dpToPx(), 0, 0); setTextColor(ColorHelper.getTextColor(this@SettingsActivity)) })
        container.addView(valSeek)

        val btnReset = Button(this).apply {
            text = getString(R.string.action_reset)
            setOnClickListener { 
                input.setText(defaultHex) 
            }
        }
        container.addView(btnReset)

        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val hex = s.toString().trim()
                if (hex.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$"))) {
                    try {
                        val color = Color.parseColor(hex)
                        dialogPreview.setBackgroundColor(color)
                        Color.colorToHSV(color, hsv)
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
                    val color = Color.HSVToColor(hsv)
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

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ -> onColorChosen(input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showLanguageDialog() {
        val options = arrayOf(getString(R.string.language_english), getString(R.string.language_dutch))
        val codes = arrayOf("en", "nl")
        val currentIdx = codes.indexOf(selectedLangCode).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.language)
            .setSingleChoiceItems(options, currentIdx) { d, i ->
                selectedLangCode = codes[i]
                d.dismiss()
            }
            .show()
            .let { ColorHelper.styleSupportAlertDialog(it, this) }
    }

    private fun showThemePriorityDialog() {
        val sharedPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val currentPriority = sharedPref.getString("theme_priority", "newest") ?: "newest"
        
        val options = arrayOf(getString(R.string.theme_priority_newest), getString(R.string.theme_priority_oldest))
        val codes = arrayOf("newest", "oldest")
        val currentIdx = codes.indexOf(currentPriority).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.theme_priority_title)
            .setSingleChoiceItems(options, currentIdx) { d, i ->
                sharedPref.edit { putString("theme_priority", codes[i]) }
                Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                d.dismiss()
                updatePreviews()
                ColorHelper.applySettings(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let { ColorHelper.styleSupportAlertDialog(it, this) }
    }

    private fun showStartPageDialog() {
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val options = mutableListOf<String>()
        val codes = mutableListOf<String>()

        val pluralMaster = sp.getBoolean("module_fronting_enabled", true)
        val frontSub = sp.getBoolean("sub_front_page", true) && pluralMaster
        val sysmediaSub = sp.getBoolean("module_sysmedia_enabled", true) && pluralMaster

        val moodMaster = sp.getBoolean("module_mood_enabled", true)
        val moodLogSub = sp.getBoolean("sub_mood_log_enabled", true) && moodMaster

        val notesEnabled = sp.getBoolean("module_notes_enabled", true)
        val todoEnabled = sp.getBoolean("module_todo_enabled", true)
        val calendarEnabled = sp.getBoolean("module_calendar_enabled", true)

        if (frontSub) {
            options.add(getString(R.string.front_page))
            codes.add("members")
            options.add(getString(R.string.statistics))
            codes.add("stats")
        }
        
        if (moodLogSub) {
            options.add(getString(R.string.mood_tracker))
            codes.add("mood")
        }
        
        if (notesEnabled) {
            options.add(getString(R.string.diary))
            codes.add("diary")
        }
        
        if (todoEnabled) {
            options.add(getString(R.string.todo))
            codes.add("todo")
        }

        if (calendarEnabled) {
            options.add(getString(R.string.calendar))
            codes.add("calendar")
        }
        
        if (sysmediaSub) {
            options.add(getString(R.string.sysmedia))
            codes.add("sysmedia")
        }
        
        val currentIdx = codes.indexOf(selectedStartPage).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_start_page)
            .setSingleChoiceItems(options.toTypedArray(), currentIdx) { d, i ->
                selectedStartPage = codes[i]
                d.dismiss()
            }
            .show()
            .let { ColorHelper.styleSupportAlertDialog(it, this) }
    }

    private fun showManagePagesDialog() {
        val sharedPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)

        // De indeling van je pagina's in groepen
        val hierarchy = listOf(
            ModuleGroup("module_fronting_enabled", getString(R.string.module_fronting), listOf(
                ModuleSub("sub_front_page", getString(R.string.front_page)),
                ModuleSub("sub_statistics", getString(R.string.statistics)),
                ModuleSub("sub_who_am_i", getString(R.string.who_am_i)),
                ModuleSub("module_sysmedia_enabled", getString(R.string.sysmedia))
            )),
            ModuleGroup("module_notes_enabled", getString(R.string.module_notes)),
            ModuleGroup("module_todo_enabled", getString(R.string.module_todo)),
            ModuleGroup("module_calendar_enabled", getString(R.string.module_calendar)),
            ModuleGroup("module_mood_enabled", getString(R.string.module_mood), listOf(
                ModuleSub("sub_mood_log_enabled", getString(R.string.mood_tracker)),
                ModuleSub("sub_mood_stats_enabled", getString(R.string.mood_stats))
            ))
        )

        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p/2, p, p)
        }
        scrollView.addView(container)

        val allChecks = mutableMapOf<String, CheckBox>()

        hierarchy.forEach { group ->
            val groupCheck = CheckBox(this).apply {
                text = group.label
                isChecked = sharedPref.getBoolean(group.key, true)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dpToPx() }
            }
            container.addView(groupCheck)
            allChecks[group.key] = groupCheck

            if (group.subs.isNotEmpty()) {
                val subContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((32 * resources.displayMetrics.density).toInt(), 0, 0, 0)
                    visibility = if (groupCheck.isChecked) View.VISIBLE else View.GONE
                }
                container.addView(subContainer)

                group.subs.forEach { sub ->
                    val subCheck = CheckBox(this).apply {
                        text = sub.label
                        isChecked = sharedPref.getBoolean(sub.key, true)
                        setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
                    }
                    subContainer.addView(subCheck)
                    allChecks[sub.key] = subCheck
                }

                groupCheck.setOnCheckedChangeListener { _, isChecked ->
                    subContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.action_manage_pages)
            .setView(scrollView)
            .setPositiveButton(R.string.save) { _, _ ->
                sharedPref.edit {
                    allChecks.forEach { (key, cb) ->
                        putBoolean(key, cb.isChecked)
                    }
                }
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let { ColorHelper.styleSupportAlertDialog(it, this) }
    }

    private data class ModuleGroup(val key: String, val label: String, val subs: List<ModuleSub> = emptyList())
    private data class ModuleSub(val key: String, val label: String)

    private data class PageItem(
        val key: String,
        val label: String,
        var isEnabled: Boolean,
        val isHeader: Boolean = false,
        val parentKey: String? = null
    )

    private fun showNotificationSettingsDialog() {
        val sharedPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val frontEnabled = sharedPref.getBoolean("front_notif_enabled", true)
        val todoEnabled = sharedPref.getBoolean("todo_notif_enabled", true)
        val moodEnabled = sharedPref.getBoolean("mood_notif_enabled", false)
        val timesJson = sharedPref.getString("mood_notif_times", "[]") ?: "[]"
        val moodTimes: MutableList<String> = try { 
            Gson().fromJson(timesJson, object : TypeToken<MutableList<String>>() {}.type) ?: mutableListOf()
        } catch (_: Exception) { mutableListOf() }

        val scrollView = androidx.core.widget.NestedScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, p)
        }
        scrollView.addView(layout)

        val swFront = SwitchCompat(this).apply {
            text = getString(R.string.notification_front_toggle)
            isChecked = frontEnabled
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }
        }
        layout.addView(swFront)

        val swTodo = SwitchCompat(this).apply {
            text = getString(R.string.notification_todo_toggle)
            isChecked = todoEnabled
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }
        }
        layout.addView(swTodo)

        val swMood = SwitchCompat(this).apply {
            text = getString(R.string.notification_mood_toggle)
            isChecked = moodEnabled
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        layout.addView(swMood)

        val timesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (moodEnabled) View.VISIBLE else View.GONE
        }
        layout.addView(timesContainer)

        fun refreshTimes() {
            timesContainer.removeAllViews()
            
            val label = TextView(this).apply {
                text = getString(R.string.notification_times_label)
                setPadding(0, 16, 0, 8)
                alpha = 0.7f
            }
            timesContainer.addView(label)

            moodTimes.sorted().forEach { time ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 4, 0, 4)
                }
                
                val tv = TextView(this).apply {
                    text = time
                    textSize = 18f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
                }
                row.addView(tv)

                val btnRemove = Button(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                    text = getString(R.string.action_remove_notification)
                    setTextColor(Color.RED)
                    setOnClickListener {
                        moodTimes.remove(time)
                        refreshTimes()
                    }
                }
                row.addView(btnRemove)
                timesContainer.addView(row)
            }

            val btnAdd = Button(this).apply {
                text = getString(R.string.action_add_notification)
                setOnClickListener {
                    val cal = Calendar.getInstance()
                    android.app.TimePickerDialog(this@SettingsActivity, { _, h, m ->
                        val newTime = String.format("%02d:%02d", h, m)
                        if (!moodTimes.contains(newTime)) {
                            moodTimes.add(newTime)
                            refreshTimes()
                        }
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                }
            }
            timesContainer.addView(btnAdd)
        }

        refreshTimes()

        swMood.setOnCheckedChangeListener { _, isChecked ->
            timesContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_section_notifications)
            .setView(scrollView)
            .setPositiveButton(R.string.save) { _, _ ->
                sharedPref.edit {
                    putBoolean("front_notif_enabled", swFront.isChecked)
                    putBoolean("todo_notif_enabled", swTodo.isChecked)
                    putBoolean("mood_notif_enabled", swMood.isChecked)
                    putString("mood_notif_times", Gson().toJson(moodTimes))
                }
                NotificationReceiver.cancelAllMoodAlarms(this)
                if (swMood.isChecked) NotificationReceiver.rescheduleAlarms(this)
                
                Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun performExport(uri: Uri) {
        val json = BackupHelper.createBackupJson(this)
        contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        Toast.makeText(this, getString(R.string.backup_saved), Toast.LENGTH_SHORT).show()
    }

    private var pendingPdfSelections: BooleanArray? = null
    private var pendingPdfStart: Long? = null
    private var pendingPdfEnd: Long? = null

    private fun showPdfExportDialog() {
        val items = arrayOf(
            getString(R.string.export_front_data),
            getString(R.string.export_mood_data),
            getString(R.string.export_notes_data),
            getString(R.string.export_todo_data)
        )
        val selected = booleanArrayOf(true, true, true, true)
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 8.dpToPx())
        }

        val tvPeriodLabel = TextView(this).apply {
            text = getString(R.string.stats_period)
            setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
            setPadding(0, 0, 0, 8.dpToPx())
        }
        container.addView(tvPeriodLabel)

        val spinner = Spinner(this)
        val periods = arrayOf(
            getString(R.string.period_all_time),
            getString(R.string.period_custom)
        )
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, periods)
        container.addView(spinner)

        val dateContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 8.dpToPx(), 0, 0)
        }
        container.addView(dateContainer)

        var tempStart: Long? = null
        var tempEnd: Long? = null
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val btnStart = Button(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.start_date)
            setOnClickListener {
                val cal = Calendar.getInstance()
                android.app.DatePickerDialog(this@SettingsActivity, { _, y, m, d ->
                    cal.set(y, m, d, 0, 0, 0)
                    tempStart = cal.timeInMillis
                    text = "${getString(R.string.start_date)}: ${sdf.format(cal.time)}"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        dateContainer.addView(btnStart)

        val btnEnd = Button(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.end_date)
            setOnClickListener {
                val cal = Calendar.getInstance()
                android.app.DatePickerDialog(this@SettingsActivity, { _, y, m, d ->
                    cal.set(y, m, d, 23, 59, 59)
                    tempEnd = cal.timeInMillis
                    text = "${getString(R.string.end_date)}: ${sdf.format(cal.time)}"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        dateContainer.addView(btnEnd)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                dateContainer.visibility = if (position == 1) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.export_pdf_title)
            .setMultiChoiceItems(items, selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            .setView(container)
            .setPositiveButton(R.string.action_export) { _, _ ->
                pendingPdfSelections = selected
                if (spinner.selectedItemPosition == 1) {
                    pendingPdfStart = tempStart
                    pendingPdfEnd = tempEnd
                } else {
                    pendingPdfStart = null
                    pendingPdfEnd = null
                }
                pdfLauncher.launch("plural_export.pdf")
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun performPdfExport(uri: Uri) {
        val selections = pendingPdfSelections ?: booleanArrayOf(true, true, true, true)
        PdfExportHelper.exportFullDataToPdf(this, uri, selections, pendingPdfStart, pendingPdfEnd)
        pendingPdfSelections = null
        pendingPdfStart = null
        pendingPdfEnd = null
    }

    private fun performImport(uri: Uri) {
        contentResolver.openInputStream(uri)?.use { stream ->
            BackupHelper.restoreBackup(this, stream)
            Toast.makeText(this, getString(R.string.backup_imported), Toast.LENGTH_LONG).show()
            recreate()
        }
    }

    private fun showImportDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_3rd_party_import, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.action_3rd_party_import)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .create()

        val sharedPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val etSpToken = view.findViewById<EditText>(R.id.etSpToken)
        val etPkToken = view.findViewById<EditText>(R.id.etPkToken)
        
        etSpToken.setText(sharedPref.getString("sp_token", ""))
        etPkToken.setText(sharedPref.getString("pk_token", ""))

        view.findViewById<Button>(R.id.btnImportSp).setOnClickListener {
            val token = etSpToken.text.toString()
            sharedPref.edit().putString("sp_token", token).apply()
            importFromSimplyPlural(token)
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnImportSpJson).setOnClickListener {
            spJsonLauncher.launch("application/json")
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnImportPk).setOnClickListener {
            val token = etPkToken.text.toString()
            sharedPref.edit().putString("pk_token", token).apply()
            importFromPluralKit(token)
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnImportPkJson).setOnClickListener {
            pkJsonLauncher.launch("application/json")
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnImportDaylio).setOnClickListener {
            daylioLauncher.launch("*/*")
            dialog.dismiss()
        }

        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        etSpToken.setTextColor(ColorHelper.getTextColor(this))
        etPkToken.setTextColor(ColorHelper.getTextColor(this))
    }

    private fun importFromSimplyPlural(tokenInput: String) {
        val token = tokenInput.trim()
        if (token.isBlank()) {
            Toast.makeText(this, "Please enter a token first", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val meUrl = URL("https://api.apparyllis.com/v1/me")
                val meConn = meUrl.openConnection() as HttpURLConnection
                meConn.requestMethod = "GET"
                meConn.setRequestProperty("Authorization", token)
                meConn.setRequestProperty("User-Agent", "InterliPlural-Android")

                if (meConn.responseCode == 200) {
                    val meJson = meConn.inputStream.bufferedReader().use { it.readText() }
                    val meData = Gson().fromJson<Map<String, Any>>(meJson, object : TypeToken<Map<String, Any>>() {}.type)
                    val userId = meData["id"] as? String

                    if (userId != null) {
                        val url = URL("https://api.apparyllis.com/v1/members/$userId")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.setRequestProperty("Authorization", token)
                        conn.setRequestProperty("User-Agent", "InterliPlural-Android")

                        if (conn.responseCode == 200) {
                            val json = conn.inputStream.bufferedReader().use { it.readText() }
                            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                            val spMembers: List<Map<String, Any>> = Gson().fromJson(json, type)
                            processSpMembers(spMembers)
                        } else {
                            runOnUiThread { Toast.makeText(this, "SP Error: ${conn.responseCode}", Toast.LENGTH_LONG).show() }
                        }
                    }
                } else {
                    runOnUiThread { Toast.makeText(this, "SP Auth Error: ${meConn.responseCode}", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Connection error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun importFromPluralKit(token: String) {
        if (token.isBlank()) return
        Thread {
            try {
                val url = URL("https://api.pluralkit.me/v2/systems/@me/members")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", token)
                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().use { it.readText() }
                    val pkMembers = Gson().fromJson<List<Map<String, Any>>>(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
                    runOnUiThread {
                        val people = loadPeopleList()
                        pkMembers.forEach { pkm ->
                            val pkId = pkm["id"] as? String ?: ""
                            val name = pkm["name"] as? String ?: "PK Member"
                            val desc = pkm["description"] as? String ?: ""
                            val avatar = pkm["avatar_url"] as? String

                            val colorHex = pkm["color"] as? String
                            var profileColor = -6934396
                            if (!colorHex.isNullOrBlank()) {
                                try {
                                    val formattedHex = if (colorHex.startsWith("#")) colorHex else "#$colorHex"
                                    profileColor = android.graphics.Color.parseColor(formattedHex)
                                } catch (_: Exception) {}
                            }

                            if (pkId.isNotEmpty() && people.none { it.manualId == pkId }) {
                                val initialHandle = name.replace(" ", "_").lowercase().replace(Regex("[^a-z0-9_]"), "")

                                people.add(Person(
                                    name = name,
                                    manualId = pkId,
                                    profileInfo = desc,
                                    profilePictureUri = avatar,
                                    profileColor = profileColor,
                                    sysmediaProfile = SysmediaProfile(handle = initialHandle)
                                ))
                            }
                        }
                        MemberHelper.savePeople(this@SettingsActivity, people)
                        Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread { Toast.makeText(this, "PK Error: ${conn.responseCode}", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "PK Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun importFromSpJson(uri: Uri) {
        Thread {
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@Thread
                val root = Gson().fromJson<Any>(json, object : TypeToken<Any>() {}.type)
                
                val membersToProcess = mutableListOf<Map<String, Any>>()

                if (root is List<*>) {
                    @Suppress("UNCHECKED_CAST")
                    (root as List<Map<String, Any>>).forEach { membersToProcess.add(it) }
                } else if (root is Map<*, *>) {
                    val members = root["members"]
                    if (members is List<*>) {
                        @Suppress("UNCHECKED_CAST")
                        (members as List<Map<String, Any>>).forEach { membersToProcess.add(it) }
                    } else if (members is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        (members as Map<String, Map<String, Any>>).forEach { (key, value) ->
                            val mutableMember = value.toMutableMap()
                            if (!mutableMember.containsKey("id")) mutableMember["id"] = key as String
                            membersToProcess.add(mutableMember)
                        }
                    } else if (root.containsKey("content") && (root.containsKey("id") || root.containsKey("uid"))) {
                        @Suppress("UNCHECKED_CAST")
                        membersToProcess.add(root as Map<String, Any>)
                    }
                }

                if (membersToProcess.isEmpty()) {
                    try {
                        val directList = Gson().fromJson<List<Map<String, Any>>>(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
                        processSpMembers(directList)
                        return@Thread
                    } catch (_: Exception) {}
                }

                processSpMembers(membersToProcess)
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "SP JSON Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun processSpMembers(spMembers: List<Map<String, Any>>) {
        runOnUiThread {
            val people = loadPeopleList()
            var count = 0
            spMembers.forEach { spm ->
                val id = spm["id"] as? String ?: spm["uid"] as? String ?: ""
                val content = spm["content"] as? Map<*, *>

                val name = content?.get("name") as? String ?: spm["name"] as? String ?: "SP Member"
                val desc = content?.get("desc") as? String ?: spm["description"] as? String ?: ""
                val avatar = content?.get("avatarUrl") as? String ?: spm["avatar_url"] as? String

                val colorHex = content?.get("color") as? String ?: spm["color"] as? String
                var profileColor = -6934396
                if (!colorHex.isNullOrBlank()) {
                    try {
                        val formattedHex = if (colorHex.startsWith("#")) colorHex else "#$colorHex"
                        profileColor = android.graphics.Color.parseColor(formattedHex)
                    } catch (_: Exception) {}
                }

                if (id.isNotEmpty() && people.none { it.manualId == id }) {
                    val initialHandle = name.replace(" ", "_").lowercase().replace(Regex("[^a-z0-9_]"), "")
                    people.add(Person(
                        name = name,
                        manualId = id,
                        profileInfo = desc,
                        profilePictureUri = avatar,
                        profileColor = profileColor,
                        sysmediaProfile = SysmediaProfile(handle = initialHandle)
                    ))
                    count++
                }
            }
            MemberHelper.savePeople(this, people)
            Toast.makeText(this, "Imported $count members from Simply Plural", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFromPkJson(uri: Uri) {
        Thread {
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@Thread
                val root = Gson().fromJson<Map<String, Any>>(json, object : TypeToken<Map<String, Any>>() {}.type)
                
                @Suppress("UNCHECKED_CAST")
                val membersList = root["members"] as? List<Map<String, Any>>

                if (membersList == null) {
                    try {
                        val directList = Gson().fromJson<List<Map<String, Any>>>(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
                        processPkMembers(directList)
                        return@Thread
                    } catch (_: Exception) {}
                    runOnUiThread { Toast.makeText(this, "No members found in PluralKit JSON", Toast.LENGTH_SHORT).show() }
                    return@Thread
                }

                processPkMembers(membersList)
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "PK JSON Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun processPkMembers(pkMembers: List<Map<String, Any>>) {
        runOnUiThread {
            val people = loadPeopleList()
            var count = 0
            pkMembers.forEach { pkm ->
                val id = pkm["id"] as? String ?: ""
                val name = pkm["name"] as? String ?: "PK Member"
                val desc = pkm["description"] as? String ?: ""
                val avatar = pkm["avatar_url"] as? String
                
                // Kleur extraheren (PluralKit gebruikt hex codes)
                val colorHex = pkm["color"] as? String
                var profileColor = -6934396
                if (!colorHex.isNullOrBlank()) {
                    try {
                        val formattedHex = if (colorHex.startsWith("#")) colorHex else "#$colorHex"
                        profileColor = android.graphics.Color.parseColor(formattedHex)
                    } catch (_: Exception) {}
                }

                if (id.isNotEmpty() && people.none { it.manualId == id }) {
                    val initialHandle = name.replace(" ", "_").lowercase().replace(Regex("[^a-z0-9_]"), "")
                    people.add(Person(
                        name = name, 
                        manualId = id, 
                        profileInfo = desc, 
                        profilePictureUri = avatar,
                        profileColor = profileColor,
                        sysmediaProfile = SysmediaProfile(handle = initialHandle)
                    ))
                    count++
                }
            }
            MemberHelper.savePeople(this, people)
            Toast.makeText(this, "Imported $count members from PluralKit JSON", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFromDaylio(uri: Uri) {
        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@Thread
                val reader = BufferedReader(InputStreamReader(inputStream))
                val header = reader.readLine()
                
                val delimiter = if (header?.contains(";") == true) ";" else ","
                
                val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                val entriesJson = sharedPref.getString("mood_entries", "[]") ?: "[]"
                val entries: MutableList<MoodActivity.MoodEntry> = Gson().fromJson(entriesJson, object : TypeToken<MutableList<MoodActivity.MoodEntry>>() {}.type) ?: mutableListOf()
                
                var importedCount = 0
                reader.forEachLine { line ->
                    val parts = line.split(delimiter).map { it.trim().removeSurrounding("\"") }
                    if (parts.size >= 5) {
                        val fullDate = parts[0]
                        val time = parts[3]
                        val moodLabel = parts[4]
                        val note = if (parts.size > 6) parts[6] else ""
                        
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val timestamp = try { sdf.parse("$fullDate $time")?.time ?: 0L } catch (_: Exception) { 0L }
                        
                        if (timestamp != 0L && entries.none { it.timestamp == timestamp }) {
                            val (score, emoji, color) = when (moodLabel.lowercase()) {
                                "rad" -> Triple(5, "🤩", Color.parseColor(moodColors[0]))
                                "good" -> Triple(4, "😊", Color.parseColor(moodColors[1]))
                                "meh" -> Triple(3, "😐", Color.parseColor(moodColors[2]))
                                "bad" -> Triple(2, "😟", Color.parseColor(moodColors[3]))
                                "awful" -> Triple(1, "😢", Color.parseColor(moodColors[4]))
                                else -> Triple(3, "😐", Color.parseColor(moodColors[2]))
                            }
                            
                            entries.add(MoodActivity.MoodEntry(
                                timestamp = timestamp,
                                moodEmoji = emoji,
                                moodRotation = (score - 3) * 15f,
                                moodLabel = moodLabel,
                                moodColor = color,
                                note = note
                            ))
                            importedCount++
                        }
                    }
                }
                
                runOnUiThread {
                    sharedPref.edit().putString("mood_entries", Gson().toJson(entries)).apply()
                    Toast.makeText(this, "Imported $importedCount Daylio entries", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Daylio Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun showBulkMoveDialog() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val groupsJson = sharedPref.getString("groups_list", "[]") ?: "[]"
        val groups: List<Group> = Gson().fromJson(groupsJson, object : TypeToken<List<Group>>() {}.type)
        
        if (groups.isEmpty()) {
            Toast.makeText(this, getString(R.string.create_groups_first), Toast.LENGTH_SHORT).show()
            return
        }

        val names = groups.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.label_select_group))
            .setItems(names) { _, which -> showMemberSelectionForBulkMove(groups[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let { ColorHelper.styleSupportAlertDialog(it, this) }
    }

    private fun showMemberSelectionForBulkMove(targetGroup: Group) {
        val people = loadPeopleList()
        val groups = loadGroupsList()
        val initiallySelectedIds = people.filter { it.safeGroupIds.contains(targetGroup.id) }.map { it.id }
        val filteredPeople = people.filter { !it.isArchived && !it.isSysmediaOnly }

        DialogHelper.showMemberSelectionDialog(this, getString(R.string.select_groups), filteredPeople, groups, initiallySelectedIds) { newList ->
            filteredPeople.forEach { person ->
                if (newList.contains(person.id)) {
                    if (person.groupIds == null) person.groupIds = mutableListOf()
                    if (!person.groupIds!!.contains(targetGroup.id)) person.groupIds!!.add(targetGroup.id)
                } else {
                    person.groupIds?.remove(targetGroup.id)
                }
            }
            val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
            MemberHelper.savePeople(this, people)
            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showArchivedMembersDialog() {
        DialogHelper.showArchivedMembersDialog(this) {
        }
    }

    private fun showBackupManagementDialog() {
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val enabled = sp.getBoolean("auto_backup_enabled", false)
        val frequency = sp.getString("auto_backup_frequency", "daily") ?: "daily"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, p)
        }

        val swEnable = SwitchCompat(this).apply {
            text = getString(R.string.enable_auto_backup)
            isChecked = enabled
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16.dpToPx() }
        }
        layout.addView(swEnable)

        val freqContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (enabled) View.VISIBLE else View.GONE
        }
        layout.addView(freqContainer)

        freqContainer.addView(TextView(this).apply { 
            text = getString(R.string.backup_frequency)
            alpha = 0.7f
            setPadding(0, 8.dpToPx(), 0, 4.dpToPx()) 
            setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
        })
        
        val rgFreq = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val rbDaily = RadioButton(this).apply { 
            text = getString(R.string.frequency_daily)
            id = View.generateViewId()
            setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
        }
        val rbWeekly = RadioButton(this).apply { 
            text = getString(R.string.frequency_weekly)
            id = View.generateViewId()
            setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
        }
        val rbMonthly = RadioButton(this).apply { 
            text = getString(R.string.frequency_monthly)
            id = View.generateViewId()
            setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
        }
        rgFreq.addView(rbDaily)
        rgFreq.addView(rbWeekly)
        rgFreq.addView(rbMonthly)
        when (frequency) {
            "weekly" -> rbWeekly.isChecked = true
            "monthly" -> rbMonthly.isChecked = true
            else -> rbDaily.isChecked = true
        }
        freqContainer.addView(rgFreq)

        swEnable.setOnCheckedChangeListener { _, isChecked ->
            freqContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        freqContainer.addView(TextView(this).apply {
            text = getString(R.string.backup_location)
            alpha = 0.7f
            setPadding(0, 16.dpToPx(), 0, 4.dpToPx())
            setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
        })
        
        val currentFolder = java.io.File(getExternalFilesDir(null), "backups")
        val tvPath = TextView(this).apply {
            text = currentFolder.absolutePath
            textSize = 12f
            alpha = 0.6f
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            background = android.graphics.drawable.ColorDrawable(0x11000000)
            setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
        }
        freqContainer.addView(tvPath)

        val backupsLabel = TextView(this).apply {
            text = getString(R.string.action_restore_auto)
            setPadding(0, 24.dpToPx(), 0, 8.dpToPx())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
        }
        layout.addView(backupsLabel)

        val backups = BackupHelper.getAutoBackups(this)
        if (backups.isEmpty()) {
            layout.addView(TextView(this).apply { 
                text = getString(R.string.no_backups_found)
                alpha = 0.5f 
                setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
            })
        } else {
            backups.forEach { file ->
                val btn = Button(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                    text = file.name
                    isAllCaps = false
                    setTextColor(ColorHelper.getBtnColor(this@SettingsActivity))
                    setOnClickListener {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Restore backup?")
                            .setMessage("Current data will be replaced.")
                            .setPositiveButton("Restore") { _, _ ->
                                BackupHelper.restoreBackup(this@SettingsActivity, file.inputStream())
                                Toast.makeText(this@SettingsActivity, R.string.backup_imported, Toast.LENGTH_SHORT).show()
                                recreate()
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                            .let { ColorHelper.styleSupportAlertDialog(it, this@SettingsActivity) }
                    }
                }
                layout.addView(btn)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.auto_backup_settings)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val newFreq = when {
                    rbWeekly.isChecked -> "weekly"
                    rbMonthly.isChecked -> "monthly"
                    else -> "daily"
                }
                sp.edit {
                    putBoolean("auto_backup_enabled", swEnable.isChecked)
                    putString("auto_backup_frequency", newFreq)
                }
                BackupHelper.updateAutoBackupSchedule(this)
                Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun loadPeopleList(): MutableList<Person> {
        return MemberHelper.loadAllPeople(this)
    }
    
    private fun loadGroupsList(): List<Group> {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = sharedPref.getString("groups_list", "[]") ?: "[]"
        return Gson().fromJson(json, object : TypeToken<List<Group>>() {}.type)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun showDeleteAllDataDialog() {
        val labels = arrayOf(
            getString(R.string.delete_members),
            getString(R.string.delete_history),
            getString(R.string.delete_mood),
            getString(R.string.delete_notes),
            getString(R.string.delete_todo),
            getString(R.string.delete_settings)
        )
        val checked = BooleanArray(labels.size) { false }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.delete_all_warning_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(getString(R.string.delete) + " (8)", null)
            .setNegativeButton(R.string.cancel, null)
            .create()
            
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        
        var confirmClicks = 0
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (checked.none { it }) {
                Toast.makeText(this, "Select at least one item", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            confirmClicks++
            if (confirmClicks >= 8) {
                performBulkDelete(checked)
                dialog.dismiss()
            } else {
                (it as? Button)?.text = "${getString(R.string.delete)} (${8 - confirmClicks})"
            }
        }
    }

    private fun performBulkDelete(checked: BooleanArray) {
        val spApp = getSharedPreferences("my_app", MODE_PRIVATE)
        val spSettings = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        
        spApp.edit().apply {
            if (checked[0]) { // Members & Groups
                remove("people_list")
                remove("sysmedia_people_list")
                remove("groups_list")
            }
            if (checked[1]) { // History
                remove("sessions_list")
            }
            if (checked[2]) { // Mood
                remove("mood_entries")
            }
            if (checked[3]) { // Notes
                remove("diary_notes")
                remove("sysmedia_posts")
                remove("sysmedia_notifications")
                remove("sysmedia_dms")
                remove("sysmedia_chat_groups")
            }
            if (checked[4]) { // Todo
                remove("todo_lists")
                remove("todo_bundles")
            }
        }.apply()

        if (checked[5]) { // settings
            spSettings.edit().clear().apply()
        }

        Toast.makeText(this, "Selected data deleted", Toast.LENGTH_SHORT).show()
        recreate()
    }
}
