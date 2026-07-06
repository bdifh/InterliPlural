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

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            uri?.let { performExport(it) }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { performImport(it) }
        }

    private val pdfLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            uri?.let { performPdfExport(it) }
        }

    private val daylioLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { importFromDaylio(it) }
        }

    private val spJsonLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { importFromSpJson(it) }
        }

    private val pkJsonLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { importFromPkJson(it) }
        }

    private val spAvatarZipLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { importSpAvatars(it) }
        }

    private val psJsonLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { importFromPsJson(it) }
        }

    private val hmJsonLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { importFromHivemindJson(it) }
        }

    private val ampersandPickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleAmpersandFile(it) }
        }

    private val pluralStarLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { importFromPluralStar(it) }
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
        selectedStartPage = when (rawStartPage.uppercase()) {
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

        findViewById<MaterialCardView>(R.id.cardMood1).setOnClickListener {
            showColorDialog(
                "Mood 1 (Rad)",
                moodColors[0],
                "#fffa94"
            ) { moodColors[0] = it; updatePreviews() }
        }
        findViewById<MaterialCardView>(R.id.cardMood2).setOnClickListener {
            showColorDialog(
                "Mood 2 (Good)",
                moodColors[1],
                "#54bd44"
            ) { moodColors[1] = it; updatePreviews() }
        }
        findViewById<MaterialCardView>(R.id.cardMood3).setOnClickListener {
            showColorDialog(
                "Mood 3 (Meh)",
                moodColors[2],
                "#8844bd"
            ) { moodColors[2] = it; updatePreviews() }
        }
        findViewById<MaterialCardView>(R.id.cardMood4).setOnClickListener {
            showColorDialog(
                "Mood 4 (Bad)",
                moodColors[3],
                "#4446bd"
            ) { moodColors[3] = it; updatePreviews() }
        }
        findViewById<MaterialCardView>(R.id.cardMood5).setOnClickListener {
            showColorDialog(
                "Mood 5 (Awful)",
                moodColors[4],
                "#3a3a47"
            ) { moodColors[4] = it; updatePreviews() }
        }

        findViewById<Button>(R.id.btnChangeLanguage).setOnClickListener { showLanguageDialog() }
        findViewById<Button>(R.id.btnChangeFontSize).setOnClickListener { showFontSizeDialog() }
        findViewById<Button>(R.id.btnChangeStartPage).setOnClickListener { showStartPageDialog() }
        findViewById<Button>(R.id.btnManagePages).setOnClickListener { showManagePagesDialog() }
        findViewById<Button>(R.id.btnManageNotifications).setOnClickListener { showNotificationSettingsDialog() }

        findViewById<Button>(R.id.btnManageThemes).setOnClickListener {
            startActivity(android.content.Intent(this, ThemesActivity::class.java))
        }

        findViewById<Button>(R.id.btnManageMoodThemes).setOnClickListener {
            startActivity(android.content.Intent(this, MoodThemesActivity::class.java))
        }

        findViewById<Button>(R.id.btnExportMoodHex).setOnClickListener {
            val hexString = moodColors.joinToString(",")
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("mood_hex", hexString)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Mood Hex copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnImportMoodHex).setOnClickListener {
            showImportMoodHexDialog()
        }

        findViewById<Button>(R.id.btnThemePriority).setOnClickListener {
            showThemePriorityDialog()
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

            getSharedPreferences("my_app", MODE_PRIVATE).edit(commit = true) {
                putString("mood_color_1", moodColors[0])
                putString("mood_color_2", moodColors[1])
                putString("mood_color_3", moodColors[2])
                putString("mood_color_4", moodColors[3])
                putString("mood_color_5", moodColors[4])
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

        findViewById<Button>(R.id.btnExport).setOnClickListener { exportLauncher.launch("plural_app_backup.zip") }
        findViewById<Button>(R.id.btnImport).setOnClickListener {
            importLauncher.launch(
                arrayOf(
                    "application/json",
                    "application/zip"
                )
            )
        }
        findViewById<Button>(R.id.btnOpenImportDialog).setOnClickListener { showImportDialog() }

        findViewById<Button>(R.id.btnBulkMove).setOnClickListener { showBulkMoveDialog() }
        findViewById<Button>(R.id.btnViewArchived).setOnClickListener { showArchivedMembersDialog() }
        findViewById<Button>(R.id.btnPdfExport).setOnClickListener { showPdfExportDialog() }
        
        findViewById<Button>(R.id.btnRestoreDeleted).setOnClickListener {
            restoreClicks++
            if (restoreClicks >= 5) {
                restoreClicks = 0
                showRestoreMemberDialog()
            } else {
                Toast.makeText(this, getString(R.string.restore_archived_deleted_clicks, 5 - restoreClicks), Toast.LENGTH_SHORT).show()
            }
        }

        val mainMenu = findViewById<LinearLayout>(R.id.settingsMainMenu)
        val layoutGroup = findViewById<LinearLayout>(R.id.layoutSettingsGroup)
        val appSettingsGroup = findViewById<LinearLayout>(R.id.appSettingsGroup)
        val backupGroup = findViewById<LinearLayout>(R.id.backupSettingsGroup)
        val membersGroup = findViewById<LinearLayout>(R.id.memberSettingsGroup)
        val topAppBar =
            findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        val topAppBarTitle = topAppBar.getChildAt(0) as? TextView

        fun showGroup(group: LinearLayout, title: String) {
            mainMenu.visibility = View.GONE
            layoutGroup.visibility = View.GONE
            appSettingsGroup.visibility = View.GONE
            backupGroup.visibility = View.GONE
            membersGroup.visibility = View.GONE

            group.visibility = View.VISIBLE
            topAppBarTitle?.text = title
            topAppBar.setNavigationIcon(android.R.drawable.ic_menu_revert)
            topAppBar.setNavigationOnClickListener {
                showGroup(mainMenu, getString(R.string.settings))
                topAppBar.setNavigationIcon(android.R.drawable.ic_menu_sort_by_size)
                topAppBar.setNavigationOnClickListener {
                    findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout).openDrawer(
                        android.view.Gravity.LEFT
                    )
                }
            }
        }

        findViewById<Button>(R.id.btnCategoryLayout).setOnClickListener {
            showGroup(
                layoutGroup,
                getString(R.string.settings_category_layout)
            )
        }
        findViewById<Button>(R.id.btnCategoryAppSettings).setOnClickListener {
            showGroup(
                appSettingsGroup,
                getString(R.string.settings_category_app)
            )
        }
        findViewById<Button>(R.id.btnCategoryBackup).setOnClickListener {
            showGroup(
                backupGroup,
                getString(R.string.settings_category_backup)
            )
        }
        findViewById<Button>(R.id.btnCategoryMembers).setOnClickListener {
            showGroup(
                membersGroup,
                getString(R.string.settings_category_members)
            )
        }

        val btnDeleteData = Button(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            id = View.generateViewId()
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
        findViewById<LinearLayout>(R.id.deleteDataContainer).addView(btnDeleteData)

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
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(discordUrl)
            )
            startActivity(intent)
        }

        findViewById<TextView>(R.id.tvDiscordLink2).apply {
            setOnClickListener(discordListener)
            setTextColor(btnColor)
        }
    }

    override fun onBackPressed() {
        val mainMenu = findViewById<LinearLayout>(R.id.settingsMainMenu)
        if (mainMenu.visibility == View.GONE) {
            val layoutGroup = findViewById<LinearLayout>(R.id.layoutSettingsGroup)
            val appSettingsGroup = findViewById<LinearLayout>(R.id.appSettingsGroup)
            val backupGroup = findViewById<LinearLayout>(R.id.backupSettingsGroup)
            val membersGroup = findViewById<LinearLayout>(R.id.memberSettingsGroup)
            val topAppBar =
                findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
            val topAppBarTitle = topAppBar.getChildAt(0) as? TextView

            mainMenu.visibility = View.VISIBLE
            layoutGroup.visibility = View.GONE
            appSettingsGroup.visibility = View.GONE
            backupGroup.visibility = View.GONE
            membersGroup.visibility = View.GONE

            topAppBarTitle?.text = getString(R.string.settings)
            topAppBar.setNavigationIcon(android.R.drawable.ic_menu_sort_by_size)
            topAppBar.setNavigationOnClickListener {
                findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout).openDrawer(
                    android.view.Gravity.LEFT
                )
            }
        } else {
            super.onBackPressed()
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
        findViewById<View>(R.id.mood1Preview).setBackgroundColor(Color.parseColor(moodColors[0]))
        findViewById<View>(R.id.mood2Preview).setBackgroundColor(Color.parseColor(moodColors[1]))
        findViewById<View>(R.id.mood3Preview).setBackgroundColor(Color.parseColor(moodColors[2]))
        findViewById<View>(R.id.mood4Preview).setBackgroundColor(Color.parseColor(moodColors[3]))
        findViewById<View>(R.id.mood5Preview).setBackgroundColor(Color.parseColor(moodColors[4]))
    }

    private fun showColorDialog(
        title: String,
        currentHex: String,
        defaultHex: String,
        onColorChosen: (String) -> Unit
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, 0)
        }

        val dialogPreview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (40 * resources.displayMetrics.density).toInt()
            ).apply {
                bottomMargin = (16 * resources.displayMetrics.density).toInt()
            }
            try {
                setBackgroundColor(Color.parseColor(currentHex))
            } catch (_: Exception) {
                setBackgroundColor(Color.GRAY)
            }
        }
        container.addView(dialogPreview)

        val input = EditText(this).apply {
            setText(currentHex)
            hint = "#RRGGBB"
            isSingleLine = true
        }
        container.addView(input)

        val hsv = FloatArray(3)
        try {
            Color.colorToHSV(Color.parseColor(currentHex), hsv)
        } catch (_: Exception) {
        }

        val hueSeek = SeekBar(this).apply { max = 360; progress = hsv[0].toInt() }
        val satSeek = SeekBar(this).apply { max = 100; progress = (hsv[1] * 100).toInt() }
        val valSeek = SeekBar(this).apply { max = 100; progress = (hsv[2] * 100).toInt() }

        container.addView(TextView(this).apply {
            text = "Hue"; setPadding(
            0,
            8.dpToPx(),
            0,
            0
        ); setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
        })
        container.addView(hueSeek)
        container.addView(TextView(this).apply {
            text = "Saturation"; setPadding(
            0,
            8.dpToPx(),
            0,
            0
        ); setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
        })
        container.addView(satSeek)
        container.addView(TextView(this).apply {
            text = "Brightness"; setPadding(
            0,
            8.dpToPx(),
            0,
            0
        ); setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
        })
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
                    } catch (_: Exception) {
                    }
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
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun showLanguageDialog() {
        val options =
            arrayOf(getString(R.string.language_english), getString(R.string.language_dutch))
        val codes = arrayOf("en", "nl")
        val currentIdx = codes.indexOf(selectedLangCode).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.language)
            .setSingleChoiceItems(options, currentIdx) { d, i ->
                selectedLangCode = codes[i]
                d.dismiss()
            }
            .show()
            .let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun showThemePriorityDialog() {
        val sharedPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val currentPriority = sharedPref.getString("theme_priority", "newest") ?: "newest"

        val options = arrayOf(
            getString(R.string.theme_priority_newest),
            getString(R.string.theme_priority_oldest)
        )
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
            .let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun showStartPageDialog() {
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val options = mutableListOf<String>()
        val codes = mutableListOf<String>()

        val pluralMaster = sp.getBoolean("module_fronting_enabled", true)
        val frontSub = sp.getBoolean("sub_front_page", true) && pluralMaster
        val relationsSub = sp.getBoolean("sub_relations_enabled", true) && pluralMaster
        val sysmediaSub = sp.getBoolean("module_sysmedia_enabled", true) && pluralMaster

        val moodMaster = sp.getBoolean("module_mood_enabled", true)
        val moodLogSub = sp.getBoolean("sub_mood_log_enabled", true) && moodMaster
        val moodInsightsSub = sp.getBoolean("sub_mood_insights", true) && moodMaster

        val notesEnabled = sp.getBoolean("module_notes_enabled", true)
        val todoEnabled = sp.getBoolean("module_todo_enabled", true)
        val calendarEnabled = sp.getBoolean("module_calendar_enabled", true)

        if (frontSub) {
            options.add(getString(R.string.front_page))
            codes.add("members")
            options.add(getString(R.string.statistics))
            codes.add("stats")
        }

        if (relationsSub) {
            options.add(getString(R.string.module_relations))
            codes.add("relations")
        }

        if (moodLogSub) {
            options.add(getString(R.string.mood_tracker))
            codes.add("mood")
        }

        if (moodInsightsSub) {
            options.add(getString(R.string.mood_insights))
            codes.add("mood_insights")
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
            .let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun showManagePagesDialog() {
        val sharedPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)

        val hierarchy = listOf(
            ModuleGroup(
                "module_fronting_enabled", getString(R.string.module_fronting), listOf(
                    ModuleSub("sub_front_page", getString(R.string.front_page)),
                    ModuleSub("sub_statistics", getString(R.string.statistics)),
                    ModuleSub("sub_who_am_i", getString(R.string.who_am_i)),
                    ModuleSub("sub_relations_enabled", getString(R.string.module_relations)),
                    ModuleSub("module_sysmedia_enabled", getString(R.string.sysmedia))
                )
            ),
            ModuleGroup("module_notes_enabled", getString(R.string.module_notes)),
            ModuleGroup("module_todo_enabled", getString(R.string.module_todo)),
            ModuleGroup("module_calendar_enabled", getString(R.string.module_calendar)),
            ModuleGroup(
                "module_mood_enabled", getString(R.string.module_mood), listOf(
                    ModuleSub("sub_mood_log_enabled", getString(R.string.mood_tracker)),
                    ModuleSub("sub_mood_stats_enabled", getString(R.string.mood_stats)),
                    ModuleSub("sub_mood_insights", getString(R.string.mood_insights))
                )
            )
        )

        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, p)
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
            .let { ColorHelper.styleAlertDialog(it, this) }
    }

    private data class ModuleGroup(
        val key: String,
        val label: String,
        val subs: List<ModuleSub> = emptyList()
    )

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
        val sysmediaNotisEnabled = sharedPref.getBoolean("sysmedia_notif_enabled", true)
        val sysmediaDmsEnabled = sharedPref.getBoolean("sysmedia_dm_notif_enabled", true)
        val moodEnabled = sharedPref.getBoolean("mood_notif_enabled", false)
        val timesJson = sharedPref.getString("mood_notif_times", "[]") ?: "[]"
        val moodTimes: MutableList<String> = try {
            Gson().fromJson(timesJson, object : TypeToken<MutableList<String>>() {}.type)
                ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }

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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        }
        layout.addView(swFront)

        val swTodo = SwitchCompat(this).apply {
            text = getString(R.string.notification_todo_toggle)
            isChecked = todoEnabled
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        }
        layout.addView(swTodo)

        val swSysmediaNotis = SwitchCompat(this).apply {
            text = getString(R.string.notification_sysmedia_notif_toggle)
            isChecked = sysmediaNotisEnabled
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        }
        layout.addView(swSysmediaNotis)

        val swSysmediaDms = SwitchCompat(this).apply {
            text = getString(R.string.notification_sysmedia_dm_toggle)
            isChecked = sysmediaDmsEnabled
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        }
        layout.addView(swSysmediaDms)

        val swMood = SwitchCompat(this).apply {
            text = getString(R.string.notification_mood_toggle)
            isChecked = moodEnabled
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
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
                    layoutParams =
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
                }
                row.addView(tv)

                val btnRemove =
                    Button(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
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
                    putBoolean("sysmedia_notif_enabled", swSysmediaNotis.isChecked)
                    putBoolean("sysmedia_dm_notif_enabled", swSysmediaDms.isChecked)
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
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun performExport(uri: Uri) {
        contentResolver.openOutputStream(uri)?.use {
            BackupHelper.createBackupZip(this, it)
        }
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
            getString(R.string.export_todo_data),
            getString(R.string.module_relations)
        )
        val selected = booleanArrayOf(true, true, true, true, true)

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

        val btnStart = Button(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = getString(R.string.start_date)
            setOnClickListener {
                val cal = Calendar.getInstance()
                android.app.DatePickerDialog(this@SettingsActivity, { _, y, m, d ->
                    cal.set(y, m, d, 0, 0, 0)
                    tempStart = cal.timeInMillis
                    text = "${getString(R.string.start_date)}: ${sdf.format(cal.time)}"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                    .show()
            }
        }
        dateContainer.addView(btnStart)

        val btnEnd = Button(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = getString(R.string.end_date)
            setOnClickListener {
                val cal = Calendar.getInstance()
                android.app.DatePickerDialog(this@SettingsActivity, { _, y, m, d ->
                    cal.set(y, m, d, 23, 59, 59)
                    tempEnd = cal.timeInMillis
                    text = "${getString(R.string.end_date)}: ${sdf.format(cal.time)}"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                    .show()
            }
        }
        dateContainer.addView(btnEnd)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
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
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun performPdfExport(uri: Uri) {
        val selections = pendingPdfSelections ?: booleanArrayOf(true, true, true, true, true)
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
            spJsonLauncher.launch("*/*")
            dialog.dismiss()
        }

        view.findViewById<Button>(R.id.btnImportSpAvatars)?.setOnClickListener {
            spAvatarZipLauncher.launch("*/*")
            dialog.dismiss()
        }

        view.findViewById<Button>(R.id.btnImportHm).setOnClickListener {
            hmJsonLauncher.launch("*/*")
            dialog.dismiss()
        }

        view.findViewById<Button>(R.id.btnImportPk).setOnClickListener {
            val token = etPkToken.text.toString()
            sharedPref.edit().putString("pk_token", token).apply()
            importFromPluralKit(token)
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnImportPkJson).setOnClickListener {
            pkJsonLauncher.launch("*/*")
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnImportDaylio).setOnClickListener {
            daylioLauncher.launch("*/*")
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnImportPs).setOnClickListener {
            psJsonLauncher.launch("*/*")
            dialog.dismiss()
        }

        view.findViewById<Button>(R.id.btnImportAmpersand).setOnClickListener {
            ampersandPickerLauncher.launch("*/*")
            dialog.dismiss()
        }

        view.findViewById<Button>(R.id.btnImportPluralStar)?.setOnClickListener {
            pluralStarLauncher.launch("*/*")
            dialog.dismiss()
        }

        dialog.show()

        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
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
                    val meData = Gson().fromJson<Map<String, Any>>(
                        meJson,
                        object : TypeToken<Map<String, Any>>() {}.type
                    )
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
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    "SP Error: ${conn.responseCode}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "SP Auth Error: ${meConn.responseCode}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Connection error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
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
                    val pkMembers = Gson().fromJson<List<Map<String, Any>>>(
                        json,
                        object : TypeToken<List<Map<String, Any>>>() {}.type
                    )
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
                                    val formattedHex =
                                        if (colorHex.startsWith("#")) colorHex else "#$colorHex"
                                    profileColor = android.graphics.Color.parseColor(formattedHex)
                                } catch (_: Exception) {
                                }
                            }

                            if (pkId.isNotEmpty() && people.none { it.manualId == pkId }) {
                                val initialHandle = name.replace(" ", "_").lowercase()
                                    .replace(Regex("[^a-z0-9_]"), "")

                                people.add(
                                    Person(
                                        name = name,
                                        manualId = pkId,
                                        profileInfo = desc,
                                        profilePictureUri = avatar,
                                        profileColor = profileColor,
                                        sysmediaProfile = SysmediaProfile(handle = initialHandle)
                                    )
                                )
                            }
                        }
                        MemberHelper.savePeople(this@SettingsActivity, people)
                        Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "PK Error: ${conn.responseCode}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "PK Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun importFromSpJson(uri: Uri) {
        Thread {
            try {
                val json =
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: return@Thread
                val root = Gson().fromJson<Any>(json, object : TypeToken<Any>() {}.type)

                val membersToProcess = mutableListOf<Map<String, Any>>()

                fun extractMembers(obj: Any?) {
                    when (obj) {
                        is List<*> -> {
                            obj.forEach { item ->
                                if (item is Map<*, *>) {
                                    @Suppress("UNCHECKED_CAST")
                                    val m = item as Map<String, Any>
                                    if (m.containsKey("content") || m.containsKey("name")) {
                                        membersToProcess.add(m)
                                    } else {
                                        extractMembers(item)
                                    }
                                }
                            }
                        }

                        is Map<*, *> -> {
                            val members = obj["members"]
                            if (members != null) {
                                extractMembers(members)
                            } else {
                                @Suppress("UNCHECKED_CAST")
                                val map = obj as Map<String, Any>
                                if (map.containsKey("content") || map.containsKey("name")) {
                                    membersToProcess.add(map)
                                } else {
                                    map.values.forEach { extractMembers(it) }
                                }
                            }
                        }
                    }
                }

                extractMembers(root)

                if (membersToProcess.isEmpty()) {
                    try {
                        val directList = Gson().fromJson<List<Map<String, Any>>>(
                            json,
                            object : TypeToken<List<Map<String, Any>>>() {}.type
                        )
                        processSpMembers(directList)
                        return@Thread
                    } catch (_: Exception) {
                    }
                }

                processSpMembers(membersToProcess)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "SP JSON Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun importFromPsJson(uri: Uri) {
        Thread {
            try {
                val json =
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: return@Thread
                val root = Gson().fromJson<Map<String, Any>>(
                    json,
                    object : TypeToken<Map<String, Any>>() {}.type
                )

                runOnUiThread {
                    processPsImport(root)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "PluralSpace Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun processPsImport(root: Map<String, Any>) {
        val people = loadPeopleList()
        val psAlters = root["tid_alters"] as? List<Map<String, Any>> ?: emptyList()
        val psTracker = root["tid_tracker"] as? List<Map<String, Any>> ?: emptyList()
        val psFronting = root["tid_fronting"] as? List<Map<String, Any>> ?: emptyList()

        var altersCount = 0
        var moodCount = 0
        var sessionsCount = 0

        psAlters.forEach { alter ->
            val psId = alter["id"] as? String ?: ""
            val name = alter["name"] as? String ?: "Unknown"
            if (psId.isNotEmpty() && people.none { it.manualId == psId }) {
                val pronouns = alter["pronouns"] as? String ?: ""
                val desc = alter["description"] as? String ?: ""
                val colorHex = alter["color"] as? String
                val avatarBase64 = alter["avatarImg"] as? String

                var profileColor = -6934396
                if (!colorHex.isNullOrBlank()) {
                    try {
                        profileColor = android.graphics.Color.parseColor(colorHex)
                    } catch (_: Exception) {
                    }
                }

                val person = Person(
                    name = name,
                    manualId = psId,
                    profileInfo = if (pronouns.isNotEmpty()) "Pronouns: $pronouns\n$desc" else desc,
                    profileColor = profileColor,
                    sysmediaProfile = SysmediaProfile(
                        handle = name.replace(" ", "_").lowercase().replace(Regex("[^a-z0-9_]"), "")
                    )
                )

                if (avatarBase64?.startsWith("data:image") == true) {
                    try {
                        val base64Data = avatarBase64.substringAfter("base64,")
                        val imageBytes =
                            android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        val file =
                            File(filesDir, "profile_${person.id}_${System.currentTimeMillis()}.jpg")
                        file.writeBytes(imageBytes)
                        person.profilePictureUri = Uri.fromFile(file).toString()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                people.add(person)
                altersCount++
            }
        }
        MemberHelper.savePeople(this, people)

        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val entriesJson = sharedPref.getString("mood_entries", "[]") ?: "[]"
        val entries: MutableList<MoodActivity.MoodEntry> = Gson().fromJson(
            entriesJson,
            object : TypeToken<MutableList<MoodActivity.MoodEntry>>() {}.type
        ) ?: mutableListOf()

        psTracker.forEach { log ->
            val ts = (log["ts"] as? Number)?.toLong() ?: 0L
            if (ts > 0 && entries.none { it.timestamp == ts }) {
                val psMood = (log["mood"] as? String)?.lowercase() ?: ""
                val psAlterId = log["alterId"] as? String
                val note = log["note"] as? String ?: ""

                val (label, emoji, color) = when (psMood) {
                    "increíble", "muy bien" -> Triple("Rad", "🤩", Color.parseColor(moodColors[0]))
                    "bien" -> Triple("Good", "😊", Color.parseColor(moodColors[1]))
                    "neutro", "normal" -> Triple("Meh", "😐", Color.parseColor(moodColors[2]))
                    "mal" -> Triple("Bad", "😟", Color.parseColor(moodColors[3]))
                    "muy mal", "horrible" -> Triple("Awful", "😢", Color.parseColor(moodColors[4]))
                    else -> Triple("Meh", "😐", Color.parseColor(moodColors[2]))
                }

                val targetMemberId = people.find { it.manualId == psAlterId }?.id

                entries.add(
                    MoodActivity.MoodEntry(
                        timestamp = ts,
                        moodEmoji = emoji,
                        moodRotation = (if (label == "Rad") 2f else if (label == "Good") 1f else 0f) * 15f,
                        moodLabel = label.lowercase(),
                        moodColor = color,
                        note = note,
                        memberIds = if (targetMemberId != null) listOf(targetMemberId) else emptyList()
                    )
                )
                moodCount++
            }
        }
        sharedPref.edit().putString("mood_entries", Gson().toJson(entries)).apply()

        val sessionsJson = sharedPref.getString("sessions_list", "[]") ?: "[]"
        val sessions: MutableList<FrontSession> =
            Gson().fromJson(sessionsJson, object : TypeToken<MutableList<FrontSession>>() {}.type)
                ?: mutableListOf()

        psFronting.forEach { f ->
            val start = (f["start"] as? Number)?.toLong() ?: 0L
            if (start > 0 && sessions.none { it.startTime == start }) {
                val psAlterId = f["alterId"] as? String
                val end = (f["end"] as? Number)?.toLong()
                val note = f["note"] as? String
                val person = people.find { it.manualId == psAlterId }

                if (person != null) {
                    sessions.add(
                        FrontSession(
                            personName = person.name,
                            startTime = start,
                            endTime = end,
                            personId = person.id,
                            note = note
                        )
                    )
                    sessionsCount++
                }
            }
        }
        sharedPref.edit().putString("sessions_list", Gson().toJson(sessions)).apply()

        Toast.makeText(
            this,
            "Imported $altersCount alters, $moodCount mood logs and $sessionsCount sessions",
            Toast.LENGTH_LONG
        ).show()
        recreate()
    }

    private fun importFromHivemindJson(uri: Uri) {
        Thread {
            try {
                val json =
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: return@Thread
                val root = Gson().fromJson<Map<String, Any>>(
                    json,
                    object : TypeToken<Map<String, Any>>() {}.type
                )

                runOnUiThread {
                    processHivemindImport(root)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Hivemind Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun processHivemindImport(root: Map<String, Any>) {
        val people = loadPeopleList()
        val hmAlters = root["alters"] as? List<Map<String, Any>> ?: emptyList()
        val hmSubsystems = root["subsystems"] as? List<Map<String, Any>> ?: emptyList()
        val hmFronting = root["front_entries"] as? List<Map<String, Any>> ?: emptyList()
        val hmJournal = root["journal_entries"] as? List<Map<String, Any>> ?: emptyList()

        var altersCount = 0
        var groupsCount = 0
        var sessionsCount = 0
        var journalCount = 0

        hmAlters.forEach { alter ->
            val hmId = (alter["alter_id"] as? Number)?.toInt()?.toString() ?: ""
            val name = alter["name"] as? String ?: "Unknown"

            if (hmId.isNotEmpty() && people.none { it.manualId == hmId }) {
                val profile = alter["profile"] as? String ?: ""
                val colorHex = alter["color"] as? String
                val avatarData = alter["avatar_data"] as? String // Base64

                var profileColor = -6934396
                if (!colorHex.isNullOrBlank()) {
                    try {
                        profileColor = android.graphics.Color.parseColor(colorHex)
                    } catch (_: Exception) {
                    }
                }

                val person = Person(
                    name = name,
                    manualId = hmId,
                    profileInfo = profile,
                    profileColor = profileColor,
                    sysmediaProfile = SysmediaProfile(
                        handle = name.replace(" ", "_").lowercase().replace(Regex("[^a-z0-9_]"), "")
                    )
                )

                if (!avatarData.isNullOrBlank()) {
                    try {
                        val imageBytes =
                            android.util.Base64.decode(avatarData, android.util.Base64.DEFAULT)
                        val file =
                            File(filesDir, "profile_${person.id}_${System.currentTimeMillis()}.jpg")
                        file.writeBytes(imageBytes)
                        person.profilePictureUri = Uri.fromFile(file).toString()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                people.add(person)
                altersCount++
            }
        }
        MemberHelper.savePeople(this, people)

        val groups = loadGroupsList().toMutableList()
        hmSubsystems.forEach { sub ->
            val subName = sub["name"] as? String ?: "Unknown Subsystem"
            if (groups.none { it.name == subName }) {
                groups.add(Group(name = subName))
                groupsCount++
            }
        }
        val spApp = getSharedPreferences("my_app", MODE_PRIVATE)
        spApp.edit().putString("groups_list", Gson().toJson(groups)).apply()

        val sessionsJson = spApp.getString("sessions_list", "[]") ?: "[]"
        val sessions: MutableList<FrontSession> =
            Gson().fromJson(sessionsJson, object : TypeToken<MutableList<FrontSession>>() {}.type)
                ?: mutableListOf()

        hmFronting.forEach { f ->
            val start = (f["start_time"] as? Number)?.toLong() ?: 0L
            if (start > 0 && sessions.none { it.startTime == start }) {
                val hmAlterId = (f["alter_id"] as? Number)?.toInt()?.toString()
                val end = (f["end_time"] as? Number)?.toLong()
                val person = people.find { it.manualId == hmAlterId }

                if (person != null) {
                    sessions.add(
                        FrontSession(
                            personName = person.name,
                            startTime = start,
                            endTime = if (end != null && end > 0) end else null,
                            personId = person.id
                        )
                    )
                    sessionsCount++
                }
            }
        }
        spApp.edit().putString("sessions_list", Gson().toJson(sessions)).apply()

        val notesJson = spApp.getString("diary_notes", "[]") ?: "[]"
        val notes: MutableList<DiaryNote> =
            Gson().fromJson(notesJson, object : TypeToken<MutableList<DiaryNote>>() {}.type)
                ?: mutableListOf()

        hmJournal.forEach { j ->
            val ts = (j["timestamp"] as? Number)?.toLong() ?: 0L
            if (ts > 0 && notes.none { it.timestamp == ts }) {
                val title = j["title"] as? String ?: "Unnamed Entry"
                val content = j["content"] as? String ?: ""
                val hmAlterId = (j["alter_id"] as? Number)?.toInt()?.toString()

                val targetMemberId = people.find { it.manualId == hmAlterId }?.id

                notes.add(
                    DiaryNote(
                        title = title,
                        content = content,
                        timestamp = ts,
                        linkedMemberIds = if (targetMemberId != null) mutableListOf(targetMemberId) else mutableListOf()
                    )
                )
                journalCount++
            }
        }
        spApp.edit().putString("diary_notes", Gson().toJson(notes)).apply()

        Toast.makeText(
            this,
            "Imported $altersCount alters, $groupsCount groups, $sessionsCount front sessions and $journalCount journal entries",
            Toast.LENGTH_LONG
        ).show()
        recreate()
    }

    private fun processSpMembers(spMembers: List<Map<String, Any>>) {
        runOnUiThread {
            val people = loadPeopleList()
            var count = 0
            spMembers.forEach { spm ->
                val id =
                    spm["_id"] as? String ?: spm["id"] as? String ?: spm["uid"] as? String ?: ""
                val content = spm["content"] as? Map<*, *>

                val name = content?.get("name") as? String ?: spm["name"] as? String ?: "SP Member"
                val desc = content?.get("desc") as? String ?: spm["description"] as? String ?: ""

                val avatar = content?.get("avatarUrl") as? String
                    ?: spm["avatarUrl"] as? String
                    ?: spm["avatar_url"] as? String

                val colorHex = content?.get("color") as? String ?: spm["color"] as? String
                var profileColor = -6934396
                if (!colorHex.isNullOrBlank()) {
                    try {
                        val formattedHex = if (colorHex.startsWith("#")) colorHex else "#$colorHex"
                        profileColor = android.graphics.Color.parseColor(formattedHex)
                    } catch (_: Exception) {
                    }
                }

                if (id.isNotEmpty() && people.none { it.manualId == id }) {
                    val initialHandle =
                        name.replace(" ", "_").lowercase().replace(Regex("[^a-z0-9_]"), "")

                    val avatarUuid = spm["avatarUuid"] as? String

                    people.add(
                        Person(
                            name = name,
                            manualId = id,
                            profileInfo = desc,
                            profilePictureUri = avatar,
                            profileColor = profileColor,
                            sysmediaProfile = SysmediaProfile(handle = initialHandle)
                        )
                    )
                    count++
                }
            }
            MemberHelper.savePeople(this, people)
            Toast.makeText(this, "Imported $count members from Simply Plural", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun importFromPkJson(uri: Uri) {
        Thread {
            try {
                val json =
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: return@Thread
                val root = Gson().fromJson<Map<String, Any>>(
                    json,
                    object : TypeToken<Map<String, Any>>() {}.type
                )

                @Suppress("UNCHECKED_CAST")
                val membersList = root["members"] as? List<Map<String, Any>>

                if (membersList == null) {
                    try {
                        val directList = Gson().fromJson<List<Map<String, Any>>>(
                            json,
                            object : TypeToken<List<Map<String, Any>>>() {}.type
                        )
                        processPkMembers(directList)
                        return@Thread
                    } catch (_: Exception) {
                    }
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "No members found in PluralKit JSON",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@Thread
                }

                processPkMembers(membersList)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "PK JSON Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
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

                val colorHex = pkm["color"] as? String
                var profileColor = -6934396
                if (!colorHex.isNullOrBlank()) {
                    try {
                        val formattedHex = if (colorHex.startsWith("#")) colorHex else "#$colorHex"
                        profileColor = android.graphics.Color.parseColor(formattedHex)
                    } catch (_: Exception) {
                    }
                }

                if (id.isNotEmpty() && people.none { it.manualId == id }) {
                    val initialHandle =
                        name.replace(" ", "_").lowercase().replace(Regex("[^a-z0-9_]"), "")
                    people.add(
                        Person(
                            name = name,
                            manualId = id,
                            profileInfo = desc,
                            profilePictureUri = avatar,
                            profileColor = profileColor,
                            sysmediaProfile = SysmediaProfile(handle = initialHandle)
                        )
                    )
                    count++
                }
            }
            MemberHelper.savePeople(this, people)
            Toast.makeText(this, "Imported $count members from PluralKit JSON", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun handleAmpersandFile(uri: Uri) {
        Thread {
            val success = AmpersandImportHelper.importFromUri(this, uri)

            runOnUiThread {
                if (success) {
                    Toast.makeText(
                        this,
                        getString(R.string.import_ampersand_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    recreate()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.import_ampersand_decode_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun importFromPluralStar(uri: Uri) {
        Thread {
            try {
                val people = loadPeopleList()
                val avatarDir = filesDir
                var membersCount = 0
                var historyCount = 0
                var foundDataJson = false

                contentResolver.openInputStream(uri)?.use { input ->
                    java.util.zip.ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        var jsonData: String? = null
                        val tempAvatars = mutableMapOf<String, ByteArray>()

                        while (entry != null) {
                            val entryName = entry.name
                            val fileName =
                                entryName.substringAfterLast('/').substringAfterLast('\\')

                            if (fileName.equals(
                                    "data.json",
                                    ignoreCase = true
                                ) && !entry.isDirectory
                            ) {
                                foundDataJson = true
                                jsonData = zis.readBytes().toString(Charsets.UTF_8)
                            } else if (entryName.contains(
                                    "media/",
                                    ignoreCase = true
                                ) && !entry.isDirectory
                            ) {
                                tempAvatars[fileName] = zis.readBytes()
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }

                        if (jsonData != null) {
                            val root = try {
                                Gson().fromJson<Map<String, Any>>(
                                    jsonData,
                                    object : TypeToken<Map<String, Any>>() {}.type
                                )
                            } catch (e: Exception) {
                                null
                            }

                            if (root != null) {
                                val membersList =
                                    root["members"] as? List<Map<String, Any>> ?: emptyList()
                                val historyList =
                                    root["frontHistory"] as? List<Map<String, Any>> ?: emptyList()

                                membersList.forEach { m ->
                                    val id = m["id"] as? String ?: ""
                                    val name = m["name"] as? String ?: "Unknown"
                                    val pronouns = m["pronouns"] as? String ?: ""
                                    val role = m["role"] as? String ?: ""
                                    val desc = m["description"] as? String ?: ""
                                    val colorHex = m["color"] as? String
                                    val archived = m["archived"] as? Boolean ?: false
                                    val avatarPath = m["avatar_media_path"] as? String

                                    if (id.isNotEmpty() && people.none { it.manualId == id }) {
                                        var profileColor = -6934396
                                        if (!colorHex.isNullOrBlank()) {
                                            try {
                                                profileColor = Color.parseColor(colorHex)
                                            } catch (_: Exception) {
                                            }
                                        }

                                        val bio = StringBuilder()
                                        if (pronouns.isNotEmpty()) bio.append("Pronouns: $pronouns\n")
                                        if (role.isNotEmpty()) bio.append("Role: $role\n")
                                        bio.append(desc)

                                        val person = Person(
                                            name = name,
                                            manualId = id,
                                            profileInfo = bio.toString().trim(),
                                            profileColor = profileColor,
                                            isArchived = archived,
                                            sysmediaProfile = SysmediaProfile(
                                                handle = name.replace(
                                                    " ",
                                                    "_"
                                                ).lowercase().replace(Regex("[^a-z0-9_]"), "")
                                            )
                                        )

                                        val avatarFileName = avatarPath?.substringAfterLast('/')
                                            ?.substringAfterLast('\\')
                                        if (avatarFileName != null && tempAvatars.containsKey(
                                                avatarFileName
                                            )
                                        ) {
                                            val localFile = File(
                                                avatarDir,
                                                "profile_${person.id}_${System.currentTimeMillis()}.png"
                                            )
                                            localFile.writeBytes(tempAvatars[avatarFileName]!!)
                                            person.profilePictureUri =
                                                Uri.fromFile(localFile).toString()
                                        }

                                        people.add(person)
                                        membersCount++
                                    }
                                }
                                MemberHelper.savePeople(this@SettingsActivity, people)

                                val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
                                val sessionsJson =
                                    sharedPref.getString("sessions_list", "[]") ?: "[]"
                                val sessions: MutableList<FrontSession> = Gson().fromJson(
                                    sessionsJson,
                                    object : TypeToken<MutableList<FrontSession>>() {}.type
                                ) ?: mutableListOf()

                                historyList.forEach { h ->
                                    val memberIds = h["memberIds"] as? List<String> ?: emptyList()
                                    val start = (h["startTime"] as? Number)?.toLong() ?: 0L
                                    val end = (h["endTime"] as? Number)?.toLong()
                                    val note = h["note"] as? String
                                    val changeType = h["changeType"] as? String

                                    if (changeType != null && changeType != "front") return@forEach

                                    if (start > 0 && sessions.none { it.startTime == start }) {
                                        memberIds.forEach { mid ->
                                            val person = people.find { it.manualId == mid }
                                            if (person != null) {
                                                sessions.add(
                                                    FrontSession(
                                                        personName = person.name,
                                                        startTime = start,
                                                        endTime = if (end != null && end > 0) end else null,
                                                        personId = person.id,
                                                        note = note
                                                    )
                                                )
                                                historyCount++
                                            }
                                        }
                                    }
                                }
                                sharedPref.edit()
                                    .putString("sessions_list", Gson().toJson(sessions)).apply()
                            }
                        }
                    }
                }

                runOnUiThread {
                    if (membersCount > 0 || historyCount > 0) {
                        Toast.makeText(
                            this,
                            getString(
                                R.string.import_plural_star_success,
                                membersCount,
                                historyCount
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                        recreate()
                    } else if (!foundDataJson) {
                        Toast.makeText(
                            this,
                            "Could not find data.json inside the ZIP",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.import_plural_star_no_new_data),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.plural_star_error, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
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

    private fun importSpAvatars(uri: Uri) {
        Thread {
            try {
                val people = loadPeopleList()
                val avatarDir = filesDir
                var count = 0
                contentResolver.openInputStream(uri)?.use { input ->
                    java.util.zip.ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val fileName = entry.name.substringAfterLast("/")
                                val idInFile = fileName.substringBeforeLast(".")
                                val person = people.find { it.manualId == idInFile }
                                if (person != null) {
                                    val localFile = File(avatarDir, "profile_${person.id}_${System.currentTimeMillis()}.png")
                                    avatarDir.listFiles { f -> f.name.startsWith("profile_${person.id}_") }?.forEach { it.delete() }
                                    FileOutputStream(localFile).use { out -> zis.copyTo(out) }
                                    person.profilePictureUri = Uri.fromFile(localFile).toString()
                                    count++
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
                runOnUiThread {
                    MemberHelper.savePeople(this, people)
                    Toast.makeText(this, "Imported $count avatars from ZIP", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Avatar ZIP Error: ${e.message}", Toast.LENGTH_SHORT).show() }
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
            .let { ColorHelper.styleAlertDialog(it, this) }
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

    private var restoreClicks = 0
    private fun showRestoreMemberDialog() {
        val people = loadPeopleList()
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val sessionsJson = sharedPref.getString("sessions_list", "[]") ?: "[]"
        val type = object : TypeToken<List<FrontSession>>() {}.type
        val sessions: List<FrontSession> = try {
            Gson().fromJson(sessionsJson, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val archivedMembers = people.filter { it.isArchived }

        val currentNames = people.map { it.name.lowercase() }.toSet()
        val currentIds = people.map { it.id }.toSet()

        val historyMembers = sessions.filter {
            it.personName.lowercase() !in currentNames && (it.personId == null || it.personId !in currentIds)
        }.distinctBy { it.personName.lowercase() }

        val items = mutableListOf<Triple<String, String?, Boolean>>() // Name, ID, isArchived
        archivedMembers.forEach { items.add(Triple(it.name, it.id, true)) }
        historyMembers.forEach { items.add(Triple(it.personName, it.personId, false)) }

        if (items.isEmpty()) {
            Toast.makeText(this, R.string.no_restorable_members, Toast.LENGTH_SHORT).show()
            return
        }

        val names = items.map {
            val prefix = if (it.third) "[Archived] " else "[Deleted] "
            prefix + it.first
        }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.restore_dialog_title)
            .setItems(names) { _, which ->
                val selected = items[which]
                if (selected.third) {
                    val person = people.find { it.id == selected.second }
                    if (person != null) {
                        person.isArchived = false
                        MemberHelper.savePeople(this, people)
                        Toast.makeText(this, getString(R.string.restore_member_success, selected.first), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    MemberHelper.restoreDeletedMember(this, selected.first, selected.second)
                    Toast.makeText(this, getString(R.string.restore_member_success, selected.first), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let { ColorHelper.styleSupportAlertDialog(it, this) }
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
                            .let { ColorHelper.styleAlertDialog(it, this@SettingsActivity) }
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
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun loadPeopleList(): MutableList<Person> {
        return MemberHelper.loadAllPeople(this)
    }
    
    private fun loadGroupsList(): List<Group> {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = sharedPref.getString("groups_list", "[]") ?: "[]"
        return Gson().fromJson(json, object : TypeToken<List<Group>>() {}.type)
    }

    private fun showFontSizeDialog() {
        val sharedPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val currentMult = sharedPref.getFloat("font_size_multiplier", 1.0f)

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, p)
        }

        val tvPreview = android.widget.TextView(this).apply {
            text = "Preview Text Size"
            textSize = 16f
            setTextColor(ColorHelper.getTextColor(this@SettingsActivity))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, (24 * resources.displayMetrics.density).toInt())
        }
        container.addView(tvPreview)

        val seekBar = android.widget.SeekBar(this).apply {
            max = 100
            progress = ((currentMult - 0.75f) * 100).toInt().coerceIn(0, 100)
        }

        fun updatePreview(progress: Int) {
            val mult = 0.75f + (progress / 100f)
            tvPreview.textSize = 16f * mult
            tvPreview.text = "Preview: ${(mult * 100).toInt()}%"
        }

        updatePreview(seekBar.progress)

        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                updatePreview(p)
            }
            override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
        })
        container.addView(seekBar)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Font Size")
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val finalMult = 0.75f + (seekBar.progress / 100f)
                sharedPref.edit(commit = true) { putFloat("font_size_multiplier", finalMult) }
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun showImportMoodHexDialog() {
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
            .setTitle("Import Mood Hex")
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val hexString = input.text.toString().trim()
                val parts = hexString.split(",").map { it.trim() }
                if (parts.size >= 5) {
                    for (i in 0 until 5) {
                        moodColors[i] = parts[i]
                    }
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

    private fun showDeleteAllDataDialog() {
        val labels = arrayOf(
            getString(R.string.delete_members),
            getString(R.string.delete_history),
            getString(R.string.delete_mood),
            getString(R.string.delete_notes),
            getString(R.string.delete_todo),
            getString(R.string.module_relations),
            getString(R.string.delete_settings),
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
        ColorHelper.styleAlertDialog(dialog, this)
        
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
                spSettings.edit().remove("saved_mood_themes").apply()
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
            if (checked[5]) { // relationships
                remove("relations_environments")
                remove("relations_data")
            }
        }.apply()

        if (checked[6]) { // settings
            spSettings.edit().clear().apply()
        }

        Toast.makeText(this, "Selected data deleted", Toast.LENGTH_SHORT).show()
        recreate()
    }
}
