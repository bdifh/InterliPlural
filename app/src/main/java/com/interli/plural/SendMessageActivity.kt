package com.interli.plural

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class SendMessageActivity : BaseActivity() {

    private lateinit var tvSelectedSenders: TextView
    private lateinit var tvSelectedTargets: TextView
    private lateinit var cbAnonymous: CheckBox
    private lateinit var cbProfileOnly: CheckBox
    private lateinit var cbNextFronter: CheckBox
    private lateinit var editMessageContent: EditText
    private lateinit var editMessageTitle: EditText
    
    private lateinit var people: List<Person>
    private val selectedSenderIds = mutableListOf<String>()
    private val selectedTargetIds = mutableListOf<String>()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_message)

        ColorHelper.applySettings(this)

        tvSelectedSenders = findViewById(R.id.tvSelectedSenders)
        tvSelectedTargets = findViewById(R.id.tvSelectedTargets)
        cbAnonymous = findViewById(R.id.cbAnonymous)
        cbProfileOnly = findViewById(R.id.cbProfileOnly)
        cbNextFronter = findViewById(R.id.cbNextFronter)
        editMessageContent = findViewById(R.id.editMessageContent)
        editMessageTitle = findViewById(R.id.editMessageTitle)
        
        editMessageTitle.setText(R.string.dialog_send_message_title)

        val replyContent = intent.getStringExtra("reply_content")
        if (replyContent != null) {
            editMessageContent.setText("\n\n----\n$replyContent")
            editMessageContent.setSelection(0)
        }

        loadPeople()

        val targetId = intent.getStringExtra("target_id")
        if (targetId != null) {
            selectedTargetIds.add(targetId)
            updateSelectedTargetsText()
        }

        // Default to current fronter(s)
        val fronters = people.filter { it.isFront && !it.isArchived && !it.isSysmediaOnly }
        if (fronters.isNotEmpty()) {
            selectedSenderIds.addAll(fronters.map { it.id })
            updateSelectedSendersText()
        }
        
        val fromId = intent.getStringExtra("from_id")
        if (fromId != null) {
            if (!selectedSenderIds.contains(fromId)) {
                selectedSenderIds.add(fromId)
                updateSelectedSendersText()
            }
        }

        tvSelectedSenders.setOnClickListener {
            showSenderSelectionDialog()
        }

        tvSelectedTargets.setOnClickListener {
            showTargetSelectionDialog()
        }

        findViewById<Button>(R.id.btnSendMessageBack).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnSend).setOnClickListener {
            sendMessage()
        }
        
        cbAnonymous.setOnCheckedChangeListener { _, isChecked ->
            tvSelectedSenders.isEnabled = !isChecked
            tvSelectedSenders.alpha = if (isChecked) 0.5f else 1.0f
        }
        
        cbNextFronter.setOnCheckedChangeListener { _, isChecked ->
            tvSelectedTargets.isEnabled = !isChecked
            tvSelectedTargets.alpha = if (isChecked) 0.5f else 1.0f
            if (isChecked) {
                selectedTargetIds.clear()
                updateSelectedTargetsText()
            }
        }
        
        editMessageContent.setHintTextColor(ColorHelper.getTextColor(this) and 0x88FFFFFF.toInt())
        editMessageContent.setTextColor(ColorHelper.getTextColor(this))
        
        editMessageTitle.setHintTextColor(ColorHelper.getTextColor(this) and 0x88FFFFFF.toInt())
        editMessageTitle.setTextColor(ColorHelper.getTextColor(this))

        val textColor = ColorHelper.getTextColor(this)
        val hintColor = textColor and 0x00FFFFFF or 0x99000000.toInt()
        val states = android.content.res.ColorStateList.valueOf(hintColor)
        findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutMessageTitle).defaultHintTextColor = states
        findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutMessageContent).defaultHintTextColor = states
    }

    private fun loadPeople() {
        people = MemberHelper.loadAllPeople(this)
    }

    private fun showSenderSelectionDialog() {
        if (people.isEmpty()) return
        
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val groupsJson = sharedPref.getString("groups_list", "[]") ?: "[]"
        val groups: List<Group> = gson.fromJson(groupsJson, object : TypeToken<List<Group>>() {}.type)

        val filteredPeople = people.filter { !it.isArchived && !it.isSysmediaOnly }

        DialogHelper.showMemberSelectionDialog(
            this,
            getString(R.string.label_from),
            filteredPeople,
            groups,
            selectedSenderIds
        ) { newList ->
            selectedSenderIds.clear()
            selectedSenderIds.addAll(newList)
            updateSelectedSendersText()
        }
    }

    private fun updateSelectedSendersText() {
        val activePeople = people.filter { !it.isArchived && !it.isSysmediaOnly }
        val names = activePeople.filter { selectedSenderIds.contains(it.id) }.map { it.name }
        tvSelectedSenders.text = when {
            names.isEmpty() -> ""
            names.size == activePeople.size -> getString(R.string.action_link_everyone)
            else -> names.joinToString(", ")
        }
    }

    private fun showTargetSelectionDialog() {
        if (people.isEmpty()) return
        
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val groupsJson = sharedPref.getString("groups_list", "[]") ?: "[]"
        val groups: List<Group> = gson.fromJson(groupsJson, object : TypeToken<List<Group>>() {}.type)

        val filteredPeople = people.filter { !it.isArchived && !it.isSysmediaOnly }

        DialogHelper.showMemberSelectionDialog(
            this,
            getString(R.string.label_to),
            filteredPeople,
            groups,
            selectedTargetIds
        ) { newList ->
            selectedTargetIds.clear()
            selectedTargetIds.addAll(newList)
            updateSelectedTargetsText()
        }
    }

    private fun updateSelectedTargetsText() {
        val activePeople = people.filter { !it.isArchived && !it.isSysmediaOnly }
        val names = activePeople.filter { selectedTargetIds.contains(it.id) }.map { it.name }
        tvSelectedTargets.text = when {
            names.isEmpty() -> ""
            names.size == activePeople.size -> getString(R.string.action_link_everyone)
            else -> names.joinToString(", ")
        }
    }

    private fun sendMessage() {
        val fromNames = if (cbAnonymous.isChecked) listOf(getString(R.string.action_anonymous)) 
                        else people.filter { selectedSenderIds.contains(it.id) && !it.isArchived }.map { it.name }
        
        if (fromNames.isEmpty() && !cbAnonymous.isChecked) {
            Toast.makeText(this, "Please select at least one sender", Toast.LENGTH_SHORT).show()
            return
        }
        
        val fromNamesStr = fromNames.joinToString(", ")
        val content = editMessageContent.text.toString().trim()
        val title = editMessageTitle.text.toString().trim().ifEmpty { getString(R.string.dialog_send_message_title) }

        if (content.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedTargetIds.isEmpty() && !cbNextFronter.isChecked) {
            Toast.makeText(this, "Please select at least one recipient", Toast.LENGTH_SHORT).show()
            return
        }

        val fromHeader = getString(R.string.message_from_placeholder, fromNamesStr)
        val fullMessage = "$fromHeader\n\n$content"
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)

        val newNote = DiaryNote(
            id = UUID.randomUUID().toString(),
            title = title,
            content = fullMessage,
            timestamp = System.currentTimeMillis(),
            linkedMemberIds = if (cbNextFronter.isChecked) mutableListOf() else selectedTargetIds.toMutableList(),
            senderId = if (cbAnonymous.isChecked) "anonymous" else selectedSenderIds.firstOrNull(),
            isProfileOnly = cbProfileOnly.isChecked,
            parentNoteId = intent.getStringExtra("reply_to_id")
        )

        if (cbNextFronter.isChecked) {
            prefs.edit { 
                putString("pending_next_fronter_message", fullMessage)
                putString("pending_next_fronter_note_id", newNote.id)
            }
        }
        
        val allNotesJson = prefs.getString("diary_notes", "[]") ?: "[]"
        val allNotes: MutableList<DiaryNote> = try { 
            gson.fromJson(allNotesJson, object : TypeToken<MutableList<DiaryNote>>() {}.type) 
        } catch (_: Exception) { 
            mutableListOf() 
        }
        
        allNotes.add(newNote)
        prefs.edit { putString("diary_notes", gson.toJson(allNotes)) }

        Toast.makeText(this, getString(R.string.message_sent), Toast.LENGTH_SHORT).show()
        finish()
    }
}
