package com.interli.plural

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.graphics.drawable.GradientDrawable

private var activeDialog: AlertDialog? = null
class RelationsActivity : BaseActivity() {

    private lateinit var mapView: RelationsMapView
    private var relationsData = RelationsData()
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

        mapView.onNodeLongClicked = { node -> showEditNodeDialog(node) }
        mapView.onGroupLongClicked = { group -> showEditGroupDialog(group) }
        mapView.onDataChanged = { saveData(silent = true) }
    }

    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = sharedPref.getString("relations_data", "{}") ?: "{}"
        relationsData = try {
            gson.fromJson(json, RelationsData::class.java) ?: RelationsData()
        } catch (_: Exception) {
            RelationsData()
        }
        mapView.setData(relationsData)
    }

    private fun saveData(silent: Boolean = false) {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = gson.toJson(relationsData)
        sharedPref.edit().putString("relations_data", json).apply()
        if (!silent) {
            Toast.makeText(this, getString(R.string.backup_saved), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddNodeDialog() {
        showSelectMemberDialog()
    }

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
            isMultiSelect = false
        ) { selectedIds ->
            if (selectedIds.isNotEmpty()) {
                val person = filteredPeople.find { it.id == selectedIds[0] } ?: return@showMemberSelectionDialog

                val offset = (relationsData.nodes.size * 100f) % 400f
                val startX = 300f + offset
                val startY = 300f + (relationsData.nodes.size / 4 * 100f)

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
                mapView.setData(relationsData)
                saveData(silent = true)
            }
        }
    }

    private fun showAddEdgeDialog() {
        if (relationsData.nodes.size < 2) return

        val nodeNames = relationsData.nodes.map { it.name }.toTypedArray()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = 16.dpToPx()
            setPadding(p, p, p, p)
        }

        val fromSpinner = Spinner(this).apply { adapter = ArrayAdapter(this@RelationsActivity, android.R.layout.simple_spinner_dropdown_item, nodeNames) }
        val toSpinner = Spinner(this).apply { adapter = ArrayAdapter(this@RelationsActivity, android.R.layout.simple_spinner_dropdown_item, nodeNames) }
        val tagInput = EditText(this).apply { hint = getString(R.string.relation_tag) }

        layout.addView(TextView(this).apply { text = getString(R.string.select_from_node) })
        layout.addView(fromSpinner)
        layout.addView(TextView(this).apply { text = getString(R.string.select_to_node) })
        layout.addView(toSpinner)
        layout.addView(tagInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.add_edge)
            .setView(layout)
            .setPositiveButton(R.string.action_add) { _, _ ->
                val fromNode = relationsData.nodes[fromSpinner.selectedItemPosition]
                val toNode = relationsData.nodes[toSpinner.selectedItemPosition]
                if (fromNode.id != toNode.id) {
                    val edge = RelationEdge(fromNodeId = fromNode.id, toNodeId = toNode.id, tag = tagInput.text.toString())
                    relationsData.edges.add(edge)
                    mapView.setData(relationsData)
                    saveData(silent = true)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun showGroupsListDialog() {
        val options = mutableListOf(" + " + getString(R.string.action_new_group))
        options.addAll(relationsData.groups.map { it.name })

        AlertDialog.Builder(this)
            .setTitle(R.string.add_group_bubble)
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) showEditGroupDialog(RelationGroup())
                else showEditGroupDialog(relationsData.groups[which - 1])
            }
            .show()
            .let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun showEditGroupDialog(group: RelationGroup) {
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
            isChecked = true
            setTextColor(textColor)
        }

        layout.addView(TextView(this).apply { text = getString(R.string.label_name_colon); setTextColor(textColor) })
        layout.addView(nameInput)
        layout.addView(TextView(this).apply { text = getString(R.string.label_color_colon); setTextColor(textColor); setPadding(0, 8.dpToPx(), 0, 0) })
        layout.addView(scroll)
        layout.addView(cbSnap)

        val nodeNames = relationsData.nodes.map { it.name }.toTypedArray()
        val checked = BooleanArray(nodeNames.size) { i -> group.nodeIds.contains(relationsData.nodes[i].id) }

        AlertDialog.Builder(this)
            .setTitle(if (isNew) getString(R.string.new_group_bubble) else getString(R.string.edit_group_bubble))
            .setView(layout)
            .setMultiChoiceItems(nodeNames, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.save) { _, _ ->
                group.name = nameInput.text.toString()
                group.color = selectedColor
                group.nodeIds = relationsData.nodes.filterIndexed { i, _ -> checked[i] }.map { it.id }.toMutableList()

                if (isNew) relationsData.groups.add(group)

                if (cbSnap.isChecked && group.nodeIds.isNotEmpty()) {
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
                if (!isNew) relationsData.groups.remove(group)
                mapView.setData(relationsData)
                saveData(silent = true)
            }
            .show()
            .let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun showManageGroupsForNodeDialog(node: RelationNode) {
        val groups = relationsData.groups
        if (groups.isEmpty()) {
            Toast.makeText(this, getString(R.string.create_groups_first), Toast.LENGTH_SHORT).show()
            return
        }

        val names = groups.map { it.name }.toTypedArray()
        val checked = BooleanArray(groups.size) { i -> groups[i].nodeIds.contains(node.id) }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_manage_groups))
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.done) { _, _ ->
                groups.forEachIndexed { index, group ->
                    if (checked[index]) {
                        if (!group.nodeIds.contains(node.id)) group.nodeIds.add(node.id)
                    } else {
                        group.nodeIds.remove(node.id)
                    }
                }
                mapView.setData(relationsData)
                saveData(silent = true)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun showEditNodeDialog(node: RelationNode) {
        val options = arrayOf(
            getString(R.string.action_connections),
            getString(R.string.action_manage_groups),
            getString(R.string.delete)
        )
        AlertDialog.Builder(this)
            .setTitle(node.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showManageConnectionsDialog(node)
                    1 -> showManageGroupsForNodeDialog(node)
                    2 -> {
                        relationsData.nodes.remove(node)
                        relationsData.edges.removeAll { it.fromNodeId == node.id || it.toNodeId == node.id }
                        relationsData.groups.forEach { it.nodeIds.remove(node.id) }
                        mapView.setData(relationsData)
                        saveData(silent = true)
                    }
                }
            }
            .show()
            .let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun showManageConnectionsDialog(node: RelationNode) {
        activeDialog?.dismiss()
        val nodeEdges = relationsData.edges.filter { it.fromNodeId == node.id || it.toNodeId == node.id }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = 16.dpToPx()
            setPadding(p, p, p, p)
        }

        val titleExisting = TextView(this).apply {
            text = getString(R.string.label_linked_members)
            textSize = 18f
            setPadding(0, 0, 0, 8.dpToPx())
            setTextColor(ColorHelper.getTextColor(this@RelationsActivity))
        }
        container.addView(titleExisting)

        if (nodeEdges.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.no_connections)
                setTextColor(ColorHelper.getTextColor(this@RelationsActivity))
            })
        } else {
            nodeEdges.forEach { edge ->
                val otherNodeId = if (edge.fromNodeId == node.id) edge.toNodeId else edge.fromNodeId
                val otherNode = relationsData.nodes.find { it.id == otherNodeId } ?: return@forEach

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 4.dpToPx(), 0, 4.dpToPx())
                }

                val txt = TextView(this).apply {
                    text = "${otherNode.name}${if (edge.tag.isNullOrBlank()) "" else " (${edge.tag})"}"
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setTextColor(ColorHelper.getTextColor(this@RelationsActivity))
                }

                val btnDel = ImageButton(this).apply {
                    setImageResource(android.R.drawable.ic_menu_delete)
                    background = null
                    setOnClickListener {
                        relationsData.edges.remove(edge)
                        mapView.setData(relationsData)
                        Toast.makeText(this@RelationsActivity, getString(R.string.connection_removed), Toast.LENGTH_SHORT).show()
                        showManageConnectionsDialog(node)
                    }
                }

                row.addView(txt)
                row.addView(btnDel)
                container.addView(row)
            }
        }

        val btnAdd = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.add_edge)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 16.dpToPx()
            }
            setOnClickListener {
                showAddEdgeForNodeDialog(node)
            }
        }
        container.addView(btnAdd)

        AlertDialog.Builder(this)
            .setTitle(node.name)
            .setView(container)
            .setPositiveButton(R.string.done, null)
            .show()
            .let {
                ColorHelper.styleAlertDialog(it, this)
                activeDialog = it
            }
    }

    private fun showAddEdgeForNodeDialog(node: RelationNode) {
        val otherNodes = relationsData.nodes.filter { it.id != node.id }
        if (otherNodes.isEmpty()) return

        val nodeNames = mutableListOf(getString(R.string.select_member_placeholder))
        nodeNames.addAll(otherNodes.map { it.name })

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = 16.dpToPx()
            setPadding(p, p, p, p)
        }

        val toSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@RelationsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                nodeNames
            )
        }
        val tagInput = EditText(this).apply { hint = getString(R.string.relation_tag) }

        layout.addView(TextView(this).apply {
            text = getString(R.string.connect_with)
            setTextColor(ColorHelper.getTextColor(this@RelationsActivity))
        })
        layout.addView(toSpinner)
        layout.addView(TextView(this).apply {
            text = getString(R.string.relation_tag)
            setTextColor(ColorHelper.getTextColor(this@RelationsActivity))
            setPadding(0, 8.dpToPx(), 0, 0)
        })
        layout.addView(tagInput)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_edge))
            .setView(layout)
            .setPositiveButton(R.string.action_add) { _, _ ->
                if (toSpinner.selectedItemPosition > 0) {
                    val toNode = otherNodes[toSpinner.selectedItemPosition - 1]
                    val edge = RelationEdge(
                        fromNodeId = node.id,
                        toNodeId = toNode.id,
                        tag = tagInput.text.toString()
                    )
                    relationsData.edges.add(edge)
                    mapView.setData(relationsData)
                    saveData(silent = true)
                    Toast.makeText(this, getString(R.string.connection_added), Toast.LENGTH_SHORT)
                        .show()
                    showManageConnectionsDialog(node)
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.select_member_placeholder),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            .let {
                ColorHelper.styleAlertDialog(it, this)
                activeDialog = it
            }
    }

        private fun showCustomColorPickerDialog(currentColor: Int, onColorSelected: (Int) -> Unit) {
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
            container.addView(TextView(this).apply { text = "Kleurtoon (Hue)"; setTextColor(textColor) })
            container.addView(hueSeek)
            container.addView(TextView(this).apply { text = "Verzadiging (Saturation)"; setTextColor(textColor) })
            container.addView(satSeek)
            container.addView(TextView(this).apply { text = "Helderheid (Brightness)"; setTextColor(textColor) })
            container.addView(valSeek)

            val updatePreview = {
                try {
                    val color = Color.HSVToColor(floatArrayOf(hueSeek.progress.toFloat(), satSeek.progress / 100f, valSeek.progress / 100f))
                    preview.setBackgroundColor(color)
                    hexInput.setText(String.format("#%06X", (0xFFFFFF and color)))
                } catch (_: Exception) {}
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
                    val str = s.toString()
                    if (str.length == 7 && str.startsWith("#")) {
                        try {
                            val color = Color.parseColor(str)
                            preview.setBackgroundColor(color)
                            Color.colorToHSV(color, hsv)
                            hueSeek.progress = hsv[0].toInt()
                            satSeek.progress = (hsv[1] * 100).toInt()
                            valSeek.progress = (hsv[2] * 100).toInt()
                        } catch (_: Exception) {}
                    }
                }
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            })

            AlertDialog.Builder(this)
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
                .let { ColorHelper.styleAlertDialog(it, this) }
        }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
