package com.interli.plural.features.relations

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import coil.imageLoader
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.core.DialogHelper
import com.interli.plural.core.PdfExportHelper
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.features.relations.NodeType
import com.interli.plural.features.relations.RelationEdge
import com.interli.plural.features.relations.RelationEnvironment
import com.interli.plural.features.relations.RelationGroup
import com.interli.plural.features.relations.RelationNode
import com.interli.plural.features.relations.RelationsData
import com.interli.plural.features.relations.RelationsMapView
import com.interli.plural.Group
import com.interli.plural.Person
import com.interli.plural.R

private var activeDialog: AlertDialog? = null
class RelationsActivity : BaseActivity() {
    private lateinit var mapView: RelationsMapView
    private var environments = mutableListOf<RelationEnvironment>()
    private var currentEnvIndex = 0
    private val relationsData: RelationsData get() = environments[currentEnvIndex].data
    private val gson = Gson()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relations)
        ColorHelper.applySettings(this)
        setupNavigationDrawer()
        mapView = findViewById(R.id.relationsMapView)
        loadData()
        findViewById<View>(R.id.btnAddNode).setOnClickListener { showAddNodeDialog() }
        findViewById<View>(R.id.btnAddEdge).setOnClickListener { showAddEdgeDialog() }
        findViewById<View>(R.id.btnAddGroup).setOnClickListener { showGroupsListDialog() }
        findViewById<View>(R.id.btnSave).setOnClickListener { saveData() }
        findViewById<View>(R.id.btnExport).setOnClickListener { exportToPdf() }
        mapView.onNodeLongClicked = { node -> showManageNodeDialog(node) }
        mapView.onGroupLongClicked = { group -> showEditGroupDialog(group) }
        mapView.onEdgeLongClicked = { edge -> showEditEdgeDialogFromMap(edge) }
        mapView.onDataChanged = { saveData(silent = true) }
    }
    private fun showEditEdgeDialogFromMap(edge: RelationEdge) {
        activeDialog?.dismiss()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = 16.dpToPx()
            setPadding(p, p, p, p)
        }
        val tagInput = EditText(this).apply {
            hint = getString(R.string.relation_tag)
            setText(edge.tag)
            setTextColor(ColorHelper.getTextColor(this@RelationsActivity))
        }
        val noteInput = EditText(this).apply {
            hint = "Note"
            setText(edge.note)
            setTextColor(ColorHelper.getTextColor(this@RelationsActivity))
        }
        layout.addView(tagInput)
        layout.addView(noteInput)
        activeDialog = AlertDialog.Builder(this)
            .setTitle(R.string.action_edit)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                edge.tag = tagInput.text.toString()
                edge.note = noteInput.text.toString()
                mapView.invalidate()
                saveData(silent = true)
            }
            .setNegativeButton(R.string.delete) { _, _ ->
                relationsData.edges.remove(edge)
                mapView.invalidate()
                saveData(silent = true)
            }
            .setNeutralButton(R.string.cancel, null)
            .show().also { ColorHelper.styleAlertDialog(it, this) }
    }
    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = sharedPref.getString("relations_environments", null)
        if (json != null) {
            val type = object : TypeToken<MutableList<RelationEnvironment>>() {}.type
            environments = try { gson.fromJson(json, type) } catch (_: Exception) { mutableListOf() }
        }
        if (environments.isEmpty()) {
            val legacyJson = sharedPref.getString("relations_data", "{}")
            val legacyData = try { gson.fromJson(legacyJson, RelationsData::class.java) } catch (_: Exception) { RelationsData() }
            environments.add(RelationEnvironment(name = "Main", data = legacyData))
        }
        currentEnvIndex = 0
        setupEnvironmentSpinner()
        syncMemberNodes()
        mapView.setData(relationsData)
    }
    private fun syncMemberNodes() {
        val people = MemberHelper.loadAllPeople(this)
        var changed = false
        relationsData.nodes.forEach { node ->
            if (node.type == NodeType.MEMBER && node.memberId != null) {
                val person = people.find { it.id == node.memberId }
                if (person != null) {
                    if (node.name != person.name || node.color != person.profileColor || node.imageUri != person.profilePictureUri) {
                        node.name = person.name
                        node.color = person.profileColor
                        node.imageUri = person.profilePictureUri
                        changed = true
                    }
                }
            }
        }
        if (changed) saveData(silent = true)
    }
    private fun saveData(silent: Boolean = false) {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = gson.toJson(environments)
        sharedPref.edit().putString("relations_environments", json).apply()
        if (!silent) {
            Toast.makeText(this, getString(R.string.backup_saved), Toast.LENGTH_SHORT).show()
        }
    }
    private fun showAddNodeDialog() {
        activeDialog?.dismiss()
        val options = arrayOf(
            getString(R.string.add_existing_member),
            getString(R.string.add_relationship_orb)
        )
        activeDialog = AlertDialog.Builder(this)
            .setTitle(R.string.add_node)
            .setItems(options) { _, which ->
                if (which == 0) showSelectMemberDialog()
                else showEditRelationshipOrbDialog(null)
            }
            .show()
            .also { ColorHelper.styleAlertDialog(it, this) }
    }
    private fun showEditRelationshipOrbDialog(existingNode: RelationNode?) {
        val isNew = existingNode == null
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = 16.dpToPx()
            setPadding(p, p, p, p)
        }
        val textColor = ColorHelper.getTextColor(this)
        val nameInput = EditText(this).apply {
            hint = getString(R.string.relationship_orb_name)
            setText(existingNode?.name ?: "")
            setTextColor(textColor)
        }
        layout.addView(nameInput)
        val colors = intArrayOf(Color.GRAY, Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.MAGENTA, Color.CYAN, Color.BLACK)
        var selectedColor = existingNode?.color ?: Color.GRAY
        val colorContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
        }
        val scroll = HorizontalScrollView(this).apply {
            addView(colorContainer)
        }
        fun updateColorDots() {
            colorContainer.removeAllViews()
            val dotSize = 36.dpToPx()
            val margin = 8.dpToPx()
            val activeColors = colors.toMutableList()
            if (!activeColors.contains(selectedColor)) activeColors.add(selectedColor)
            activeColors.forEach { color ->
                val dot = View(this@RelationsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply { setMargins(0, 0, margin, 0) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                        if (color == selectedColor) {
                            setStroke(3.dpToPx(), textColor)
                        } else {
                            setStroke(1.dpToPx(), Color.GRAY)
                        }
                    }
                    setOnClickListener {
                        selectedColor = color
                        updateColorDots()
                    }
                }
                colorContainer.addView(dot)
            }
            val customBtn = ImageButton(this@RelationsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize)
                setImageResource(android.R.drawable.ic_menu_edit)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.LTGRAY)
                }
                setOnClickListener {
                    showCustomColorPickerDialog(selectedColor) { newColor ->
                        selectedColor = newColor
                        updateColorDots()
                    }
                }
            }
            colorContainer.addView(customBtn)
        }
        updateColorDots()
        layout.addView(TextView(this).apply { text = getString(R.string.label_color_colon); setTextColor(textColor); setPadding(0, 8.dpToPx(), 0, 0) })
        layout.addView(scroll)
        var selectedImageUri = existingNode?.imageUri
        val imgPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(100.dpToPx(), 100.dpToPx()).apply { 
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 16.dpToPx()
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (selectedImageUri != null) {
                imageLoader.enqueue(coil.request.ImageRequest.Builder(this@RelationsActivity)
                    .data(selectedImageUri)
                    .target(this)
                    .build())
            } else {
                setImageResource(android.R.drawable.ic_menu_gallery)
            }
            setOnClickListener {
                imagePickerCallback = { uri ->
                    selectedImageUri = uri.toString()
                    imageLoader.enqueue(coil.request.ImageRequest.Builder(this@RelationsActivity)
                        .data(uri)
                        .target(this)
                        .build())
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(intent, 1003)
            }
        }
        layout.addView(imgPreview)
        activeDialog = AlertDialog.Builder(this)
            .setTitle(if (isNew) R.string.add_relationship_orb else R.string.action_edit)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text.toString()
                if (name.isNotBlank()) {
                    if (isNew) {
                        val offset = (relationsData.nodes.size * 100f) % 400f
                        val newNode = RelationNode(
                            type = NodeType.RELATIONSHIP_ORB,
                            name = name,
                            color = selectedColor,
                            imageUri = selectedImageUri,
                            x = 300f + offset,
                            y = 300f + (relationsData.nodes.size / 4 * 100f)
                        )
                        relationsData.nodes.add(newNode)
                    } else {
                        existingNode?.name = name
                        existingNode?.color = selectedColor
                        existingNode?.imageUri = selectedImageUri
                    }
                    mapView.setData(relationsData)
                    saveData(silent = true)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .also { ColorHelper.styleAlertDialog(it, this) }
    }
    private var imagePickerCallback: ((android.net.Uri) -> Unit)? = null
    private fun showSelectMemberDialog() {
        val allPeople = MemberHelper.loadAllPeople(this)
        val filteredPeople = allPeople.filter { p -> !p.isArchived }
        if (filteredPeople.isEmpty()) {
            Toast.makeText(this, getString(R.string.all_members_added), Toast.LENGTH_SHORT).show()
            return
        }
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val groupsJson = sharedPref.getString("groups_list", "[]") ?: "[]"
        val groups: List<Group> = gson.fromJson(groupsJson, object : TypeToken<List<Group>>() {}.type) ?: emptyList()
        DialogHelper.showMemberSelectionDialog(
            this,
            getString(R.string.select_member),
            filteredPeople,
            groups,
            emptyList(),
            isMultiSelect = true
        ) { selectedIds ->
            if (selectedIds.isNotEmpty()) {
                val groupOptions = mutableListOf(getString(R.string.no_group_bubble))
                groupOptions.addAll(relationsData.groups.map { it.name })
                groupOptions.add(getString(R.string.connect_to_group_bubble))
                activeDialog = AlertDialog.Builder(this)
                    .setTitle(R.string.action_manage_groups)
                    .setItems(groupOptions.toTypedArray()) { _, which ->
                        selectedIds.forEachIndexed { index, selectedId ->
                            val person = filteredPeople.find { it.id == selectedId } ?: return@forEachIndexed
                            val offset = ((relationsData.nodes.size + index) * 100f) % 400f
                            val startX = 300f + offset
                            val startY = 300f + ((relationsData.nodes.size + index) / 4 * 100f)
                            val newNode = RelationNode(
                                type = NodeType.MEMBER,
                                name = person.name,
                                color = person.profileColor,
                                imageUri = person.profilePictureUri,
                                memberId = person.id,
                                x = startX,
                                y = startY
                            )
                            relationsData.nodes.add(newNode)
                            if (which > 0 && which < groupOptions.size - 1) {
                                relationsData.groups[which - 1].nodeIds.add(newNode.id)
                            } else if (which == groupOptions.size - 1) {
                                showSelectGroupForConnectionDialog(newNode)
                            }
                        }
                        mapView.setData(relationsData)
                        saveData(silent = true)
                    }
                    .show().also { ColorHelper.styleAlertDialog(it, this); activeDialog = it }
            }
        }
    }
    private fun showSelectGroupForConnectionDialog(node: RelationNode) {
        if (relationsData.groups.isEmpty()) return
        val names = relationsData.groups.map { it.name }.toTypedArray()
        activeDialog = AlertDialog.Builder(this)
            .setTitle("${getString(R.string.connect_with)} ${node.name}")
            .setItems(names) { _, which ->
                val edge = RelationEdge(
                    nodeIds = mutableListOf(node.id),
                    groupIds = mutableListOf(relationsData.groups[which].id)
                )
                relationsData.edges.add(edge)
                mapView.invalidate()
                saveData(silent = true)
            }
            .show()
            .also { ColorHelper.styleAlertDialog(it, this); activeDialog = it }
    }
    private fun setupEnvironmentSpinner() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar.removeView(toolbar.findViewWithTag("envSpinner"))
        toolbar.removeView(toolbar.findViewWithTag("cbSmartLayout"))
        val spinner = Spinner(this).apply {
            tag = "envSpinner"
            val options = environments.map { it.name }.toMutableList()
            options.add(getString(R.string.add_new_environment))
            adapter = ColorHelper.createThemedAdapter(this@RelationsActivity, options)
            setSelection(currentEnvIndex)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position == environments.size) {
                        showCreateEnvironmentDialog()
                        setSelection(currentEnvIndex)
                    } else if (position != currentEnvIndex) {
                        currentEnvIndex = position
                        mapView.setData(relationsData)
                        setupEnvironmentSpinner()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            setOnLongClickListener {
                showEnvironmentOptionsDialog()
                true
            }
        }
        val lp = Toolbar.LayoutParams(Toolbar.LayoutParams.WRAP_CONTENT, Toolbar.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        lp.marginStart = 64.dpToPx()
        toolbar.addView(spinner, lp)
        val cbSmartLayout = CheckBox(this).apply {
            tag = "cbSmartLayout"
            text = "Smart"
            isChecked = relationsData.smartLayoutEnabled
            setTextColor(ColorHelper.getTextColor(this@RelationsActivity))
            setOnCheckedChangeListener { _, isChecked ->
                relationsData.smartLayoutEnabled = isChecked
                mapView.setData(relationsData)
                saveData(silent = true)
            }
        }
        val lpCb = Toolbar.LayoutParams(Toolbar.LayoutParams.WRAP_CONTENT, Toolbar.LayoutParams.WRAP_CONTENT)
        lpCb.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        lpCb.marginEnd = 8.dpToPx()
        toolbar.addView(cbSmartLayout, lpCb)
    }
    private fun showCreateEnvironmentDialog() {
        activeDialog?.dismiss()
        val input = EditText(this).apply { hint = getString(R.string.hint_environment_name) }
        activeDialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_new_environment_title)
            .setView(input)
            .setPositiveButton(R.string.action_create) { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    environments.add(RelationEnvironment(name = name))
                    currentEnvIndex = environments.size - 1
                    saveData(silent = true)
                    setupEnvironmentSpinner()
                    mapView.setData(relationsData)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .also { ColorHelper.styleAlertDialog(it, this); activeDialog = it }
    }
    private fun showRenameEnvironmentDialog() {
        activeDialog?.dismiss()
        val currentEnv = environments[currentEnvIndex]
        val input = EditText(this).apply {
            hint = getString(R.string.hint_environment_name)
            setText(currentEnv.name)
        }
        activeDialog = AlertDialog.Builder(this)
            .setTitle(R.string.action_edit)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    currentEnv.name = name
                    saveData(silent = true)
                    setupEnvironmentSpinner()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show().also { ColorHelper.styleAlertDialog(it, this) }
    }
    private fun showDeleteEnvironmentDialog() {
        if (environments.size <= 1) {
            Toast.makeText(this, "Cannot delete the last environment", Toast.LENGTH_SHORT).show()
            return
        }
        activeDialog?.dismiss()
        activeDialog = AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage("Are you sure you want to delete '${environments[currentEnvIndex].name}'?")
            .setPositiveButton(R.string.delete) { _, _ ->
                environments.removeAt(currentEnvIndex)
                currentEnvIndex = 0
                saveData(silent = true)
                setupEnvironmentSpinner()
                mapView.setData(relationsData)
            }
            .setNegativeButton(R.string.cancel, null)
            .show().also { ColorHelper.styleAlertDialog(it, this) }
    }
    private fun showEnvironmentOptionsDialog() {
        val options = arrayOf(getString(R.string.action_edit), getString(R.string.delete))
        activeDialog?.dismiss()
        activeDialog = AlertDialog.Builder(this)
            .setTitle(environments[currentEnvIndex].name)
            .setItems(options) { _, which ->
                if (which == 0) showRenameEnvironmentDialog()
                else showDeleteEnvironmentDialog()
            }
            .show().also { ColorHelper.styleAlertDialog(it, this) }
    }
    private fun showAddEdgeDialog() {
        if (relationsData.nodes.size + relationsData.groups.size < 2) return
        activeDialog?.dismiss()
        val selectedNodeIndices = mutableListOf(-1, -1) 
        val selectedGroupIndices = mutableListOf(-1, -1)
        val textColor = ColorHelper.getTextColor(this)
        val bgColor = ColorHelper.getBgColor(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = 16.dpToPx()
            setPadding(p, p, p, p)
            setBackgroundColor(bgColor)
        }
        val spinnersContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(spinnersContainer)
        fun updateSpinners() {
            spinnersContainer.removeAllViews()
            val allOptions = mutableListOf(getString(R.string.select_member_placeholder))
            allOptions.addAll(relationsData.nodes.map { "👤 ${it.name}" })
            allOptions.addAll(relationsData.groups.map { "📁 ${it.name}" })
            selectedNodeIndices.forEachIndexed { index, _ ->
                val label = TextView(this@RelationsActivity).apply {
                    text = when(index) {
                        0 -> getString(R.string.select_from_node)
                        1 -> getString(R.string.select_to_node)
                        else -> getString(R.string.add_node).replace("+", "").trim() + " ${index + 1}"
                    }
                    setTextColor(textColor)
                    setPadding(0, 8.dpToPx(), 0, 0)
                }
                val spinner = Spinner(this@RelationsActivity).apply {
                    adapter = ColorHelper.createThemedAdapter(this@RelationsActivity, allOptions)
                    val nodeIdx = selectedNodeIndices[index]
                    val groupIdx = selectedGroupIndices[index]
                    if (nodeIdx != -1) setSelection(nodeIdx + 1)
                    else if (groupIdx != -1) setSelection(relationsData.nodes.size + groupIdx + 1)
                    else setSelection(0)
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                            if (pos == 0) {
                                selectedNodeIndices[index] = -1
                                selectedGroupIndices[index] = -1
                            } else if (pos <= relationsData.nodes.size) {
                                selectedNodeIndices[index] = pos - 1
                                selectedGroupIndices[index] = -1
                            } else {
                                selectedNodeIndices[index] = -1
                                selectedGroupIndices[index] = pos - relationsData.nodes.size - 1
                            }
                        }
                        override fun onNothingSelected(p0: AdapterView<*>?) {}
                    }
                }
                spinnersContainer.addView(label)
                spinnersContainer.addView(spinner)
            }
        }
        updateSpinners()
        val btnAddMember = Button(this).apply {
            text = getString(R.string.add_member_to_relation)
            setTextColor(textColor)
            setOnClickListener {
                selectedNodeIndices.add(-1)
                selectedGroupIndices.add(-1)
                updateSpinners()
            }
        }
        layout.addView(btnAddMember)
        val tagInput = EditText(this).apply {
            hint = getString(R.string.relation_tag)
            setTextColor(textColor)
            setHintTextColor(textColor and 0x80FFFFFF.toInt())
        }
        layout.addView(tagInput)
        val noteInput = EditText(this).apply {
            tag = "noteInput"
            hint = "Note"
            setTextColor(textColor)
            setHintTextColor(textColor and 0x80FFFFFF.toInt())
        }
        layout.addView(noteInput)
        val scroll = ScrollView(this).apply {
            addView(layout)
        }
        activeDialog = AlertDialog.Builder(this)
            .setTitle(R.string.add_edge)
            .setView(scroll)
            .setPositiveButton(R.string.action_add) { _, _ ->
                val nodeIds = mutableListOf<String>()
                val groupIds = mutableListOf<String>()
                selectedNodeIndices.forEachIndexed { i, nodeIdx ->
                    if (nodeIdx != -1) nodeIds.add(relationsData.nodes[nodeIdx].id)
                    val groupIdx = selectedGroupIndices[i]
                    if (groupIdx != -1) groupIds.add(relationsData.groups[groupIdx].id)
                }
                if (nodeIds.size + groupIds.size >= 2) {
                    val edge = RelationEdge(
                        nodeIds = nodeIds, 
                        groupIds = groupIds, 
                        tag = tagInput.text.toString(),
                        note = layout.findViewWithTag<EditText>("noteInput")?.text?.toString()
                    )
                    relationsData.edges.add(edge)
                    mapView.setData(relationsData)
                    saveData(silent = true)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show().also { ColorHelper.styleAlertDialog(it, this); activeDialog = it }
    }
    private fun showGroupsListDialog() {
        activeDialog?.dismiss()
        val options = mutableListOf(" + " + getString(R.string.action_new_group))
        options.addAll(relationsData.groups.map { it.name })
        activeDialog = AlertDialog.Builder(this)
            .setTitle(R.string.add_group_bubble)
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) showEditGroupDialog(RelationGroup())
                else showEditGroupDialog(relationsData.groups[which - 1])
            }
            .show()
            .also { ColorHelper.styleAlertDialog(it, this); activeDialog = it }
    }
    private fun showEditGroupDialog(group: RelationGroup) {
        activeDialog?.dismiss()
        val isNew = !relationsData.groups.contains(group)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.group_bubble_name)
            setText(group.name)
        }
        val textColor = ColorHelper.getTextColor(this)
        val colors = intArrayOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.MAGENTA, Color.CYAN, Color.LTGRAY)
        var selectedColor = group.color
        val colorContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
        }
        val scroll = HorizontalScrollView(this).apply {
            addView(colorContainer)
        }
        fun updateDots() {
            colorContainer.removeAllViews()
            val dotSize = 36.dpToPx()
            val margin = 8.dpToPx()
            val activeColors = colors.toMutableList()
            if (!activeColors.contains(selectedColor)) activeColors.add(selectedColor)
            activeColors.forEach { color ->
                val dot = View(this@RelationsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply { setMargins(0, 0, margin, 0) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                        if (color == selectedColor) {
                            setStroke(3.dpToPx(), textColor)
                        } else {
                            setStroke(1.dpToPx(), Color.GRAY)
                        }
                    }
                    setOnClickListener {
                        selectedColor = color
                        updateDots()
                    }
                }
                colorContainer.addView(dot)
            }
            val customBtn = ImageButton(this@RelationsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize)
                setImageResource(android.R.drawable.ic_menu_edit)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.LTGRAY)
                }
                setOnClickListener {
                    showCustomColorPickerDialog(selectedColor) { newColor ->
                        selectedColor = newColor
                        updateDots()
                    }
                }
            }
            colorContainer.addView(customBtn)
        }
        updateDots()
        val cbSnap = CheckBox(this).apply {
            text = getString(R.string.snap_members)
            isChecked = group.snapEnabled
            setTextColor(textColor)
        }
        layout.addView(TextView(this).apply { text = getString(R.string.label_name_colon); setTextColor(textColor) })
        layout.addView(nameInput)
        layout.addView(TextView(this).apply { text = getString(R.string.label_color_colon); setTextColor(textColor); setPadding(0, 8.dpToPx(), 0, 0) })
        layout.addView(scroll)
        layout.addView(cbSnap)
        layout.addView(TextView(this).apply {
            text = getString(R.string.label_linked_members)
            setTextColor(textColor)
            setPadding(0, 16.dpToPx(), 0, 8.dpToPx())
        })
        val checkedIds = group.nodeIds.toMutableSet()
        relationsData.nodes.forEach { node ->
            val cb = CheckBox(this).apply {
                text = node.name
                isChecked = checkedIds.contains(node.id)
                setTextColor(textColor)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) checkedIds.add(node.id)
                    else checkedIds.remove(node.id)
                }
            }
            layout.addView(cb)
        }
        val scrollOuter = ScrollView(this).apply {
            addView(layout)
        }
        activeDialog = AlertDialog.Builder(this)
            .setTitle(if (isNew) getString(R.string.new_group_bubble) else getString(R.string.edit_group_bubble))
            .setView(scrollOuter)
            .setPositiveButton(R.string.save) { _, _ ->
                group.name = nameInput.text.toString()
                group.color = selectedColor
                group.nodeIds = checkedIds.toMutableList()
                group.snapEnabled = cbSnap.isChecked
                if (isNew) relationsData.groups.add(group)
                if (group.snapEnabled && group.nodeIds.isNotEmpty()) {
                    val memberNodes = relationsData.nodes.filter { group.nodeIds.contains(it.id) }
                    val avgX = memberNodes.map { it.x }.average().toFloat()
                    val avgY = memberNodes.map { it.y }.average().toFloat()
                    memberNodes.forEach {
                        it.x = (it.x + avgX) / 2
                        it.y = (it.y + avgY) / 2
                    }
                }
                mapView.setData(relationsData)
                saveData(silent = true)
            }
            .setNegativeButton(if (isNew) R.string.cancel else R.string.delete) { _, _ ->
                if (!isNew) {
                    relationsData.groups.remove(group)
                    relationsData.edges.removeAll { it.groupIds.contains(group.id) }
                }
                mapView.setData(relationsData)
                saveData(silent = true)
            }
            .show()
            .also { ColorHelper.styleAlertDialog(it, this) }
    }
    private fun showManageNodeDialog(node: RelationNode) {
        activeDialog?.dismiss()
        val textColor = ColorHelper.getTextColor(this)
        val bgColor = ColorHelper.getBgColor(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = 16.dpToPx()
            setPadding(p, p, p, p)
            setBackgroundColor(bgColor)
        }
        // Section: Info
        if (node.type == NodeType.RELATIONSHIP_ORB) {
            val nameEdit = EditText(this).apply {
                setText(node.name)
                hint = "Name"
                setTextColor(textColor)
            }
            container.addView(nameEdit)
            nameEdit.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { 
                    node.name = s.toString()
                    mapView.invalidate()
                    saveData(silent = true)
                }
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            })
        }
        // Section: Groups
        val titleGroups = TextView(this).apply {
            text = getString(R.string.action_manage_groups)
            textSize = 16f
            setPadding(0, 16.dpToPx(), 0, 8.dpToPx())
            setTextColor(textColor)
            setTypeface(null, Typeface.BOLD)
        }
        container.addView(titleGroups)
        relationsData.groups.forEach { group ->
            val cb = CheckBox(this).apply {
                text = group.name
                isChecked = group.nodeIds.contains(node.id)
                setTextColor(textColor)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        if (!group.nodeIds.contains(node.id)) group.nodeIds.add(node.id)
                    } else {
                        group.nodeIds.remove(node.id)
                    }
                    mapView.setData(relationsData)
                    saveData(silent = true)
                }
            }
            container.addView(cb)
        }
        // Section: Connections
        val titleConn = TextView(this).apply {
            text = getString(R.string.action_connections)
            textSize = 16f
            setPadding(0, 16.dpToPx(), 0, 8.dpToPx())
            setTextColor(textColor)
            setTypeface(null, Typeface.BOLD)
        }
        container.addView(titleConn)
        val connectionsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(connectionsList)
        var editingEdge: RelationEdge? = null
        val addTitle = TextView(this).apply {
            text = "+ Add Connection"
            setTextColor(textColor)
            setPadding(0, 32.dpToPx(), 0, 4.dpToPx())
            setTypeface(null, Typeface.BOLD)
        }
        container.addView(addTitle)
        val allOtherOptions = mutableListOf(getString(R.string.select_member_placeholder))
        val otherNodes = relationsData.nodes.filter { it.id != node.id }
        allOtherOptions.addAll(otherNodes.map { "👤 ${it.name}" })
        allOtherOptions.addAll(relationsData.groups.map { "📁 ${it.name}" })
        val toSpinner = Spinner(this).apply {
            adapter = ColorHelper.createThemedAdapter(this@RelationsActivity, allOtherOptions)
        }
        container.addView(toSpinner)
        addTitle.setOnClickListener { toSpinner.performClick() }
        val tagEdit = EditText(this).apply { hint = getString(R.string.relation_tag); setTextColor(textColor) }
        val noteEdit = EditText(this).apply { hint = "Note"; setTextColor(textColor) }
        container.addView(tagEdit)
        container.addView(noteEdit)
        val btnAdd = Button(this).apply {
            text = getString(R.string.action_add)
        }
        container.addView(btnAdd)
        fun refreshConnections() {
            connectionsList.removeAllViews()
            val actualEdges = relationsData.edges.filter { it.getSafeNodeIds().contains(node.id) }
            actualEdges.forEach { edge ->
                val otherNodeIds = edge.getSafeNodeIds().filter { it != node.id }
                val names = otherNodeIds.mapNotNull { id -> relationsData.nodes.find { it.id == id }?.name }.toMutableList()
                edge.groupIds.forEach { gid -> relationsData.groups.find { it.id == gid }?.let { names.add("📁 ${it.name}") } }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 4.dpToPx(), 0, 4.dpToPx())
                }
                val txt = TextView(this).apply {
                    text = names.joinToString(", ") + (if (!edge.tag.isNullOrBlank()) " (${edge.tag})" else "")
                    setTextColor(textColor)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                val btnEdit = ImageButton(this).apply {
                    setImageResource(android.R.drawable.ic_menu_edit)
                    background = null
                    setOnClickListener {
                        editingEdge = edge
                        addTitle.text = "Edit Connection"
                        btnAdd.text = getString(R.string.save)
                        tagEdit.setText(edge.tag)
                        noteEdit.setText(edge.note)
                        val targetNodeId = edge.getSafeNodeIds().find { it != node.id }
                        val targetGroupId = edge.groupIds.firstOrNull()
                        if (targetNodeId != null) {
                            val idx = otherNodes.indexOfFirst { it.id == targetNodeId }
                            if (idx != -1) toSpinner.setSelection(idx + 1)
                        } else if (targetGroupId != null) {
                            val idx = relationsData.groups.indexOfFirst { it.id == targetGroupId }
                            if (idx != -1) toSpinner.setSelection(otherNodes.size + idx + 1)
                        }
                    }
                }
                val btnDel = ImageButton(this).apply {
                    setImageResource(android.R.drawable.ic_menu_delete)
                    background = null
                    setOnClickListener {
                        relationsData.edges.remove(edge)
                        mapView.invalidate()
                        saveData(silent = true)
                        refreshConnections()
                    }
                }
                row.addView(txt)
                row.addView(btnEdit)
                row.addView(btnDel)
                connectionsList.addView(row)
            }
        }
        refreshConnections()
        btnAdd.setOnClickListener {
            val pos = toSpinner.selectedItemPosition
            if (pos > 0) {
                val tag = tagEdit.text.toString()
                val note = noteEdit.text.toString()
                if (editingEdge != null) {
                    val edge = editingEdge!!
                    edge.tag = tag
                    edge.note = note
                    edge.nodeIds.clear()
                    edge.groupIds.clear()
                    edge.nodeIds.add(node.id)
                    if (pos <= otherNodes.size) {
                        edge.nodeIds.add(otherNodes[pos - 1].id)
                    } else {
                        edge.groupIds.add(relationsData.groups[pos - otherNodes.size - 1].id)
                    }
                } else {
                    val edge = if (pos <= otherNodes.size) {
                        RelationEdge(nodeIds = mutableListOf(node.id, otherNodes[pos - 1].id), tag = tag, note = note)
                    } else {
                        RelationEdge(nodeIds = mutableListOf(node.id), groupIds = mutableListOf(relationsData.groups[pos - otherNodes.size - 1].id), tag = tag, note = note)
                    }
                    relationsData.edges.add(edge)
                }
                mapView.invalidate()
                saveData(silent = true)
                // Reset form
                editingEdge = null
                addTitle.text = "+ Add Connection"
                btnAdd.text = getString(R.string.action_add)
                tagEdit.setText("")
                noteEdit.setText("")
                toSpinner.setSelection(0)
                refreshConnections()
            }
        }
        // Section: Delete Node
        var deleteClicks = 0
        val btnDeleteNode = Button(this).apply {
            text = getString(R.string.delete) + " " + node.name + " (8)"
            setTextColor(Color.RED)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = 48.dpToPx()
            }
            setOnClickListener {
                deleteClicks++
                if (deleteClicks >= 8) {
                    relationsData.nodes.remove(node)
                    relationsData.edges.removeAll { it.getSafeNodeIds().contains(node.id) }
                    relationsData.groups.forEach { it.nodeIds.remove(node.id) }
                    mapView.setData(relationsData)
                    saveData(silent = true)
                    activeDialog?.dismiss()
                } else {
                    text = "${getString(R.string.delete)} ${node.name} (${8 - deleteClicks})"
                }
            }
        }
        container.addView(btnDeleteNode)
        val scroll = ScrollView(this).apply { addView(container) }
        activeDialog = AlertDialog.Builder(this)
            .setTitle(node.name)
            .setView(scroll)
            .setPositiveButton(R.string.done, null)
            .show().also { ColorHelper.styleAlertDialog(it, this) }
    }
    private fun showCustomColorPickerDialog(currentColor: Int, onColorSelected: (Int) -> Unit) {
        activeDialog?.dismiss()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = 16.dpToPx()
            setPadding(p, p, p, p)
        }
        val hsv = FloatArray(3)
        Color.colorToHSV(currentColor, hsv)
        val preview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 40.dpToPx()).apply {
                bottomMargin = 8.dpToPx()
            }
            setBackgroundColor(currentColor)
        }
        container.addView(preview)
        val hexInput = EditText(this).apply {
            setText(String.format("#%06X", (0xFFFFFF and currentColor)))
            hint = "#RRGGBB"
            setSingleLine(true)
        }
        container.addView(hexInput)
        val hueSeek = SeekBar(this).apply { max = 360; progress = hsv[0].toInt() }
        val satSeek = SeekBar(this).apply { max = 100; progress = (hsv[1] * 100).toInt() }
        val valSeek = SeekBar(this).apply { max = 100; progress = (hsv[2] * 100).toInt() }
        val textColor = ColorHelper.getTextColor(this)
        container.addView(TextView(this).apply { text = getString(R.string.label_hue); setTextColor(textColor) })
        container.addView(hueSeek)
        container.addView(TextView(this).apply { text = getString(R.string.label_saturation); setTextColor(textColor) })
        container.addView(satSeek)
        container.addView(TextView(this).apply { text = getString(R.string.label_brightness); setTextColor(textColor) })
        container.addView(valSeek)
        var isUpdating = false
        val updatePreview = {
            if (!isUpdating) {
                isUpdating = true
                try {
                    val color = Color.HSVToColor(floatArrayOf(hueSeek.progress.toFloat(), satSeek.progress / 100f, valSeek.progress / 100f))
                    preview.setBackgroundColor(color)
                    hexInput.setText(String.format("#%06X", (0xFFFFFF and color)))
                } catch (_: Exception) {}
                isUpdating = false
            }
        }
        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) { if (p2) updatePreview() }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        }
        hueSeek.setOnSeekBarChangeListener(seekListener)
        satSeek.setOnSeekBarChangeListener(seekListener)
        valSeek.setOnSeekBarChangeListener(seekListener)
        hexInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isUpdating) return
                val str = s.toString()
                if (str.length == 7 && str.startsWith("#")) {
                    try {
                        isUpdating = true
                        val color = Color.parseColor(str)
                        preview.setBackgroundColor(color)
                        Color.colorToHSV(color, hsv)
                        hueSeek.progress = hsv[0].toInt()
                        satSeek.progress = (hsv[1] * 100).toInt()
                        valSeek.progress = (hsv[2] * 100).toInt()
                        isUpdating = false
                    } catch (_: Exception) { isUpdating = false }
                }
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })
        activeDialog = AlertDialog.Builder(this)
            .setTitle(R.string.choose_color_hex)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                try {
                    val color = Color.HSVToColor(floatArrayOf(hueSeek.progress.toFloat(), satSeek.progress / 100f, valSeek.progress / 100f))
                    onColorSelected(color)
                } catch (_: Exception) {}
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .also { ColorHelper.styleAlertDialog(it, this); activeDialog = it }
    }
    private fun exportToPdf() {
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_TITLE, "relations_export.pdf")
        }
        startActivityForResult(intent, 1002)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1002 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                PdfExportHelper.exportRelationsToPdf(this, uri, relationsData, mapView)
            }
        } else if (requestCode == 1003 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                imagePickerCallback?.invoke(uri)
            }
        }
    }
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
