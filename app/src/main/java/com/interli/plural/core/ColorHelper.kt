package com.interli.plural.core

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.AppTheme
import com.interli.plural.CoFrontMoodTheme
import com.interli.plural.CoFrontTheme
import com.interli.plural.FrontSession
import com.interli.plural.Group
import com.interli.plural.MoodTheme
import com.interli.plural.Person
import com.interli.plural.R

object ColorHelper {
    fun applySettings(activity: Activity) {
        val effectiveTheme = getEffectiveTheme(activity)
        val sharedPref = activity.getSharedPreferences("settings_prefs", Activity.MODE_PRIVATE)
        val bgHex = effectiveTheme?.bgColor ?: sharedPref.getString("bg_color", "#FFFDF0") ?: "#FFFDF0"
        val btnHex = effectiveTheme?.btnColor ?: sharedPref.getString("btn_color", "#7D4EBA") ?: "#7D4EBA"
        val btnTextHex = effectiveTheme?.btnTextColor ?: sharedPref.getString("btn_text_color", "#D2B8F5") ?: "#D2B8F5"
        val textHex = effectiveTheme?.textColor ?: sharedPref.getString("text_color", "#1A1811") ?: "#1A1811"
        try {
            val bgColor = bgHex.toColorInt()
            val btnColor = btnHex.toColorInt()
            val btnTextColor = btnTextHex.toColorInt()
            val globalTextColor = textHex.toColorInt()
            activity.window.decorView.setBackgroundColor(bgColor)
            val controller = androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            controller.isAppearanceLightStatusBars = !isDark(bgColor)
            val root = activity.findViewById<View>(android.R.id.content)
            val colorKey = "$bgHex|$btnHex|$btnTextHex|$textHex|v3"
            if (root != null) {
                if (root is ViewGroup) {
                    applyToViewGroup(root, bgColor, btnColor, btnTextColor, globalTextColor)
                }
                root.setTag(R.id.color_tag, colorKey)
            }
            activity.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)?.let {
                styleNavigationView(it)
            }
            activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)?.let {
                it.setBackgroundColor(bgColor)
                it.setTitleTextColor(globalTextColor)
                it.setNavigationIconTint(globalTextColor)
                it.overflowIcon?.setTint(globalTextColor)
            }
        } catch (_: Exception) {}
    }
    fun applyTheme(context: android.content.Context, theme: AppTheme) {
        val sharedPref = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
        sharedPref.edit(commit = true) {
            putString("bg_color", theme.bgColor)
            putString("btn_color", theme.btnColor)
            putString("btn_text_color", theme.btnTextColor)
            putString("front_color", theme.frontColor)
            putString("text_color", theme.textColor)
        }
    }
    fun styleAlertDialog(dialog: android.app.AlertDialog, context: android.content.Context) {
        val textColor = getTextColor(context)
        val bgColor = getBgColor(context)
        val btnColor = getBtnColor(context)
        val btnTextColor = getBtnTextColor(context)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bgColor))
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(textColor)
        dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
        val applyButtons = {
            val btnPos = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            val btnNeg = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
            val btnNeu = dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)
            val spaceInPixels = (16 * context.resources.displayMetrics.density).toInt()
            (btnPos?.layoutParams as? LinearLayout.LayoutParams)?.let {
                it.marginStart = spaceInPixels
                btnPos.layoutParams = it
            }
            listOf(btnPos, btnNeg, btnNeu).forEach { btn ->
                (btn as? com.google.android.material.button.MaterialButton)?.let { mb ->
                    mb.iconPadding = 12
                    mb.strokeWidth = 0
                }
                btn?.setTextColor(btnTextColor)
            }
        }
        dialog.setOnShowListener { applyButtons() }
        applyButtons()
        val decor = dialog.window?.decorView
        if (decor is ViewGroup) {
            applyToViewGroup(decor, bgColor, btnColor, btnTextColor, textColor)
        }
        val listView = dialog.listView
        if (listView != null) {
            listView.setBackgroundColor(bgColor)
            listView.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    child?.let { 
                        applyToView(it, bgColor, btnColor, btnTextColor, textColor)
                        if (it is ViewGroup) applyToViewGroup(it, bgColor, btnColor, btnTextColor, textColor)
                    }
                }
                override fun onChildViewRemoved(parent: View?, child: View?) {}
            })
            for (i in 0 until listView.childCount) {
                val child = listView.getChildAt(i)
                applyToView(child, bgColor, btnColor, btnTextColor, textColor)
                if (child is ViewGroup) applyToViewGroup(child, bgColor, btnColor, btnTextColor, textColor)
            }
        }
    }
    fun styleAlertDialog(dialog: androidx.appcompat.app.AlertDialog, context: android.content.Context) {
        val textColor = getTextColor(context)
        val bgColor = getBgColor(context)
        val btnColor = getBtnColor(context)
        val btnTextColor = getBtnTextColor(context)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bgColor))
        val applyStyling = {
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(textColor)
            dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(textColor)
            val btnPos = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            val btnNeg = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
            val btnNeu = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
            val spaceInPixels = (16 * context.resources.displayMetrics.density).toInt()
            (btnPos?.layoutParams as? LinearLayout.LayoutParams)?.let {
                it.marginStart = spaceInPixels
                btnPos.layoutParams = it
            }
            listOf(btnPos, btnNeg, btnNeu).forEach { btn ->
                btn?.let { b ->
                    if (b is com.google.android.material.button.MaterialButton) {
                        b.backgroundTintList = android.content.res.ColorStateList.valueOf(btnColor)
                        b.iconPadding = 12
                        b.strokeWidth = 0
                        b.rippleColor = android.content.res.ColorStateList.valueOf(btnTextColor and 0x33FFFFFF)
                    } else {
                        b.setBackgroundColor(btnColor)
                    }
                    b.setTextColor(btnTextColor)
                }
            }
        }
        dialog.setOnShowListener { applyStyling() }
        applyStyling()
        val decor = dialog.window?.decorView
        if (decor is ViewGroup) {
            applyToViewGroup(decor, bgColor, btnColor, btnTextColor, textColor)
        }
        val listView = dialog.listView
        if (listView != null) {
            listView.setBackgroundColor(bgColor)
            listView.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    child?.let { 
                        applyToView(it, bgColor, btnColor, btnTextColor, textColor)
                        if (it is ViewGroup) applyToViewGroup(it, bgColor, btnColor, btnTextColor, textColor)
                    }
                }
                override fun onChildViewRemoved(parent: View?, child: View?) {}
            })
            for (i in 0 until listView.childCount) {
                val child = listView.getChildAt(i)
                applyToView(child, bgColor, btnColor, btnTextColor, textColor)
                if (child is ViewGroup) applyToViewGroup(child, bgColor, btnColor, btnTextColor, textColor)
            }
        }
    }
    @Deprecated("Use styleAlertDialog instead", ReplaceWith("styleAlertDialog(dialog, context)"))
    fun styleSupportAlertDialog(dialog: androidx.appcompat.app.AlertDialog, context: android.content.Context) {
        styleAlertDialog(dialog, context)
    }
    fun styleNavigationView(navigationView: com.google.android.material.navigation.NavigationView) {
        val context = navigationView.context
        val textColor = getTextColor(context)
        val bgColor = getBgColor(context)
        val btnColor = getBtnColor(context)
        navigationView.setBackgroundColor(bgColor)
        navigationView.itemTextColor = android.content.res.ColorStateList.valueOf(textColor)
        navigationView.itemIconTintList = android.content.res.ColorStateList.valueOf(textColor)
        val header = if (navigationView.headerCount > 0) navigationView.getHeaderView(0) else navigationView.inflateHeaderView(R.layout.nav_header)
        if (header != null) {
            header.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            val btnMember = header.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNavAddMember)
            val btnGroup = header.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNavAddGroup)
            btnMember?.setTextColor(textColor)
            btnMember?.iconTint = android.content.res.ColorStateList.valueOf(textColor)
            btnGroup?.setTextColor(textColor)
            btnGroup?.iconTint = android.content.res.ColorStateList.valueOf(textColor)
            header.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            header.requestLayout()
        }
    }
    fun isDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }
    fun getEffectiveTheme(context: android.content.Context): AppTheme? {
        val sp = context.getSharedPreferences("my_app", android.content.Context.MODE_PRIVATE)
        val settingsSp = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
        val peopleJson = sp.getString("people_list", null) ?: return null
        val sessionsJson = sp.getString("sessions_list", null) ?: return null
        val gson = Gson()
        return try {
            val peopleType = object : TypeToken<MutableList<Person>>() {}.type
            val people: List<Person> = gson.fromJson(peopleJson, peopleType) ?: emptyList()
            val sessionsType = object : TypeToken<MutableList<FrontSession>>() {}.type
            val sessions: List<FrontSession> = gson.fromJson(sessionsJson, sessionsType) ?: emptyList()
            val frontingPeopleIds = people.filter { it.isFront && !it.isArchived }
                .map { it.id }
                .distinct()
                .sorted()
            val themesJson = settingsSp.getString("saved_themes", "[]") ?: "[]"
            val themesType = object : TypeToken<MutableList<AppTheme>>() {}.type
            val themes: List<AppTheme> = gson.fromJson(themesJson, themesType) ?: emptyList()
            if (frontingPeopleIds.isEmpty()) {
                val defaultThemeId = settingsSp.getString("default_theme_id", null)
                return themes.find { it.id == defaultThemeId }
            }
            if (frontingPeopleIds.size > 1) {
                val coFrontThemesJson = settingsSp.getString("co_front_themes", "[]") ?: "[]"
                val coFrontThemesType = object : TypeToken<MutableList<CoFrontTheme>>() {}.type
                val coFrontThemes: List<CoFrontTheme> = gson.fromJson(coFrontThemesJson, coFrontThemesType) ?: emptyList()
                val specificThemeId = coFrontThemes.find { it.memberIds.sorted() == frontingPeopleIds }?.themeId
                if (specificThemeId != null) {
                    val theme = themes.find { it.id == specificThemeId }
                    if (theme != null) return theme
                }
                val frontingPeople = people.filter { it.isFront && !it.isArchived }
                val themeCounts = mutableMapOf<String, Int>()
                frontingPeople.forEach { p ->
                    p.linkedThemeId?.let { themeId ->
                        themeCounts[themeId] = (themeCounts[themeId] ?: 0) + 1
                    }
                }
                val majorityThemeEntry = themeCounts.entries.find { it.value > frontingPeopleIds.size * 0.51 }
                if (majorityThemeEntry != null) {
                    val theme = themes.find { it.id == majorityThemeEntry.key }
                    if (theme != null) return theme
                }
                val multiThemeId = settingsSp.getString("multi_front_theme_id", null)
                val multiTheme = themes.find { it.id == multiThemeId }
                if (multiTheme != null) return multiTheme
            }
            val priority = settingsSp.getString("theme_priority", "newest")
            val activeSessionsMap = mutableMapOf<String, FrontSession>()
            sessions.filter { it.endTime == null && it.personId != null && frontingPeopleIds.contains(it.personId) }
                .forEach { session ->
                    val pid = session.personId!!
                    val existing = activeSessionsMap[pid]
                    if (existing == null || session.startTime > existing.startTime) {
                        activeSessionsMap[pid] = session
                    }
                }
            val activeSessions = if (priority == "oldest") {
                activeSessionsMap.values.sortedBy { it.startTime }
            } else {
                activeSessionsMap.values.sortedByDescending { it.startTime }
            }
            for (session in activeSessions) {
                val person = people.find { it.id == session.personId }
                if (person?.linkedThemeId != null) {
                    val theme = themes.find { it.id == person.linkedThemeId }
                    if (theme != null) return theme
                }
            }
            val fallbackThemeId = settingsSp.getString("default_theme_id", null)
            themes.find { it.id == fallbackThemeId }
        } catch (_: Exception) {
            null
        }
    }
    fun getEffectiveMoodTheme(context: android.content.Context): MoodTheme? {
        val sp = context.getSharedPreferences("my_app", android.content.Context.MODE_PRIVATE)
        val settingsSp = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
        val peopleJson = sp.getString("people_list", null) ?: return null
        val sessionsJson = sp.getString("sessions_list", null) ?: return null
        val gson = Gson()
        return try {
            val peopleType = object : TypeToken<MutableList<Person>>() {}.type
            val people: List<Person> = gson.fromJson(peopleJson, peopleType) ?: emptyList()
            val sessionsType = object : TypeToken<MutableList<FrontSession>>() {}.type
            val sessions: List<FrontSession> = gson.fromJson(sessionsJson, sessionsType) ?: emptyList()
            val frontingPeopleIds = people.filter { it.isFront && !it.isArchived }
                .map { it.id }
                .distinct()
                .sorted()
            val themesJson = settingsSp.getString("saved_mood_themes", "[]") ?: "[]"
            val themesType = object : TypeToken<MutableList<MoodTheme>>() {}.type
            val themes: List<MoodTheme> = gson.fromJson(themesJson, themesType) ?: emptyList()
            if (frontingPeopleIds.isEmpty()) {
                val defaultThemeId = settingsSp.getString("default_mood_theme_id", null)
                return themes.find { it.id == defaultThemeId }
            }
            if (frontingPeopleIds.size > 1) {
                val coFrontThemesJson = settingsSp.getString("co_front_mood_themes", "[]") ?: "[]"
                val coFrontThemesType = object : TypeToken<MutableList<CoFrontMoodTheme>>() {}.type
                val coFrontThemes: List<CoFrontMoodTheme> = gson.fromJson(coFrontThemesJson, coFrontThemesType) ?: emptyList()
                val specificThemeId = coFrontThemes.find { it.memberIds.sorted() == frontingPeopleIds }?.moodThemeId
                if (specificThemeId != null) {
                    val theme = themes.find { it.id == specificThemeId }
                    if (theme != null) return theme
                }
                val frontingPeople = people.filter { it.isFront && !it.isArchived }
                val themeCounts = mutableMapOf<String, Int>()
                frontingPeople.forEach { p ->
                    p.linkedMoodThemeId?.let { themeId ->
                        themeCounts[themeId] = (themeCounts[themeId] ?: 0) + 1
                    }
                }
                val majorityThemeEntry = themeCounts.entries.find { it.value > frontingPeopleIds.size * 0.51 }
                if (majorityThemeEntry != null) {
                    val theme = themes.find { it.id == majorityThemeEntry.key }
                    if (theme != null) return theme
                }
                val multiThemeId = settingsSp.getString("multi_front_mood_theme_id", null)
                val multiTheme = themes.find { it.id == multiThemeId }
                if (multiTheme != null) return multiTheme
            }
            val priority = settingsSp.getString("theme_priority", "newest")
            val activeSessionsMap = mutableMapOf<String, FrontSession>()
            sessions.filter { it.endTime == null && it.personId != null && frontingPeopleIds.contains(it.personId) }
                .forEach { session ->
                    val pid = session.personId!!
                    val existing = activeSessionsMap[pid]
                    if (existing == null || session.startTime > existing.startTime) {
                        activeSessionsMap[pid] = session
                    }
                }
            val activeSessions = if (priority == "oldest") {
                activeSessionsMap.values.sortedBy { it.startTime }
            } else {
                activeSessionsMap.values.sortedByDescending { it.startTime }
            }
            for (session in activeSessions) {
                val person = people.find { it.id == session.personId }
                if (person?.linkedMoodThemeId != null) {
                    val theme = themes.find { it.id == person.linkedMoodThemeId }
                    if (theme != null) return theme
                }
            }
            val fallbackThemeId = settingsSp.getString("default_mood_theme_id", null)
            themes.find { it.id == fallbackThemeId }
        } catch (_: Exception) {
            null
        }
    }
    fun getBgColor(context: android.content.Context): Int {
        val effectiveTheme = getEffectiveTheme(context)
        if (effectiveTheme != null) return try { effectiveTheme.bgColor.toColorInt() } catch(_: Exception) { "#FFFDF0".toColorInt() }
        val sharedPref = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
        val bgHex = sharedPref.getString("bg_color", "#FFFDF0") ?: "#FFFDF0"
        return try { bgHex.toColorInt() } catch (_: Exception) { "#FFFDF0".toColorInt() }
    }
    fun getFrontColor(context: android.content.Context): Int {
        val effectiveTheme = getEffectiveTheme(context)
        if (effectiveTheme != null) return try { effectiveTheme.frontColor.toColorInt() } catch(_: Exception) { "#FCF09F".toColorInt() }
        val sharedPref = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
        val frontHex = sharedPref.getString("front_color", "#FCF09F") ?: "#FCF09F"
        return try { frontHex.toColorInt() } catch (_: Exception) { "#FCF09F".toColorInt() }
    }
    fun getTextColor(context: android.content.Context): Int {
        val effectiveTheme = getEffectiveTheme(context)
        if (effectiveTheme != null) return try { effectiveTheme.textColor.toColorInt() } catch(_: Exception) { "#1A1811".toColorInt() }
        val sharedPref = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
        val textHex = sharedPref.getString("text_color", "#1A1811") ?: "#1A1811"
        return try { textHex.toColorInt() } catch (_: Exception) { "#1A1811".toColorInt() }
    }
    fun getBtnColor(context: android.content.Context): Int {
        val effectiveTheme = getEffectiveTheme(context)
        if (effectiveTheme != null) return try { effectiveTheme.btnColor.toColorInt() } catch(_: Exception) { "#7D4EBA".toColorInt() }
        val sharedPref = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
        val btnHex = sharedPref.getString("btn_color", "#7D4EBA") ?: "#7D4EBA"
        return try { btnHex.toColorInt() } catch (_: Exception) { "#7D4EBA".toColorInt() }
    }
    fun getBtnTextColor(context: android.content.Context): Int {
        val effectiveTheme = getEffectiveTheme(context)
        if (effectiveTheme != null) return try { effectiveTheme.btnTextColor.toColorInt() } catch(_: Exception) { "#D2B8F5".toColorInt() }
        val sharedPref = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
        val btnTextHex = sharedPref.getString("btn_text_color", "#D2B8F5") ?: "#D2B8F5"
        return try { btnTextHex.toColorInt() } catch (_: Exception) { "#D2B8F5".toColorInt() }
    }
    fun <T> createThemedAdapter(context: android.content.Context, items: List<T>): android.widget.ArrayAdapter<T> {
        val textColor = getTextColor(context)
        val bgColor = getBgColor(context)
        val adapter = object : android.widget.ArrayAdapter<T>(context, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as? TextView)?.setTextColor(textColor)
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent)
                (v as? TextView)?.setTextColor(textColor)
                v.setBackgroundColor(bgColor)
                return v
            }
        }
        return adapter
    }
    private fun applyToView(view: View, bgColor: Int, btnColor: Int, btnTextColor: Int, globalTextColor: Int) {
        val childTag = view.getTag(R.id.color_tag)
        if (childTag == "skip") return
        when (view) {
            is com.google.android.material.textfield.TextInputLayout -> {
                val hintColorStateList = android.content.res.ColorStateList.valueOf(globalTextColor)
                view.defaultHintTextColor = hintColorStateList
                view.hintTextColor = android.content.res.ColorStateList.valueOf(btnColor)
                view.setBoxStrokeColor(btnColor)
                view.setBoxStrokeColorStateList(android.content.res.ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                    intArrayOf(btnColor, globalTextColor and 0x44FFFFFF)
                ))
                view.editText?.let { et ->
                    et.setTextColor(globalTextColor)
                    et.setHintTextColor(globalTextColor and 0x88FFFFFF.toInt())
                }
            }
            is com.google.android.material.chip.Chip -> {
                if (childTag != "custom") {
                    view.chipBackgroundColor = android.content.res.ColorStateList.valueOf(btnColor)
                    view.setTextColor(btnTextColor)
                }
            }
            is com.google.android.material.button.MaterialButton -> {
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(btnColor)
                val contrastColor = btnTextColor or 0xFF000000.toInt()
                view.iconTint = android.content.res.ColorStateList.valueOf(contrastColor)
                view.setTextColor(contrastColor)
                view.strokeWidth = 0
            }
            is android.widget.CheckedTextView -> {
                view.setTextColor(globalTextColor)
                try {
                    view.checkMarkTintList = android.content.res.ColorStateList.valueOf(globalTextColor)
                } catch (_: Exception) {}
            }
            is android.widget.CompoundButton -> {
                view.setTextColor(globalTextColor)
                view.buttonTintList = android.content.res.ColorStateList.valueOf(globalTextColor)
            }
            is com.google.android.material.tabs.TabLayout -> {
                view.setBackgroundColor(bgColor)
                view.setTabTextColors(globalTextColor, btnColor)
                view.setSelectedTabIndicatorColor(btnColor)
            }
            is com.google.android.material.floatingactionbutton.FloatingActionButton -> {
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(btnColor)
                view.imageTintList = android.content.res.ColorStateList.valueOf(btnTextColor)
            }
            is Button -> {
                view.setBackgroundColor(btnColor)
                view.setTextColor(btnTextColor)
            }
            is android.widget.EditText -> {
                view.setTextColor(globalTextColor)
                view.setHintTextColor(globalTextColor and 0x88FFFFFF.toInt())
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(globalTextColor)
            }
            is TextView -> {
                view.setTextColor(globalTextColor)
            }
        }
    }
    private fun applyToViewGroup(group: ViewGroup, bgColor: Int, btnColor: Int, btnTextColor: Int, globalTextColor: Int) {
        val groupTag = group.getTag(R.id.color_tag)
        if (groupTag == "skip") return
        if (groupTag is String && groupTag.startsWith("#") && !groupTag.contains("|")) return
        if (group !is com.google.android.material.card.MaterialCardView && 
            group !is androidx.recyclerview.widget.RecyclerView &&
            group !is com.google.android.material.chip.ChipGroup) {
            group.setBackgroundColor(bgColor)
        }
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            applyToView(child, bgColor, btnColor, btnTextColor, globalTextColor)
            if (child is ViewGroup && child !is androidx.recyclerview.widget.RecyclerView) {
                applyToViewGroup(child, bgColor, btnColor, btnTextColor, globalTextColor)
            }
        }
    }
    fun applyTextColorToAllViews(group: ViewGroup, textColor: Int) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            applyToView(child, 0, 0, 0, textColor)
            if (child is ViewGroup) {
                applyTextColorToAllViews(child, textColor)
            }
        }
    }
    fun getUserColor(id: String?, currentProfileColor: Int): Int {
        if (currentProfileColor != -6934396) return currentProfileColor
        if (id == null) return Color.GRAY
        val hash = id.hashCode()
        val h = (hash.toLong() and 0xFFFFFFFFL) % 360
        return Color.HSVToColor(floatArrayOf(h.toFloat(), 0.5f, 0.85f))
    }
    fun getMoodColor(context: android.content.Context, moodLabel: String): Int {
        val sp = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
        val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")
        val moodLabels = moodKeys.map { key ->
            val resId = context.resources.getIdentifier(key, "string", context.packageName)
            if (resId != 0) context.getString(resId) else key
        }
        var index = moodKeys.indexOf(moodLabel)
        if (index == -1) index = moodLabels.indexOf(moodLabel)
        if (index == -1) return Color.GRAY
        val moodIndex = 5 - index // 1=Rad, 2=Good, 3=Meh, 4=Bad, 5=Awful
        val effectiveMoodTheme = getEffectiveMoodTheme(context)
        if (effectiveMoodTheme != null) {
            val hex = when(moodIndex) {
                1 -> effectiveMoodTheme.mood1
                2 -> effectiveMoodTheme.mood2
                3 -> effectiveMoodTheme.mood3
                4 -> effectiveMoodTheme.mood4
                5 -> effectiveMoodTheme.mood5
                else -> null
            }
            if (hex != null) return try { Color.parseColor(hex) } catch (_: Exception) { Color.GRAY }
        }
        val colorKey = "mood_color_$moodIndex"
        val defaultColors = listOf("#fffa94", "#54bd44", "#8844bd", "#4446bd", "#3a3a47")
        val colorHex = sp.getString(colorKey, defaultColors[moodIndex - 1]) ?: defaultColors[moodIndex - 1]
        return try { Color.parseColor(colorHex) } catch (_: Exception) { Color.GRAY }
    }
    fun getMoodColorByScore(context: android.content.Context, score: Float): Int {
        val displayScore = score + 1f
        val label = when {
            displayScore >= 4.5f -> "mood_rad"
            displayScore >= 3.5f -> "mood_good"
            displayScore >= 2.5f -> "mood_meh"
            displayScore >= 1.5f -> "mood_bad"
            else -> "mood_awful"
        }
        return getMoodColor(context, label)
    }
}
