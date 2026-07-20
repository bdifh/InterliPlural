package com.interli.plural.features.member

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.AppTheme
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.core.CropImageActivity
import com.interli.plural.core.ImageHelper
import com.interli.plural.CustomField
import com.interli.plural.core.MediaEmbedHelper
import com.interli.plural.features.diary.MemberNotesActivity
import com.interli.plural.features.member.MemberFrontHistoryActivity
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.features.member.MemberPreferencesActivity
import com.interli.plural.features.mood.MemberMoodActivity
import com.interli.plural.features.todo.MemberTodoActivity
import com.interli.plural.FrontSession
import com.interli.plural.Group
import com.interli.plural.Person
import com.interli.plural.R
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.Markwon
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.CustomNode
import org.commonmark.node.Node

class ProfileActivity : BaseActivity() {
    private var personIndex: Int = -1
    private lateinit var people: MutableList<Person>
    private lateinit var groups: MutableList<Group>
    private lateinit var sessions: MutableList<FrontSession>
    private var selectedColor: Int = -6934396
    private var selectedImageUri: Uri? = null
    private var selectedGroupIds: MutableList<String> = mutableListOf()
    private var selectedThemeId: String? = null
    private val customFieldEdits = mutableMapOf<String, EditText>()
    private val customFieldVisibilities = mutableMapOf<String, Boolean>()
    private var isEditMode = false
    private lateinit var markwon: Markwon
    private lateinit var customFieldsSettings: List<CustomField>
    private var initialName = ""
    private var initialColor = 0
    private var initialImageUri: String? = null
    private var initialGroupIds = listOf<String>()
    private var initialCustomFields = mapOf<String, String>()
    private var initialHiddenFields = listOf<String>()
    private var initialIncludeInStats = true
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            startCropActivity(uri)
        }
    }
    private val cropImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val croppedUriStr = result.data?.getStringExtra("cropped_uri")
            val person = people.getOrNull(personIndex)
            if (croppedUriStr != null && person != null) {
                val internalUri = saveImageToInternalStorage(Uri.parse(croppedUriStr), person.id, isSource = false)
                if (internalUri != null) {
                    selectedImageUri = internalUri
                    findViewById<ImageView>(R.id.profileImage).load(internalUri) { crossfade(true) }
                }
            }
        }
    }
    private fun startCropActivity(uri: Uri) {
        val intent = Intent(this, CropImageActivity::class.java)
        intent.putExtra("image_uri", uri.toString())
        cropImageLauncher.launch(intent)
    }
    private val downloadLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        uri?.let { saveImageToUri(it, Bitmap.CompressFormat.JPEG) }
    }
    private val downloadPngLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        uri?.let { saveImageToUri(it, Bitmap.CompressFormat.PNG) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasChanges()) {
                    showUnsavedChangesDialog { finish() }
                } else {
                    finish()
                }
            }
        })
        markwon = Markwon.builder(this)
            .usePlugin(TablePlugin.create(this))
            .usePlugin(CoilImagesPlugin.create(this))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun beforeRender(node: Node) {
                    node.accept(object : AbstractVisitor() {
                        override fun visit(customNode: CustomNode) {
                            if (customNode is TableCell) {
                                customNode.isHeader = false
                            }
                            super.visit(customNode)
                        }
                    })
                }
            })
            .build()
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        val personId = intent.getStringExtra("person_id")
        loadData()
        personIndex = people.indexOfFirst { it.id == personId }

        if (personIndex == -1) {
            Toast.makeText(this, "Member not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val person = people[personIndex]
        val nameEdit = findViewById<TextInputEditText>(R.id.editProfileName)
        val colorPreview = findViewById<View>(R.id.colorPreview)
        val colorPreviewCard = findViewById<View>(R.id.colorPreviewCard)
        val profileImageCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.profileImageCard)
        val profileImage = findViewById<ImageView>(R.id.profileImage)
        val switchIncludeInStats = findViewById<SwitchCompat>(R.id.switchIncludeInStats)
        val tvSelectedTheme = findViewById<TextView>(R.id.tvSelectedTheme)
        profileImageCard.setCardBackgroundColor(ColorHelper.getBgColor(this))
        val selectedGroupsText = findViewById<TextView>(R.id.selectedGroupsText)
        val customFieldsContainer = findViewById<LinearLayout>(R.id.customFieldsProfileContainer)
        val btnToggleEdit = findViewById<Button>(R.id.btnToggleEditMode)
        nameEdit.setText(person.name)
        selectedColor = person.profileColor
        selectedGroupIds = person.safeGroupIds.toMutableList()
        selectedThemeId = person.linkedThemeId
        switchIncludeInStats.isChecked = !person.excludeFromStats
        val sharedPrefSettings = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val fieldsJson = sharedPrefSettings.getString("custom_fields", "[]")
        customFieldsSettings = try {
            Gson().fromJson(fieldsJson, object : TypeToken<List<CustomField>>() {}.type)
        } catch (e: Exception) {
            val names: List<String> = Gson().fromJson(fieldsJson, object : TypeToken<List<String>>() {}.type)
            names.map { CustomField(name = it, template = "") }
        }
        var needsSettingsSave = false
        customFieldsSettings.forEach {
            if (it.id == null) {
                it.getUniqueId()
                needsSettingsSave = true
            }
        }
        if (needsSettingsSave) {
            sharedPrefSettings.edit {
                putString("custom_fields", Gson().toJson(customFieldsSettings))
            }
        }
        customFieldsSettings.forEach { field ->
            val fieldId = field.getUniqueId()
            val hasValue = person.safeCustomFields.containsKey(fieldId) || person.safeCustomFields.containsKey(field.name)
            val isExplicitlyHidden = person.safeHiddenFields.contains(fieldId) || person.safeHiddenFields.contains(field.name)
            val isVisible = if (!hasValue && !isExplicitlyHidden) {
                false
            } else {
                !isExplicitlyHidden
            }
            customFieldVisibilities[fieldId] = isVisible
        }
        updateColorPreview(colorPreview)
        updateGroupsText(selectedGroupsText)
        updateThemeText(tvSelectedTheme)
        renderCustomFields(customFieldsContainer, person)
        captureInitialState()
        colorPreviewCard.setOnClickListener {
            showHexColorDialog(colorPreview)
        }
        val existingUri = person.profilePictureUri
        if (!existingUri.isNullOrBlank()) {
            selectedImageUri = Uri.parse(existingUri)
            profileImage.load(selectedImageUri) {
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
            }
        }
        profileImageCard.setOnClickListener {
            if (!isEditMode) {
                isEditMode = true
                updateUiMode(nameEdit)
                renderCustomFields(customFieldsContainer, person)
            }
            showPhotoOptionsPopup()
        }
        findViewById<Button>(R.id.btnSelectGroups).setOnClickListener {
            showGroupSelectionDialog(selectedGroupsText)
        }
        findViewById<Button>(R.id.btnSelectTheme).setOnClickListener {
            showThemeSelectionDialog(tvSelectedTheme)
        }
        btnToggleEdit.setOnClickListener {
            isEditMode = true
            updateUiMode(nameEdit)
            renderCustomFields(customFieldsContainer, person)
        }
        findViewById<Button>(R.id.btnSaveProfile).setOnClickListener {
            saveAndFinish(nameEdit)
        }
        setupNavigationDrawer()
        ColorHelper.applySettings(this)
        updateUiMode(nameEdit)
        val deleteBtn = findViewById<Button>(R.id.btnDeletePerson)
        var deleteClicks = 0
        deleteBtn.setOnClickListener {
            deleteClicks++
            if (deleteClicks >= 8) {
                people.removeAt(personIndex)
                saveData()
                Toast.makeText(this, getString(R.string.member_deleted), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                deleteBtn.text = getString(R.string.delete_member_8x).replace("8x", "${8 - deleteClicks}x")
            }
        }
        val archiveBtn = findViewById<Button>(R.id.btnArchivePerson)
        var archiveClicks = 0
        archiveBtn.setOnClickListener {
            val currentPerson = people[personIndex]
            if (currentPerson.isArchived) {
                currentPerson.isArchived = false
                saveData()
                Toast.makeText(this, getString(R.string.member_unarchived), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                archiveClicks++
                if (archiveClicks >= 5) {
                    currentPerson.isArchived = true
                    if (currentPerson.isFront) {
                        currentPerson.isFront = false
                        sessions.forEach {
                            if ((it.personId == currentPerson.id || (it.personId == null && it.personName == currentPerson.name)) && it.endTime == null) {
                                it.endTime = System.currentTimeMillis()
                            }
                        }
                    }
                    saveData()
                    Toast.makeText(this, getString(R.string.member_archived), Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    archiveBtn.text = getString(R.string.archive_member_5x).replace("5x", "${5 - archiveClicks}x")
                }
            }
        }
    }
    private fun captureInitialState() {
        val person = people[personIndex]
        initialName = findViewById<TextInputEditText>(R.id.editProfileName).text.toString()
        initialColor = selectedColor
        initialImageUri = selectedImageUri?.toString()
        initialGroupIds = selectedGroupIds.toList()
        initialThemeId = selectedThemeId
        initialCustomFields = person.safeCustomFields.toMap()
        initialHiddenFields = person.safeHiddenFields.toList()
        initialIncludeInStats = findViewById<SwitchCompat>(R.id.switchIncludeInStats).isChecked
    }
    private fun hasChanges(): Boolean {
        if (!isEditMode) return false
        val currentName = findViewById<TextInputEditText>(R.id.editProfileName).text.toString()
        val currentIncludeInStats = findViewById<SwitchCompat>(R.id.switchIncludeInStats).isChecked
        val currentCustomFields = mutableMapOf<String, String>()
        customFieldEdits.forEach { (id, edit) -> currentCustomFields[id] = edit.text.toString().trim() }
        val currentHidden = mutableListOf<String>()
        customFieldVisibilities.forEach { (id, visible) -> if (!visible) currentHidden.add(id) }
        return currentName != initialName ||
                selectedColor != initialColor ||
                selectedImageUri?.toString() != initialImageUri ||
                selectedGroupIds != initialGroupIds ||
                selectedThemeId != initialThemeId ||
                currentCustomFields.any { initialCustomFields[it.key] != it.value } ||
                currentHidden != initialHiddenFields ||
                currentIncludeInStats != initialIncludeInStats
    }
    private var initialThemeId: String? = null
    private fun saveAndFinish(nameEdit: EditText) {
        val person = people[personIndex]
        val oldName = person.name
        val newName = nameEdit.text.toString().trim()
        val updatedCustomFields = person.safeCustomFields.toMutableMap()
        val hiddenList = mutableListOf<String>()
        customFieldEdits.forEach { (fieldId, editText) ->
            val newValue = editText.text.toString().trim()
            val isVisible = customFieldVisibilities[fieldId] ?: false
            val fieldDef = customFieldsSettings.find { it.getUniqueId() == fieldId }
            if (isVisible) {
                if (fieldDef != null && newValue == fieldDef.template) {
                    updatedCustomFields.remove(fieldId)
                    updatedCustomFields.remove(fieldDef.name)
                } else {
                    updatedCustomFields[fieldId] = newValue
                    if (fieldDef != null) updatedCustomFields.remove(fieldDef.name)
                }
            } else {
                updatedCustomFields.remove(fieldId)
                if (fieldDef != null) updatedCustomFields.remove(fieldDef.name)
            }
        }
        customFieldVisibilities.forEach { (fieldId, isVisible) ->
            if (!isVisible) {
                hiddenList.add(fieldId)
            }
        }
        val updatedPerson = person.copy(
            name = newName,
            profileColor = selectedColor,
            profilePictureUri = selectedImageUri?.toString(),
            groupIds = selectedGroupIds,
            linkedThemeId = selectedThemeId,
            customFields = updatedCustomFields,
            hiddenFields = hiddenList,
            excludeFromStats = !findViewById<SwitchCompat>(R.id.switchIncludeInStats).isChecked,
            isArchived = person.isArchived
        )
        people[personIndex] = updatedPerson
        sessions.forEach {
            if (it.personId == person.id) {
                it.personName = newName
            } else if (it.personId == null && it.personName == oldName) {
                it.personId = person.id
                it.personName = newName
            }
        }
        saveData()
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        if (isEditMode) finish()
    }
    override fun onResume() {
        super.onResume()
    }
    private fun updateUiMode(nameEdit: EditText) {
        val textColor = ColorHelper.getTextColor(this)
        nameEdit.isFocusable = isEditMode
        nameEdit.isFocusableInTouchMode = isEditMode
        nameEdit.setTextColor(textColor)
        val nameLayout = nameEdit.parent.parent as? com.google.android.material.textfield.TextInputLayout
        nameLayout?.let {
            it.defaultHintTextColor = android.content.res.ColorStateList.valueOf(textColor)
            it.hintTextColor = android.content.res.ColorStateList.valueOf(textColor)
        }
        findViewById<ImageView>(R.id.editPhotoBadge).visibility = if (isEditMode) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnToggleEditMode).visibility = if (isEditMode) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.btnSaveProfile).visibility = if (isEditMode) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnSelectGroups).visibility = if (isEditMode) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnSelectTheme).visibility = if (isEditMode) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvSelectedTheme).visibility = if (isEditMode || selectedThemeId != null) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.labelGroepen).visibility = if (isEditMode || selectedGroupIds.isNotEmpty()) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnDeletePerson).visibility = if (isEditMode) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnArchivePerson).let { btn ->
            btn.visibility = if (isEditMode) View.VISIBLE else View.GONE
            val person = people[personIndex]
            btn.text = if (person.isArchived) getString(R.string.unarchive_member) else getString(R.string.archive_member_5x)
        }
        findViewById<SwitchCompat>(R.id.switchIncludeInStats).visibility = if (isEditMode) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.selectedGroupsText).setTextColor(textColor)
        findViewById<TextView>(R.id.labelGroepen).setTextColor(textColor)
        findViewById<SwitchCompat>(R.id.switchIncludeInStats).setTextColor(textColor)
        try {
            val btnViewNotes = findViewById<Button>(R.id.btnViewNotes)
            btnViewNotes.setOnClickListener {
                val person = people.getOrNull(personIndex) ?: return@setOnClickListener
                val intent = android.content.Intent(this, MemberNotesActivity::class.java)
                intent.putExtra("person_id", person.id)
                intent.putExtra("person_name", person.name)
                startActivity(intent)
            }
            val btnViewMood = findViewById<Button>(R.id.btnViewMood)
            btnViewMood.setOnClickListener {
                val person = people.getOrNull(personIndex) ?: return@setOnClickListener
                val intent = android.content.Intent(this, MemberMoodActivity::class.java)
                intent.putExtra("person_id", person.id)
                intent.putExtra("person_name", person.name)
                startActivity(intent)
            }
            val btnViewTodo = findViewById<Button>(R.id.btnViewTodo)
            btnViewTodo.setOnClickListener {
                val person = people.getOrNull(personIndex) ?: return@setOnClickListener
                val intent = android.content.Intent(this, MemberTodoActivity::class.java)
                intent.putExtra("person_id", person.id)
                intent.putExtra("person_name", person.name)
                startActivity(intent)
            }
            findViewById<Button>(R.id.btnViewFront).setOnClickListener {
                val person = people.getOrNull(personIndex) ?: return@setOnClickListener
                val intent = android.content.Intent(this, MemberFrontHistoryActivity::class.java)
                intent.putExtra("person_id", person.id)
                startActivity(intent)
            }
            findViewById<View>(R.id.btnMemberPreferences).setOnClickListener {
                val person = people.getOrNull(personIndex) ?: return@setOnClickListener
                val intent = android.content.Intent(this, MemberPreferencesActivity::class.java)
                intent.putExtra("person_id", person.id)
                startActivity(intent)
            }
        } catch (_: Exception) { }
    }
    private fun renderCustomFields(container: LinearLayout, person: Person) {
        container.removeAllViews()
        customFieldEdits.clear()
        customFieldsSettings.forEach { field ->
            val fieldId = field.getUniqueId()
            val fieldName = if (field.name.isEmpty()) getString(R.string.unnamed_field) else field.name
            val isVisible = customFieldVisibilities[fieldId] ?: false
            val savedValue = person.safeCustomFields[fieldId] ?: person.safeCustomFields[field.name]
            var displayValue = savedValue ?: ""
            if (savedValue == null && field.template.isNotEmpty()) {
                displayValue = field.template
            }
            if (!isEditMode && (!isVisible || displayValue.isEmpty())) return@forEach
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (16 * resources.displayMetrics.density).toInt()
                }
            }
            if (!isEditMode) {
                val label = TextView(this).apply {
                    text = fieldName
                    textSize = 12f
                    setTextColor(ColorHelper.getTextColor(this@ProfileActivity))
                    alpha = 0.6f
                }
                row.addView(label)
                val markdownTextView = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    setTextColor(ColorHelper.getTextColor(this@ProfileActivity))
                    movementMethod = android.text.method.LinkMovementMethod.getInstance()
                }
                val processedValue = displayValue.replace("\n", "  \n")
                markwon.setMarkdown(markdownTextView, processedValue)
                row.addView(markdownTextView)
                val mediaContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                MediaEmbedHelper.addEmbedsToContainer(mediaContainer, displayValue)
                row.addView(mediaContainer)
            } else {
                val horizontalLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                val textLayout = TextInputLayout(this).apply {
                    hint = fieldName
                    boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_NONE
                    boxStrokeWidth = 0
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val editText = TextInputEditText(this).apply {
                    setText(displayValue)
                    setTextColor(ColorHelper.getTextColor(this@ProfileActivity))
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    setHorizontallyScrolling(false)
                    setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION)
                    maxLines = 10
                    background = null
                    setPadding(0, paddingTop, 0, paddingBottom)
                }
                textLayout.addView(editText)
                customFieldEdits[fieldId] = editText
                horizontalLayout.addView(textLayout)
                val toggle = SwitchCompat(this).apply {
                    isChecked = isVisible
                    setOnCheckedChangeListener { _, checked ->
                        customFieldVisibilities[fieldId] = checked
                    }
                }
                val toggleLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = 16.dpToPx()
                }
                horizontalLayout.addView(toggle, toggleLp)
                row.addView(horizontalLayout)
            }
            container.addView(row)
        }
    }
    private fun showHexColorDialog(preview: View) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, 0)
        }
        val dialogPreview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (40 * resources.displayMetrics.density).toInt()).apply {
                bottomMargin = (16 * resources.displayMetrics.density).toInt()
            }
            setBackgroundColor(selectedColor)
        }
        container.addView(dialogPreview)
        val hexStr = String.format("#%06X", (0xFFFFFF and selectedColor))
        val input = EditText(this).apply {
            setText(hexStr)
            hint = "#RRGGBB"
            isSingleLine = true
            filters = arrayOf(android.text.InputFilter.LengthFilter(7))
        }
        container.addView(input)
        val hsv = FloatArray(3)
        Color.colorToHSV(selectedColor, hsv)
        val hueSeek = SeekBar(this).apply { max = 360; progress = hsv[0].toInt() }
        val satSeek = SeekBar(this).apply { max = 100; progress = (hsv[1] * 100).toInt() }
        val valSeek = SeekBar(this).apply { max = 100; progress = (hsv[2] * 100).toInt() }
        container.addView(TextView(this).apply { text = "Hue (Kleurtoon)" })
        container.addView(hueSeek)
        container.addView(TextView(this).apply { text = "Saturation (Verzadiging)" })
        container.addView(satSeek)
        container.addView(TextView(this).apply { text = "Brightness (Helderheid)" })
        container.addView(valSeek)
        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val hex = s.toString().trim()
                if (hex.length == 7 && hex.startsWith("#")) {
                    try {
                        val color = hex.toColorInt()
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
                    input.setText(hex.lowercase())
                    input.addTextChangedListener(watcher)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }
        hueSeek.setOnSeekBarChangeListener(seekListener)
        satSeek.setOnSeekBarChangeListener(seekListener)
        valSeek.setOnSeekBarChangeListener(seekListener)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_color_hex))
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                try {
                    selectedColor = input.text.toString().trim().toColorInt()
                    preview.setBackgroundColor(selectedColor)
                    if (!isEditMode) {
                        isEditMode = true
                        updateUiMode(findViewById(R.id.editProfileName))
                        renderCustomFields(findViewById(R.id.customFieldsProfileContainer), people[personIndex])
                    }
                } catch (_: Exception) {
                    Toast.makeText(this, getString(R.string.invalid_color_code), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
        input.setTextColor(ColorHelper.getTextColor(this))
        applyTextColorToLabels(container)
    }
    private fun applyTextColorToLabels(viewGroup: ViewGroup) {
        val textColor = ColorHelper.getTextColor(this)
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is TextView) child.setTextColor(textColor)
            else if (child is ViewGroup) applyTextColorToLabels(child)
        }
    }
    private fun updateProfileImage(uri: Uri?) {
        val person = people.getOrNull(personIndex) ?: return
        val profileImage = findViewById<ImageView>(R.id.profileImage)
        if (uri == null) {
            selectedImageUri = null
            person.sourcePictureUri = null
            profileImage.load(android.R.drawable.ic_menu_gallery)
            return
        }
        val scheme = uri.scheme
        if (scheme == "http" || scheme == "https") {
            lifecycleScope.launch {
                val localUriStr = ImageHelper.downloadAndSaveProfilePicture(this@ProfileActivity, uri.toString(), person.id)
                if (localUriStr != null) {
                    val localUri = Uri.parse(localUriStr)
                    person.sourcePictureUri = localUri.toString()
                    startCropActivity(localUri)
                } else {
                    selectedImageUri = uri
                    person.sourcePictureUri = uri.toString()
                    profileImage.load(uri.toString()) {
                        crossfade(true)
                        placeholder(android.R.drawable.ic_menu_gallery)
                        error(android.R.drawable.ic_menu_report_image)
                    }
                }
            }
        } else {
            val internalUri = saveImageToInternalStorage(uri, person.id, isSource = true)
            if (internalUri != null) {
                person.sourcePictureUri = internalUri.toString()
                startCropActivity(internalUri)
            }
        }
    }
    private fun saveImageToInternalStorage(uri: Uri, personId: String, isSource: Boolean): Uri? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val suffix = if (isSource) "source" else "crop"
            val file = File(filesDir, "profile_${personId}_${suffix}.png")
            FileOutputStream(file).use { output -> inputStream.use { input -> input.copyTo(output) } }
            Uri.fromFile(file)
        } catch (e: Exception) { null }
    }
    private fun showPhotoOptionsPopup() {
        val person = people.getOrNull(personIndex)
        val imageView = findViewById<ImageView>(R.id.profileImage)
        val hasImage = !selectedImageUri?.toString().isNullOrBlank() ||
                !person?.profilePictureUri.isNullOrBlank()
        val options = mutableListOf<String>()
        options.add(getString(R.string.upload_new_photo))
        options.add(getString(R.string.paste_link_url))
        if (hasImage) {
            options.add(getString(R.string.edit_current_picture))
            options.add(getString(R.string.download_png))
            options.add(getString(R.string.download_jpg))
        }
        options.add(getString(R.string.delete_photo))
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.adjust_profile_photo))
            .setItems(options.toTypedArray()) { _, which ->
                val selectedOption = options[which]
                when (selectedOption) {
                    getString(R.string.upload_new_photo) -> pickImage.launch("image/*")
                    getString(R.string.paste_link_url) -> showUrlInputDialog()
                    getString(R.string.edit_current_picture) -> {
                        val uri = person?.sourcePictureUri?.let { Uri.parse(it) }
                            ?: selectedImageUri
                            ?: person?.profilePictureUri?.let { Uri.parse(it) }
                        uri?.let { startCropActivity(it) }
                    }
                    getString(R.string.download_png) -> {
                        val fileName = "profile_${person?.name?.replace(" ", "_") ?: "member"}_${System.currentTimeMillis()}.png"
                        downloadPngLauncher.launch(fileName)
                    }
                    getString(R.string.download_jpg) -> {
                        val fileName = "profile_${person?.name?.replace(" ", "_") ?: "member"}_${System.currentTimeMillis()}.jpg"
                        downloadLauncher.launch(fileName)
                    }
                    getString(R.string.delete_photo) -> updateProfileImage(null)
                }
            }
            .create()
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }
    private fun saveImageToUri(uri: Uri, format: Bitmap.CompressFormat) {
        val imageView = findViewById<ImageView>(R.id.profileImage)
        val drawable = imageView.drawable ?: return
        val bitmap = try {
            drawable.toBitmap()
        } catch (e: Exception) {
            null
        } ?: return
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(format, 95, out)
            }
            Toast.makeText(this, getString(R.string.entry_saved), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_saving), Toast.LENGTH_SHORT).show()
        }
    }
    private fun showUrlInputDialog() {
        val input = EditText(this).apply { hint = "https://example.com/foto.jpg" }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.photo_via_link))
            .setView(input)
            .setPositiveButton(getString(R.string.load)) { _, _ ->
                updateProfileImage(Uri.parse(input.text.toString().trim()))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
        input.setTextColor(ColorHelper.getTextColor(this))
    }
    private fun showGroupSelectionDialog(textView: TextView) {
        if (groups.isEmpty()) return
        val sortedGroups = groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val groupNames = sortedGroups.map { it.name }.toTypedArray()
        val checkedItems = BooleanArray(sortedGroups.size) { i -> selectedGroupIds.contains(sortedGroups[i].id) }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_groups))
            .setMultiChoiceItems(groupNames, checkedItems) { _, which, isChecked ->
                val id = sortedGroups[which].id
                if (isChecked) selectedGroupIds.add(id) else selectedGroupIds.remove(id)
            }
            .setPositiveButton(getString(R.string.done)) { _, _ -> updateGroupsText(textView) }
            .create()
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }
    private fun updateGroupsText(textView: TextView) {
        if (selectedGroupIds.isEmpty()) {
            textView.text = getString(R.string.no_groups_selected)
        } else {
            val names = groups.filter { selectedGroupIds.contains(it.id) }.map { it.name }
            textView.text = getString(R.string.selected_groups_label, names.joinToString(", "))
        }
    }
    private fun showThemeSelectionDialog(textView: TextView) {
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val themesJson = sp.getString("saved_themes", "[]") ?: "[]"
        val themes: List<AppTheme> = Gson().fromJson(themesJson, object : TypeToken<List<AppTheme>>() {}.type) ?: emptyList()
        val options = mutableListOf<String>()
        options.add(getString(R.string.theme_none))
        options.addAll(themes.map { it.name })
        val currentIdx = if (selectedThemeId == null) 0 else themes.indexOfFirst { it.id == selectedThemeId } + 1
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.label_link_theme)
            .setSingleChoiceItems(options.toTypedArray(), currentIdx) { d, i ->
                selectedThemeId = if (i == 0) null else themes[i - 1].id
                updateThemeText(textView)
                d.dismiss()
            }
            .show()
            .let { ColorHelper.styleAlertDialog(it, this) }
    }
    private fun updateThemeText(textView: TextView) {
        if (selectedThemeId == null) {
            textView.text = getString(R.string.theme_not_linked)
        } else {
            val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
            val themesJson = sp.getString("saved_themes", "[]") ?: "[]"
            val themes: List<AppTheme> = Gson().fromJson(themesJson, object : TypeToken<List<AppTheme>>() {}.type) ?: emptyList()
            val theme = themes.find { it.id == selectedThemeId }
            textView.text = getString(R.string.theme_linked_label, theme?.name ?: "Unknown")
        }
    }
    private fun loadData() {
        people = MemberHelper.loadAllPeople(this)
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val gson = Gson()
        val groupsJson = sharedPref.getString("groups_list", "[]")
        groups = gson.fromJson(groupsJson, object : TypeToken<MutableList<Group>>() {}.type) ?: mutableListOf()
        val sessionsJson = sharedPref.getString("sessions_list", "[]")
        sessions = gson.fromJson(sessionsJson, object : TypeToken<MutableList<FrontSession>>() {}.type) ?: mutableListOf()
    }
    private fun saveData() {
        MemberHelper.savePeople(this, people)
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val gson = com.google.gson.GsonBuilder().disableHtmlEscaping().create()
        sharedPref.edit(commit = true) {
            putString("groups_list", gson.toJson(groups))
            putString("sessions_list", gson.toJson(sessions))
        }
    }
    private fun updateColorPreview(preview: View) {
        preview.setBackgroundColor(selectedColor)
    }
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
