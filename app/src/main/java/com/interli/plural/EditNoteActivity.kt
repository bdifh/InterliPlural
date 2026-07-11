package com.interli.plural

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsControllerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.CustomNode
import org.commonmark.node.Node

class EditNoteActivity : BaseActivity() {

    private var noteId: String? = null
    private lateinit var notes: MutableList<DiaryNote>
    private lateinit var bundles: MutableList<NoteBundle>
    private lateinit var people: List<Person>
    private lateinit var editTitle: EditText
    private lateinit var editContent: EditText
    private var selectedBundleName: String? = null
    private lateinit var markdownView: TextView
    private lateinit var linkedMembersText: TextView
    private lateinit var selectedBundleText: TextView
    private lateinit var cbProfileOnly: android.widget.CheckBox
    private lateinit var markwon: Markwon

    private lateinit var allTodoLists: List<TodoList>
    private var selectedTodoId: String? = null
    private lateinit var linkedTodoText: TextView
    private var selectedMemberIds = mutableListOf<String>()
    private var isEditMode = false
    private var deleteClicks = 0
    
    private var initialTitle = ""
    private var initialContent = ""
    private var initialBundle = ""
    private var initialMemberIds = listOf<String>()

    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { performPdfExport(it) }
    }

    private val docLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/msword")) { uri ->
        uri?.let { performDocExport(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { performImport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_note)
        
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasChanges()) {
                    showUnsavedChangesDialog { finish() }
                } else {
                    finish()
                }
            }
        })

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true

        val markwon = Markwon.builder(this)
            .usePlugin(TablePlugin.create(this))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(CoilImagesPlugin.create(this))
            .usePlugin(LinkifyPlugin.create())
            .build()

        noteId = intent.getStringExtra("note_id")
        loadData()

        editTitle = findViewById(R.id.editNoteTitle)
        editContent = findViewById(R.id.editNoteContent)
        markdownView = findViewById(R.id.markdownNoteView)
        linkedMembersText = findViewById(R.id.linkedMembersText)
        selectedBundleText = findViewById(R.id.selectedBundleText)
        cbProfileOnly = findViewById(R.id.cbProfileOnlyNote)
        val btnDelete = findViewById<Button>(R.id.btnDeleteNote)
        val btnEditMode = findViewById<Button>(R.id.btnEditNoteMode)
        val btnSave = findViewById<Button>(R.id.btnSaveNote)
        val btnLink = findViewById<Button>(R.id.btnLinkMembers)
        val btnSelectBundle = findViewById<Button>(R.id.btnSelectBundle)
        val btnReply = findViewById<Button>(R.id.btnReply)
        val btnMarkRead = findViewById<Button>(R.id.btnMarkRead)
        
        val btnExportPopup = findViewById<Button>(R.id.btnExportNotePopup)
        val btnImp = findViewById<Button>(R.id.btnImportNote)

        linkedTodoText = findViewById(R.id.linkedTodoText)
        val btnLinkTodo = findViewById<Button>(R.id.btnLinkTodo)

        btnLinkTodo.setOnClickListener {
            showTodoSelectionDialog()
        }

        val existingNote = notes.find { it.id == noteId }
        if (existingNote != null) {
            isEditMode = false
            editTitle.setText(existingNote.title)
            editContent.setText(existingNote.content)
            selectedBundleName = existingNote.bundleName
            cbProfileOnly.isChecked = existingNote.isProfileOnly
            selectedMemberIds = existingNote.linkedMemberIds?.toMutableList() ?: mutableListOf()
            btnDelete.visibility = View.VISIBLE
            btnEditMode.visibility = View.VISIBLE
            btnSave.visibility = View.GONE

            btnExportPopup.visibility = View.VISIBLE
            btnImp.visibility = View.GONE

            selectedTodoId = existingNote.linkedTodoListId
            updateTodoLinkText()
            
            btnExportPopup.setOnClickListener { showExportPopup() }

            if (existingNote.senderId != null) {
                btnReply.visibility = View.VISIBLE
                btnReply.setOnClickListener {
                    val intent = android.content.Intent(this, SendMessageActivity::class.java)
                    if (existingNote.senderId != "anonymous") {
                        intent.putExtra("target_id", existingNote.senderId)
                    }
                    existingNote.linkedMemberIds?.firstOrNull()?.let { 
                        intent.putExtra("from_id", it)
                    }
                    intent.putExtra("reply_to_id", existingNote.id)
                    intent.putExtra("reply_content", existingNote.content)
                    startActivity(intent)
                }

                btnMarkRead.visibility = View.VISIBLE
                btnMarkRead.setOnClickListener {
                    markMessageAsRead(existingNote)
                }
            } else {
                val content = existingNote.content
                val fromPrefixEn = "From "
                val fromPrefixNl = "Van "
                
                val senderName = if (content.startsWith(fromPrefixEn)) {
                    content.substringAfter(fromPrefixEn).substringBefore(":")
                } else if (content.startsWith(fromPrefixNl)) {
                    content.substringAfter(fromPrefixNl).substringBefore(":")
                } else null

                if (senderName != null) {
                    val sender = people.find { it.name.equals(senderName, ignoreCase = true) }
                    if (sender != null) {
                        btnReply.visibility = View.VISIBLE
                        btnReply.setOnClickListener {
                            val intent = android.content.Intent(this, SendMessageActivity::class.java)
                            intent.putExtra("target_id", sender.id)
                            existingNote.linkedMemberIds?.firstOrNull()?.let { 
                                intent.putExtra("from_id", it)
                            }
                            intent.putExtra("reply_to_id", existingNote.id)
                            intent.putExtra("reply_content", existingNote.content)
                            startActivity(intent)
                        }
                        
                        btnMarkRead.visibility = View.VISIBLE
                        btnMarkRead.setOnClickListener {
                            markMessageAsRead(existingNote)
                        }
                    } else {
                        btnReply.visibility = View.GONE
                        btnMarkRead.visibility = View.GONE
                    }
                } else {
                    btnReply.visibility = View.GONE
                    btnMarkRead.visibility = View.GONE
                }
            }
        } else {
            isEditMode = true
            btnReply.visibility = View.GONE
            btnMarkRead.visibility = View.GONE
            
            btnExportPopup.visibility = View.GONE
            btnImp.visibility = View.VISIBLE
            btnImp.setOnClickListener {
                importLauncher.launch(arrayOf("text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            }

            val preLink = intent.getStringExtra("pre_link_member_id")
            if (!preLink.isNullOrEmpty()) {
                selectedMemberIds.add(preLink)
            }
        }
        
        initialTitle = editTitle.text.toString()
        initialContent = editContent.text.toString()
        initialBundle = selectedBundleName ?: ""
        initialMemberIds = selectedMemberIds.toList()

        btnEditMode.setOnClickListener {
            isEditMode = true
            updateUiMode()
        }

        btnSave.setOnClickListener {
            saveNote()
        }

        findViewById<Button>(R.id.btnEditNoteBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnDelete.setOnClickListener {
            deleteClicks++
            if (deleteClicks >= 8) {
                deleteNote()
            } else {
                btnDelete.text = getString(R.string.delete_note_8x, 8 - deleteClicks)
            }
        }

        btnLink.setOnClickListener {
            showMemberSelectionDialog()
        }

        btnSelectBundle.setOnClickListener {
            showBundleSelectionDialog()
        }

        ColorHelper.applySettings(this)
        updateUiMode()
        updateLinkedMembersText()
        updateSelectedBundleText()
    }

    private fun showExportPopup() {
        val options = arrayOf("PDF", "Word (.doc)")
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_export))
            .setItems(options) { _, which ->
                val title = editTitle.text.toString().ifEmpty { "Note" }
                if (which == 0) {
                    pdfLauncher.launch("$title.pdf")
                } else {
                    docLauncher.launch("$title.doc")
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showBundleSelectionDialog() {
        val uniqueBundles = (notes.mapNotNull { it.bundleName } + bundles.map { it.name })
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        
        val options = mutableListOf<String>()
        options.add(getString(R.string.action_new_bundle))
        options.addAll(uniqueBundles)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.label_bundle))
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    showNewBundleDialog()
                } else {
                    selectedBundleName = options[which]
                    updateSelectedBundleText()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun updateSelectedBundleText() {
        selectedBundleText.text = selectedBundleName ?: ""
        findViewById<TextView>(R.id.labelBundle).visibility = if (selectedBundleName.isNullOrBlank() && !isEditMode) View.GONE else View.VISIBLE
    }

    private fun showNewBundleDialog() {
        val input = EditText(this)
        val p = (24 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this)
        container.setPadding(p, p/2, p, 0)
        container.addView(input)
        input.hint = getString(R.string.label_bundle)
        input.setTextColor(ColorHelper.getTextColor(this))

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_new_bundle))
            .setView(container)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    selectedBundleName = name
                    updateSelectedBundleText()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun hasChanges(): Boolean {
        if (!isEditMode) return false
        val initialProfileOnly = intent.getStringExtra("note_id")?.let { id ->
            notes.find { it.id == id }?.isProfileOnly
        } ?: false

        return editTitle.text.toString() != initialTitle ||
               editContent.text.toString() != initialContent ||
               (selectedBundleName ?: "") != initialBundle ||
               selectedMemberIds != initialMemberIds ||
               cbProfileOnly.isChecked != initialProfileOnly
    }

    private fun setupMarkwon() {
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
    }

    private fun updateUiMode() {
        val textColor = ColorHelper.getTextColor(this)
        
        editTitle.isEnabled = isEditMode
        
        val layoutContent = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutNoteContent)
        val embedContainer = findViewById<LinearLayout>(R.id.mediaEmbedContainerNote)

        if (isEditMode) {
            layoutContent.visibility = View.VISIBLE
            markdownView.visibility = View.GONE
            editContent.isEnabled = true
            embedContainer.visibility = View.GONE
        } else {
            layoutContent.visibility = View.GONE
            markdownView.visibility = View.VISIBLE
            val content = editContent.text.toString()
            val processedMarkdown = content.replace("\r\n", "\n").replace("\n", "  \n")
            markwon.setMarkdown(markdownView, processedMarkdown)
            markdownView.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            
            MediaEmbedHelper.addEmbedsToContainer(embedContainer, content)
        }

        updateTodoLinkText()

        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val frontEnabled = sp.getBoolean("module_fronting_enabled", true) && sp.getBoolean("sub_fronting_enabled", true)
        if (!frontEnabled) {
            findViewById<View>(R.id.btnLinkMembers).visibility = View.GONE
            findViewById<View>(R.id.labelLinkedMembers).visibility = View.GONE
            findViewById<View>(R.id.linkedMembersText).visibility = View.GONE
        } else {
            findViewById<Button>(R.id.btnLinkMembers).visibility = if (isEditMode) View.VISIBLE else View.GONE
        }
        findViewById<Button>(R.id.btnSelectBundle).visibility = if (isEditMode) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnSaveNote).visibility = if (isEditMode) View.VISIBLE else View.GONE
        cbProfileOnly.visibility = if (isEditMode) View.VISIBLE else View.GONE
        cbProfileOnly.setTextColor(textColor)
        cbProfileOnly.buttonTintList = android.content.res.ColorStateList.valueOf(textColor)

        findViewById<Button>(R.id.btnEditNoteMode).visibility = if (!isEditMode && noteId != null) View.VISIBLE else View.GONE
        
        findViewById<Button>(R.id.btnExportNotePopup).visibility = if (!isEditMode && noteId != null) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnImportNote).visibility = if (isEditMode && noteId == null) View.VISIBLE else View.GONE

        val btnDelete = findViewById<Button>(R.id.btnDeleteNote)
        btnDelete.visibility = if (isEditMode && noteId != null) View.VISIBLE else View.GONE
        if (btnDelete.visibility == View.VISIBLE) {
            deleteClicks = 0
            btnDelete.text = getString(R.string.delete_note_8x, 8)
        }

        editTitle.setTextColor(textColor)
        editContent.setTextColor(textColor)
        markdownView.setTextColor(textColor)
        linkedMembersText.setTextColor(textColor)
        selectedBundleText.setTextColor(textColor)
        findViewById<TextView>(R.id.labelLinkedMembers).setTextColor(textColor)
        findViewById<TextView>(R.id.labelBundle).setTextColor(textColor)

        val layoutTitle = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutNoteTitle)
        
        val hintColor = textColor and 0x00FFFFFF or 0x99000000.toInt()
        val states = android.content.res.ColorStateList.valueOf(hintColor)
        layoutTitle.defaultHintTextColor = states
        layoutContent.defaultHintTextColor = states
    }

    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        
        val notesJson = sharedPref.getString("diary_notes", "[]")
        val type = object : TypeToken<MutableList<DiaryNote>>() {}.type
        notes = Gson().fromJson(notesJson, type) ?: mutableListOf()

        val bundleJson = sharedPref.getString("diary_bundles", "[]")
        val bundleType = object : TypeToken<MutableList<NoteBundle>>() {}.type
        bundles = Gson().fromJson(bundleJson, bundleType) ?: mutableListOf()
        
        notes.forEach { 
            @Suppress("SENSELESS_COMPARISON")
            if (it.linkedMemberIds == null) it.linkedMemberIds = mutableListOf()
        }

        val todoJson = sharedPref.getString("todo_lists", "[]")
        allTodoLists = Gson().fromJson(todoJson, object : TypeToken<List<TodoList>>() {}.type) ?: emptyList()

        people = MemberHelper.loadAllPeople(this)
    }

    private fun showMemberSelectionDialog() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val groupsJson = sharedPref.getString("groups_list", "[]") ?: "[]"
        val groups: List<Group> = Gson().fromJson(groupsJson, object : TypeToken<List<Group>>() {}.type)

        val filteredPeople = people.filter { !it.isArchived && !it.isSysmediaOnly }

        DialogHelper.showMemberSelectionDialog(
            this,
            getString(R.string.action_link_members),
            filteredPeople,
            groups,
            selectedMemberIds
        ) { newList ->
            selectedMemberIds.clear()
            selectedMemberIds.addAll(newList)
            updateLinkedMembersText()
        }
    }

    private fun updateLinkedMembersText() {
        val names = people.filter { selectedMemberIds.contains(it.id) && !it.isArchived && !it.isSysmediaOnly }.map { it.name }.toMutableList()
        
        val existingNote = notes.find { it.id == noteId }
        val recipientInfo = existingNote?.nextFronterRecipient
        
        if (recipientInfo != null) {
            val idx = names.indexOf(recipientInfo)
            if (idx != -1) {
                names[idx] = getString(R.string.next_fronter_format, recipientInfo)
            } else if (!isEditMode) {
                names.add(getString(R.string.next_fronter_format, recipientInfo))
            }
        }

        linkedMembersText.text = if (names.isEmpty()) "" else names.joinToString(", ")
        findViewById<TextView>(R.id.labelLinkedMembers).visibility = if (names.isEmpty() && !isEditMode) View.GONE else View.VISIBLE
    }

    private fun saveNote() {
        val title = editTitle.text.toString().trim()
        val content = editContent.text.toString().trim()

        if (title.isEmpty() && content.isEmpty()) {
            finish()
            return
        }

        if (noteId == null) {
            notes.add(DiaryNote(title = title, content = content, linkedMemberIds = selectedMemberIds, bundleName = selectedBundleName, linkedTodoListId = selectedTodoId, isProfileOnly = cbProfileOnly.isChecked))
        } else {
            val note = notes.find { it.id == noteId }
            if (note != null) {
                note.title = title
                note.content = content
                note.linkedMemberIds = selectedMemberIds
                note.linkedTodoListId = selectedTodoId
                note.isProfileOnly = cbProfileOnly.isChecked
                if (note.bundleName != selectedBundleName) {
                    note.bundleName = selectedBundleName
                    note.bundleId = null
                }
            }
        }

        persistNotes()
        finish()
    }

    private fun deleteNote() {
        notes.removeAll { it.id == noteId }
        persistNotes()
        Toast.makeText(this, getString(R.string.note_deleted), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun markMessageAsRead(note: DiaryNote) {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val linkedIds = note.linkedMemberIds
        
        val updatedPeople = people.map { person ->
            if (linkedIds.contains(person.id)) {
                person.copy(messageRead = true)
            } else person
        }
        
        MemberHelper.savePeople(this, updatedPeople)
        
        Toast.makeText(this, getString(R.string.action_mark_read), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun persistNotes() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = Gson().toJson(notes)
        sharedPref.edit().putString("diary_notes", json).apply()
    }

    private fun performPdfExport(uri: Uri) {
        val title = editTitle.text.toString()
        val content = editContent.text.toString()
        PdfExportHelper.exportNoteToPdf(this, uri, title, content)
    }

    private fun performDocExport(uri: Uri) {
        Thread {
            try {
                val sb = StringBuilder()
                sb.append("<html><body>")
                sb.append("<h1>${editTitle.text}</h1>")
                sb.append("<p>${editContent.text.toString().replace("\n", "<br>")}</p>")
                sb.append("</body></html>")
                contentResolver.openOutputStream(uri)?.use { os -> os.write(sb.toString().toByteArray()) }
                runOnUiThread { Toast.makeText(this, "Word file saved", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun performImport(uri: Uri) {
        Thread {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader().readText()
                    runOnUiThread {
                        editContent.setText(content)
                        Toast.makeText(this, "Content imported", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun showTodoSelectionDialog() {
        val todoTitles = allTodoLists.map { it.title.ifEmpty { getString(R.string.unnamed_note) } }
        DialogHelper.showSearchableListDialog(this, getString(R.string.action_link_todo_list), todoTitles) { selectedTitle ->
            selectedTodoId = allTodoLists.find { it.title == selectedTitle }?.id
            updateTodoLinkText()
        }
    }

    private fun updateTodoLinkText() {
        val todo = allTodoLists.find { it.id == selectedTodoId }
        linkedTodoText.text = todo?.title ?: ""
        findViewById<TextView>(R.id.labelLinkedTodo).visibility = if (selectedTodoId == null && !isEditMode) View.GONE else View.VISIBLE
        linkedTodoText.visibility = if (selectedTodoId == null && !isEditMode) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.btnLinkTodo).visibility = if (isEditMode) View.VISIBLE else View.GONE
    }
}
