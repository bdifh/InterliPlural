package com.interli.plural.core

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.interli.plural.core.ColorHelper
import com.interli.plural.core.SilentUi
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.FrontSession
import com.interli.plural.Group
import com.interli.plural.Person
import com.interli.plural.R
import java.text.SimpleDateFormat
import java.util.*

object DialogHelper {
    fun showSessionDetailsDialog(
        context: Context,
        session: FrontSession,
        people: List<Person>,
        allSessions: MutableList<FrontSession>,
        onUpdate: () -> Unit
    ) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val startStr = sdf.format(Date(session.startTime))
        val endStr = session.endTime?.let { sdf.format(Date(it)) } ?: context.getString(R.string.currently_active)
        val baseMessage = context.getString(R.string.session_details_format, startStr, endStr)
        val message = if (!session.note.isNullOrBlank()) {
            "$baseMessage\n\n${context.getString(R.string.note)}: ${session.note}"
        } else {
            baseMessage
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(session.personName)
            .setMessage(message)
            .setPositiveButton(R.string.close, null)
            .setNegativeButton(R.string.edit, null)
            .create()
        dialog.setOnShowListener {
            SilentUi.disableSoundEffects(dialog.window?.decorView)
            val btnClose = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnEdit = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            val originalButtonColor = btnClose.currentTextColor
            if (btnEdit is com.google.android.material.button.MaterialButton) {
                btnEdit.text = context.getString(R.string.edit)
                btnEdit.setIconResource(android.R.drawable.ic_menu_edit)
                btnEdit.iconTint = android.content.res.ColorStateList.valueOf(originalButtonColor)
                btnEdit.iconPadding = (8 * context.resources.displayMetrics.density).toInt()
                btnEdit.setOnClickListener {
                    dialog.dismiss()
                    showEditSessionDialog(context, session, people, allSessions, onUpdate)
                }
            } else {
                btnEdit.text = context.getString(R.string.edit)
                btnEdit.setTextColor(originalButtonColor)
                btnEdit.setOnClickListener {
                    dialog.dismiss()
                    showEditSessionDialog(context, session, people, allSessions, onUpdate)
                }
            }
            if (btnClose is com.google.android.material.button.MaterialButton) {
                btnClose.setIconResource(android.R.drawable.ic_menu_close_clear_cancel)
                btnClose.iconTint = android.content.res.ColorStateList.valueOf(originalButtonColor)
                btnClose.iconPadding = (8 * context.resources.displayMetrics.density).toInt()
            }
        }
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, context)
    }

    fun showCustomColorPickerDialog(context: Context, currentColor: Int, onColorSelected: (Int) -> Unit) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(currentColor, hsv)
        val preview = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, (40 * context.resources.displayMetrics.density).toInt()).apply { bottomMargin = 8 }
            setBackgroundColor(currentColor)
        }
        container.addView(preview)
        val hexInput = EditText(context).apply {
            setText(String.format("#%06X", (0xFFFFFF and currentColor)))
            setSingleLine(true)
            setTextColor(ColorHelper.getTextColor(context))
        }
        container.addView(hexInput)
        AlertDialog.Builder(context).setTitle(R.string.choose_color_hex).setView(container)
            .setPositiveButton(R.string.save) { _, _ -> onColorSelected(android.graphics.Color.HSVToColor(hsv)) }
            .show().also { ColorHelper.styleAlertDialog(it, context) }
    }

    fun setupColorPicker(context: Context, container: LinearLayout, initialColor: Int?, includeDefault: Boolean = true, onColorSelected: (Int?) -> Unit) {
        val colorOptions = mutableListOf<String?>()
        if (includeDefault) colorOptions.add(null)
        colorOptions.addAll(listOf("#FF5252", "#FF4081", "#E040FB", "#7C4DFF", "#536DFE", "#448AFF", "#40C4FF", "#18FFFF", "#64FFDA", "#69F0AE", "#B2FF59", "#EEFF41", "#FFFF00", "#FFD740", "#FFAB40", "#FF6E40"))

        var selectedColor = initialColor
        fun refresh() {
            container.removeAllViews()
            colorOptions.forEach { hex ->
                val color = hex?.let { android.graphics.Color.parseColor(it) } ?: ColorHelper.getBtnColor(context)
                val dot = View(context).apply {
                    val size = (32 * context.resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = 8 }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(color)
                        if ((hex == null && selectedColor == null) || (hex != null && selectedColor == color)) {
                            setStroke(3, android.graphics.Color.WHITE)
                        }
                    }
                    setOnClickListener { selectedColor = if (hex == null) null else color; onColorSelected(selectedColor); refresh() }
                }
                container.addView(dot)
            }
            val customBtn = ImageButton(context).apply {
            }
            container.addView(customBtn)
        }
        refresh()
    }

    fun showEditSessionDialog(
        context: Context,
        session: FrontSession,
        people: List<Person>,
        allSessions: MutableList<FrontSession>,
        onUpdate: () -> Unit
    ) {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(50, 40, 50, 10)
        val personLabel = TextView(context)
        personLabel.text = context.getString(R.string.label_person)
        personLabel.setTextColor(ColorHelper.getTextColor(context))
        container.addView(personLabel)
        var selectedPersonId = session.personId
        var selectedPersonName = session.personName
        val btnSelectPerson = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = selectedPersonName
            setTextColor(ColorHelper.getTextColor(context))
            setOnClickListener {
                showMemberSelectionDialog(
                    context,
                    context.getString(R.string.select_member),
                    people,
                    loadGroups(context),
                    if (selectedPersonId != null) listOf(selectedPersonId!!) else emptyList(),
                    isMultiSelect = false
                ) { newList ->
                    if (newList.isNotEmpty()) {
                        val p = people.find { it.id == newList[0] }
                        if (p != null) {
                            selectedPersonId = p.id
                            selectedPersonName = p.name
                            text = p.name
                        }
                    }
                }
            }
        }
        container.addView(btnSelectPerson)
        val startLabel = TextView(context)
        startLabel.text = "\n" + context.getString(R.string.label_start_time)
        startLabel.setTextColor(ColorHelper.getTextColor(context))
        container.addView(startLabel)
        val btnStartRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val btnStartDate = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        val btnStartTime = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val startCal = Calendar.getInstance().apply { timeInMillis = session.startTime }
        btnStartDate.text = sdfDate.format(startCal.time)
        btnStartTime.text = sdfTime.format(startCal.time)
        btnStartDate.setOnClickListener {
            showDatePicker(context, startCal) {
                btnStartDate.text = sdfDate.format(it.time)
            }
        }
        btnStartTime.setOnClickListener {
            showTimePicker(context, startCal) {
                btnStartTime.text = sdfTime.format(it.time)
            }
        }
        btnStartRow.addView(btnStartDate, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        btnStartRow.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(16, 0) })
        btnStartRow.addView(btnStartTime, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(btnStartRow)
        val endLabel = TextView(context)
        endLabel.text = "\n" + context.getString(R.string.label_end_time_optional)
        endLabel.setTextColor(ColorHelper.getTextColor(context))
        container.addView(endLabel)
        val btnEndRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val btnEndDate = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        val btnEndTime = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        val endCal = Calendar.getInstance()
        session.endTime?.let { endCal.timeInMillis = it }
        val activeText = context.getString(R.string.active_click_to_set)
        btnEndDate.text = session.endTime?.let { sdfDate.format(it) } ?: activeText
        btnEndTime.text = session.endTime?.let { sdfTime.format(it) } ?: activeText
        btnEndDate.setOnClickListener {
            showDatePicker(context, endCal) {
                btnEndDate.text = sdfDate.format(it.time)
                if (btnEndTime.text == activeText) btnEndTime.text = sdfTime.format(it.time)
            }
        }
        btnEndTime.setOnClickListener {
            showTimePicker(context, endCal) {
                btnEndTime.text = sdfTime.format(it.time)
                if (btnEndDate.text == activeText) btnEndDate.text = sdfDate.format(it.time)
            }
        }
        btnEndRow.addView(btnEndDate, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        btnEndRow.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(16, 0) })
        btnEndRow.addView(btnEndTime, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(btnEndRow)
        val noteLabel = TextView(context).apply {
            text = "\n" + context.getString(R.string.note)
            setTextColor(ColorHelper.getTextColor(context))
        }
        container.addView(noteLabel)
        val etNote = EditText(context).apply {
            setText(session.note)
            hint = context.getString(R.string.note)
            setTextColor(ColorHelper.getTextColor(context))
        }
        container.addView(etNote)
        SilentUi.disableSoundEffects(container)
        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.dialog_edit_front_title))
            .setView(container)
            .setPositiveButton(R.string.save, null)
            .setNeutralButton(context.getString(R.string.delete), null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            SilentUi.disableSoundEffects(dialog.window?.decorView)
            val btnDelete = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            val textColor = ColorHelper.getTextColor(context)
            if (btnDelete is com.google.android.material.button.MaterialButton) {
                btnDelete.setIconResource(android.R.drawable.ic_menu_delete)
                btnDelete.iconTint = android.content.res.ColorStateList.valueOf(textColor)
                btnDelete.iconPadding = (8 * context.resources.displayMetrics.density).toInt()
            }
            var deleteClicks = 0
            btnDelete.setOnClickListener {
                deleteClicks++
                if (deleteClicks >= 8) {
                    allSessions.remove(session)
                    saveData(context, allSessions, people)
                    onUpdate()
                    dialog.dismiss()
                } else {
                    btnDelete.text = context.getString(R.string.delete_session_8x, 8 - deleteClicks)
                }
            }
            val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnCancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            if (btnCancel is com.google.android.material.button.MaterialButton) {
                btnCancel.setIconResource(android.R.drawable.ic_menu_close_clear_cancel)
                btnCancel.iconTint = android.content.res.ColorStateList.valueOf(textColor)
                btnCancel.iconPadding = (8 * context.resources.displayMetrics.density).toInt()
            }
            val saveAction = {
                session.personName = selectedPersonName
                session.personId = selectedPersonId
                session.startTime = startCal.timeInMillis
                if (btnEndDate.text != activeText) {
                    session.endTime = endCal.timeInMillis
                } else {
                    session.endTime = null
                }
                session.note = etNote.text.toString().ifBlank { null }
                saveData(context, allSessions, people)
                onUpdate()
                dialog.dismiss()
            }
            if (btnSave is com.google.android.material.button.MaterialButton) {
                val contrastColor = ColorHelper.getBtnTextColor(context) or 0xFF000000.toInt()
                btnSave.text = context.getString(R.string.save)
                btnSave.setIconResource(android.R.drawable.ic_menu_save)
                btnSave.iconTint = android.content.res.ColorStateList.valueOf(contrastColor)
                btnSave.iconPadding = (8 * context.resources.displayMetrics.density).toInt()
                btnSave.setOnClickListener { saveAction() }
            } else {
                btnSave.setOnClickListener { saveAction() }
            }
        }
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, context)
    }
    private fun showDatePicker(context: Context, cal: Calendar, onSelected: (Calendar) -> Unit) {
        val dialog = DatePickerDialog(context, { _, year, month, day ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            onSelected(cal)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, context)
    }
    private fun showTimePicker(context: Context, cal: Calendar, onSelected: (Calendar) -> Unit) {
        val dialog = TimePickerDialog(context, { _, hour, minute ->
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            onSelected(cal)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true)
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, context)
    }
    private fun loadGroups(context: Context): List<Group> {
        val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val json = sharedPref.getString("groups_list", "[]") ?: "[]"
        return Gson().fromJson(json, object : com.google.gson.reflect.TypeToken<List<Group>>() {}.type) ?: emptyList()
    }
    private fun saveData(context: Context, sessions: List<FrontSession>, people: List<Person>) {
        val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val gson = Gson()
        val activeIds = sessions.filter { it.endTime == null }.mapNotNull { it.personId }.toSet()
        val activeNames = sessions.filter { it.endTime == null && it.personId == null }.map { it.personName }.toSet()
        people.forEach { person ->
            person.isFront = activeIds.contains(person.id) || activeNames.contains(person.name)
        }
        sharedPref.edit().apply {
            putString("sessions_list", gson.toJson(sessions))
            commit()
        }
        MemberHelper.savePeople(context, people)
    }
    fun showArchivedMembersDialog(context: Context, onDataChanged: () -> Unit) {
        val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val people = MemberHelper.loadAllPeople(context)
        val archived = people.filter { it.isArchived }
        if (archived.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.no_archived_members), Toast.LENGTH_SHORT).show()
            return
        }
        val names = archived.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.archived_members))
            .setItems(names) { _, which ->
                val person = archived[which]
                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle(person.name)
                    .setMessage(context.getString(R.string.unarchive_member) + "?")
                    .setPositiveButton(context.getString(R.string.yes)) { _, _ ->
                        person.isArchived = false
                        val sessionsJson = sharedPref.getString("sessions_list", "[]") ?: "[]"
                        val sessions: List<FrontSession> = gson.fromJson(sessionsJson, object : com.google.gson.reflect.TypeToken<List<FrontSession>>() {}.type)
                        saveData(context, sessions, people)
                        Toast.makeText(context, context.getString(R.string.member_unarchived), Toast.LENGTH_SHORT).show()
                        onDataChanged()
                    }
                    .setNegativeButton(context.getString(R.string.cancel), null)
                    .show()
                    .let { ColorHelper.styleSupportAlertDialog(it, context) }
            }
            .setNegativeButton(context.getString(R.string.close), null)
            .show()
            .let { ColorHelper.styleSupportAlertDialog(it, context) }
    }
    fun showMemberSelectionDialog(
        context: Context,
        title: String,
        allPeople: List<Person>,
        groups: List<Group>,
        initiallySelectedIds: List<String>,
        isMultiSelect: Boolean = true,
        includeArchived: Boolean = false,
        onSelectionConfirmed: (List<String>) -> Unit
    ) {
        val sortedPeople = MemberHelper.getSortedPeople(allPeople, groups, includeArchived)
        val selectedIds = initiallySelectedIds.toMutableList()
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_member_selection, null)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val rvMembers = view.findViewById<RecyclerView>(R.id.rvMembers)
        val btnEveryone = view.findViewById<Button>(R.id.btnSelectEveryone)
        val adapter = MemberSelectionAdapter(context, sortedPeople, selectedIds, isMultiSelect)
        rvMembers.layoutManager = LinearLayoutManager(context)
        rvMembers.adapter = adapter
        btnEveryone.visibility = if (isMultiSelect) View.VISIBLE else View.GONE
        btnEveryone.setOnClickListener {
            val allIds = sortedPeople.map { it.id }
            if (selectedIds.size == allIds.size) {
                selectedIds.clear()
            } else {
                selectedIds.clear()
                selectedIds.addAll(allIds)
            }
            adapter.notifyDataSetChanged()
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.done) { _, _ ->
                onSelectionConfirmed(selectedIds)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        adapter.onSingleSelect = {
            onSelectionConfirmed(selectedIds)
            dialog.dismiss()
        }
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, context)
        val textColor = ColorHelper.getTextColor(context)
        etSearch.setTextColor(textColor)
        etSearch.setHintTextColor(textColor and 0x88FFFFFF.toInt())
        etSearch.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0)
        etSearch.compoundDrawablePadding = (8 * context.resources.displayMetrics.density).toInt()
        etSearch.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(textColor)
    }
    fun showSearchableMultiSelectDialog(
        context: Context,
        title: String,
        items: List<String>,
        initiallySelected: List<String>,
        onSelectionConfirmed: (List<String>) -> Unit
    ) {
        val selectedItems = initiallySelected.toMutableList()
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_member_selection, null)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val rvItems = view.findViewById<RecyclerView>(R.id.rvMembers)
        val btnSelectAll = view.findViewById<Button>(R.id.btnSelectEveryone)
        btnSelectAll.text = context.getString(R.string.all_activities)
        btnSelectAll.setOnClickListener {
            if (selectedItems.size == items.size) {
                selectedItems.clear()
            } else {
                selectedItems.clear()
                selectedItems.addAll(items)
            }
            rvItems.adapter?.notifyDataSetChanged()
        }
        val adapter = object : RecyclerView.Adapter<SearchableItemViewHolder>() {
            var filteredItems = items
            val textColor = ColorHelper.getTextColor(context)
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchableItemViewHolder {
                val v = LayoutInflater.from(context).inflate(R.layout.item_member_selection, parent, false)
                return SearchableItemViewHolder(v)
            }
            override fun onBindViewHolder(holder: SearchableItemViewHolder, position: Int) {
                val item = filteredItems[position]
                holder.tvName.text = item
                holder.tvName.setTextColor(textColor)
                holder.checkBox.visibility = View.VISIBLE
                holder.checkBox.isChecked = selectedItems.contains(item)
                holder.itemView.setOnClickListener {
                    if (selectedItems.contains(item)) {
                        selectedItems.remove(item)
                    } else {
                        selectedItems.add(item)
                    }
                    notifyItemChanged(position)
                }
                holder.checkBox.setOnClickListener {
                    if (selectedItems.contains(item)) {
                        selectedItems.remove(item)
                    } else {
                        selectedItems.add(item)
                    }
                    notifyItemChanged(position)
                }
            }
            override fun getItemCount() = filteredItems.size
            fun filter(query: String) {
                filteredItems = if (query.isBlank()) items else items.filter { it.contains(query, ignoreCase = true) }
                notifyDataSetChanged()
            }
        }
        rvItems.layoutManager = LinearLayoutManager(context)
        rvItems.adapter = adapter
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.done) { _, _ ->
                onSelectionConfirmed(selectedItems)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, context)
        val textColor = ColorHelper.getTextColor(context)
        etSearch.setTextColor(textColor)
        etSearch.setHintTextColor(textColor and 0x88FFFFFF.toInt())
        etSearch.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0)
        etSearch.compoundDrawablePadding = (8 * context.resources.displayMetrics.density).toInt()
        etSearch.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(textColor)
    }
    fun showSearchableListDialog(
        context: Context,
        title: String,
        items: List<String>,
        onSelected: (String) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_member_selection, null)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val rvItems = view.findViewById<RecyclerView>(R.id.rvMembers)
        var currentDialog: AlertDialog? = null
        val adapter = object : RecyclerView.Adapter<SearchableItemViewHolder>() {
            var filteredItems = items
            val textColor = ColorHelper.getTextColor(context)
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchableItemViewHolder {
                val v = LayoutInflater.from(context).inflate(R.layout.item_member_selection, parent, false)
                return SearchableItemViewHolder(v)
            }
            override fun onBindViewHolder(holder: SearchableItemViewHolder, position: Int) {
                holder.tvName.text = filteredItems[position]
                holder.tvName.setTextColor(textColor)
                holder.checkBox.visibility = View.GONE
                holder.itemView.setOnClickListener {
                    onSelected(filteredItems[position])
                    currentDialog?.dismiss()
                }
            }
            override fun getItemCount() = filteredItems.size
            fun filter(query: String) {
                filteredItems = if (query.isBlank()) items else items.filter { it.contains(query, ignoreCase = true) }
                notifyDataSetChanged()
            }
        }
        rvItems.layoutManager = LinearLayoutManager(context)
        rvItems.adapter = adapter
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setPositiveButton(R.string.done, null)
            .setNegativeButton(R.string.cancel, null)
            .setView(view)
            .create()
        currentDialog = dialog
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, context)
        val textColor = ColorHelper.getTextColor(context)
        etSearch.setTextColor(textColor)
        etSearch.setHintTextColor(textColor and 0x88FFFFFF.toInt())
        etSearch.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0)
        etSearch.compoundDrawablePadding = (8 * context.resources.displayMetrics.density).toInt()
        etSearch.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(textColor)
    }
    private class SearchableItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkBox: CheckBox = view.findViewById(R.id.checkBox)
        val tvName: TextView = view.findViewById(R.id.tvName)
    }
    private class MemberSelectionAdapter(
        private val context: Context,
        private val allPeople: List<Person>,
        private val selectedIds: MutableList<String>,
        private val isMultiSelect: Boolean
    ) : RecyclerView.Adapter<MemberSelectionAdapter.ViewHolder>() {
        private var filteredPeople = allPeople.toList()
        private val textColor = ColorHelper.getTextColor(context)
        var onSingleSelect: () -> Unit = {}
        fun filter(query: String) {
            filteredPeople = if (query.isBlank()) {
                allPeople
            } else {
                allPeople.filter { it.name.contains(query, ignoreCase = true) }
            }
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.item_member_selection, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val person = filteredPeople[position]
            holder.tvName.text = person.name
            holder.tvName.setTextColor(textColor)
            holder.checkBox.isChecked = selectedIds.contains(person.id)
            if (!isMultiSelect) {
                holder.checkBox.visibility = View.GONE
            } else {
                holder.checkBox.visibility = View.VISIBLE
            }
            holder.itemView.setOnClickListener {
                if (isMultiSelect) {
                    if (selectedIds.contains(person.id)) {
                        selectedIds.remove(person.id)
                    } else {
                        selectedIds.add(person.id)
                    }
                    notifyItemChanged(position)
                } else {
                    selectedIds.clear()
                    selectedIds.add(person.id)
                    onSingleSelect()
                }
            }
        }
        override fun getItemCount() = filteredPeople.size
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val checkBox: CheckBox = view.findViewById(R.id.checkBox)
            val tvName: TextView = view.findViewById(R.id.tvName)
        }
    }
}
