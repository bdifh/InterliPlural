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

    private lateinit var spinnerFrom: Spinner
    private lateinit var toFieldContainer: View
    private lateinit var tvSelectedTargets: TextView
    private lateinit var cbAnonymous: CheckBox
    private lateinit var editMessageContent: EditText
    private lateinit var editMessageTitle: EditText
    
    private lateinit var people: List<Person>
    private val selectedTargetIds = mutableListOf<String>()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_message)

        ColorHelper.applySettings(this)

        spinnerFrom = findViewById(R.id.spinnerFrom)
        toFieldContainer = findViewById(R.id.toFieldContainer)
        tvSelectedTargets = findViewById(R.id.tvSelectedTargets)
        cbAnonymous = findViewById(R.id.cbAnonymous)
        editMessageContent = findViewById(R.id.editMessageContent)
        editMessageTitle = findViewById(R.id.editMessageTitle)
        
        editMessageTitle.setText(R.string.dialog_send_message_title)

        loadPeople()

        val targetId = intent.getStringExtra("target_id")
        if (targetId != null) {
            selectedTargetIds.add(targetId)
            updateSelectedTargetsText()
        }

        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val groupsJson = sharedPref.getString("groups_list", "[]") ?: "[]"
        val groups: List<Group> = gson.fromJson(groupsJson, object : TypeToken<List<Group>>() {}.type)
        val sortedPeople = MemberHelper.getSortedPeople(people, groups)
        val sortedNames = sortedPeople.map { it.name }

        val adapterFrom = ColorHelper.createThemedAdapter(this, sortedNames)
        spinnerFrom.adapter = adapterFrom
        
        val fromId = intent.getStringExtra("from_id")
        if (fromId != null) {
            val fromPerson = people.find { it.id == fromId }
            if (fromPerson != null) {
                val index = sortedNames.indexOf(fromPerson.name)
                if (index != -1) {
                    spinnerFrom.setSelection(index)
                }
            }
        }

        toFieldContainer.setOnClickListener {
            showTargetSelectionDialog()
        }

        findViewById<Button>(R.id.btnSendMessageBack).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnSend).setOnClickListener {
            sendMessage()
        }
        
        cbAnonymous.setOnCheckedChangeListener { _, isChecked ->
            spinnerFrom.isEnabled = !isChecked
            spinnerFrom.alpha = if (isChecked) 0.5f else 1.0f
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
        val names = people.filter { selectedTargetIds.contains(it.id) && !it.isArchived && !it.isSysmediaOnly }.map { it.name }
        tvSelectedTargets.text = if (names.isEmpty()) "" else names.joinToString(", ")
    }

    private fun sendMessage() {
        val fromName = if (cbAnonymous.isChecked) getString(R.string.action_anonymous) else spinnerFrom.selectedItem?.toString() ?: return
        val content = editMessageContent.text.toString().trim()
        val title = editMessageTitle.text.toString().trim().ifEmpty { getString(R.string.dialog_send_message_title) }

        if (content.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedTargetIds.isEmpty()) {
            Toast.makeText(this, "Please select at least one recipient", Toast.LENGTH_SHORT).show()
            return
        }

        val fromPerson = if (cbAnonymous.isChecked) null else people.find { it.name == fromName }
        val fullMessage = getString(R.string.message_from_placeholder, fromName, content)
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)

        val updatedPeople = people.map { 
            if (selectedTargetIds.contains(it.id)) {
                it.copy(frontMessage = fullMessage, messageRead = false)
            } else it
        }
        MemberHelper.savePeople(this, updatedPeople)

        val newNote = DiaryNote(
            id = UUID.randomUUID().toString(),
            title = title,
            content = fullMessage,
            timestamp = System.currentTimeMillis(),
            linkedMemberIds = selectedTargetIds.toMutableList(),
            senderId = if (cbAnonymous.isChecked) "anonymous" else fromPerson?.id
        )
        
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
