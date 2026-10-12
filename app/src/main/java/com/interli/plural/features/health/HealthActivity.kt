package com.interli.plural.features.health

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.interli.plural.Person
import com.interli.plural.R
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.features.member.WhoAmIActivity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar
import java.util.Date

class HealthActivity : BaseActivity() {

    private lateinit var container: LinearLayout
    private var systemMembers: List<Person> = emptyList()
    private val selectedMemberIds: MutableList<String> = mutableListOf()
    private val selectedMemberNames: MutableList<String> = mutableListOf()
    private var isMemberSelectionInitialized = false

    private val activeMemberIdString: String?
        get() = if (selectedMemberIds.isNotEmpty()) selectedMemberIds.joinToString(",") else null

    private val activeMemberNameString: String?
        get() = if (selectedMemberNames.isNotEmpty()) selectedMemberNames.joinToString(", ") else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health)
        setupNavigationDrawer()

        container = findViewById(R.id.healthContainer)
        findViewById<MaterialButton>(R.id.btnHealthSettings).let { btn ->
            styleMaterialButton(btn, primary = false)
            btn.setOnClickListener {
                startActivity(Intent(this, HealthSettingsActivity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadActiveMember()
        renderHealthPage()
    }

    private fun loadActiveMember() {
        systemMembers = MemberHelper.loadAllPeople(this).filter { !it.isArchived }

        if (!isMemberSelectionInitialized) {
            isMemberSelectionInitialized = true
            val frontingMembers = systemMembers.filter { it.isFront }
            if (frontingMembers.isNotEmpty()) {
                selectedMemberIds.clear()
                selectedMemberIds.addAll(frontingMembers.map { it.id })
                selectedMemberNames.clear()
                selectedMemberNames.addAll(frontingMembers.map { it.name })
            }
        }
    }

    private fun createCard(): MaterialCardView {
        val density = resources.displayMetrics.density
        return MaterialCardView(this).apply {
            radius = 16f * density
            cardElevation = 3f * density
            useCompatPadding = true
            setCardBackgroundColor(ColorHelper.getBgColor(this@HealthActivity))
            strokeColor = ColorHelper.getBtnColor(this@HealthActivity) and 0x88FFFFFF.toInt()
            strokeWidth = (1.5f * density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, (24 * density).toInt()) }
        }
    }

    private fun styleMaterialButton(btn: MaterialButton, primary: Boolean = true) {
        val btnBg = ColorHelper.getBtnColor(this)
        val btnText = ColorHelper.getBtnTextColor(this)
        val globalText = ColorHelper.getTextColor(this)
        if (primary) {
            btn.backgroundTintList = ColorStateList.valueOf(btnBg)
            btn.setTextColor(btnText)
            btn.strokeWidth = 0
        } else {
            btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            btn.setTextColor(globalText)
            btn.strokeColor = ColorStateList.valueOf(btnBg)
            btn.strokeWidth = (1.5f * resources.displayMetrics.density).toInt()
        }
        btn.cornerRadius = (10 * resources.displayMetrics.density).toInt()
    }

    private fun createStyledButton(textStr: String, primary: Boolean = true): MaterialButton {
        val btn = MaterialButton(this)
        btn.text = textStr
        btn.textSize = 12f
        styleMaterialButton(btn, primary)
        return btn
    }

    private fun getLastLogSubtitle(
        type: String,
        logs: List<HealthLogEntry>,
        extraTypes: List<String> = emptyList()
    ): TextView {
        val allTypes = listOf(type) + extraTypes
        val log = logs.find { allTypes.contains(it.type) }
        val subtitleText = if (log != null) {
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.timestamp))
            val member = log.memberName ?: getString(R.string.unnamed_field)
            getString(R.string.label_last_logged, timeStr, member)
        } else {
            getString(R.string.label_never_logged)
        }
        return TextView(this).apply {
            text = subtitleText
            textSize = 12f
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            alpha = 0.7f
            setPadding(0, 2, 0, 12)
        }
    }

    private fun renderCardLogs(
        cardLayout: LinearLayout,
        todayLogs: List<HealthLogEntry>,
        predicate: (HealthLogEntry) -> Boolean
    ) {
        val filteredLogs = todayLogs.filter(predicate)
        if (filteredLogs.isEmpty()) return

        val density = resources.displayMetrics.density

        val divider = View(this).apply {
            setBackgroundColor(ColorHelper.getBtnColor(this@HealthActivity) and 0x22FFFFFF)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (1 * density).toInt()
            ).apply { setMargins(0, 16, 0, 4) }
        }
        cardLayout.addView(divider)

        val logsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 4, 0, 0)
        }

        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
            isClickable = true
            isFocusable = true
        }

        val tvHeaderTitle = TextView(this).apply {
            text = "${getString(R.string.health_recent_logs)} (${filteredLogs.size})"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            alpha = 0.8f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvArrow = TextView(this).apply {
            text = "▼"
            textSize = 12f
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            alpha = 0.7f
        }

        var isExpanded = false
        headerLayout.setOnClickListener {
            isExpanded = !isExpanded
            logsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
            tvArrow.text = if (isExpanded) "▲" else "▼"
        }

        headerLayout.addView(tvHeaderTitle)
        headerLayout.addView(tvArrow)
        cardLayout.addView(headerLayout)

        filteredLogs.forEachIndexed { index, log ->
            if (index > 0) {
                val rowDivider = View(this).apply {
                    setBackgroundColor(ColorHelper.getBtnColor(this@HealthActivity) and 0x11FFFFFF)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * density).toInt()
                    ).apply { setMargins(0, 2, 0, 2) }
                }
                logsContainer.addView(rowDivider)
            }

            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 6, 0, 6)
                setOnClickListener {
                    showEditLogEntryDialog(log)
                }
            }

            val memberNameStr = if (!log.memberName.isNullOrEmpty()) log.memberName else getString(R.string.group_general)

            val tvMember = TextView(this).apply {
                text = memberNameStr
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                alpha = 0.85f
                layoutParams = LinearLayout.LayoutParams((110 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val logVal = log.value ?: ""
            val logTypeName = log.type ?: ""
            val valStr = if (logVal.isNotEmpty()) "$logTypeName ($logVal)" else logTypeName

            val tvValue = TextView(this).apply {
                text = valStr
                textSize = 11f
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                alpha = 0.9f
                setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.timestamp))

            val tvTime = TextView(this).apply {
                text = timeStr
                textSize = 11f
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                alpha = 0.7f
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams((48 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            rowLayout.addView(tvMember)
            rowLayout.addView(tvValue)
            rowLayout.addView(tvTime)

            logsContainer.addView(rowLayout)
        }

        cardLayout.addView(logsContainer)
    }

    private fun renderHealthPage() {
        val settings = HealthHelper.loadSettings(this)
        val allLogs = HealthHelper.loadLogs(this)
        val todayMillis = System.currentTimeMillis()
        val tz = java.util.TimeZone.getDefault()
        val todayLogs = allLogs.filter { HealthHelper.isSameDay(it.timestamp, todayMillis, tz) }

        container.removeAllViews()

        renderActiveMemberHeader()

        if (settings.showHydration) renderHydrationCard(todayLogs)
        if (settings.showEatenCheck) renderEatenCard(todayLogs)
        if (settings.showNutritionSchedule) renderNutritionCard(todayLogs)
        if (settings.showEnergySlider) renderEnergyCard(todayLogs)
        if (settings.showRest) renderRestCard(todayLogs)
        if (settings.showWeight) renderWeightCard(todayLogs)
        if (settings.showMedication) renderMedicationCard(todayLogs)
        if (settings.showSensations) renderSensationsCard(todayLogs)
        if (settings.showCustomCounters) renderCustomCountersCard(todayLogs)
        if (settings.showCustomSliders) renderCustomSlidersCard(todayLogs)
    }


    private fun renderActiveMemberHeader() {
        val density = resources.displayMetrics.density

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (24 * density).toInt())
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val displayNames = if (selectedMemberNames.isNotEmpty()) {
            selectedMemberNames.joinToString(", ")
        } else {
            getString(R.string.group_general)
        }

        val labelStr = getString(R.string.logged_in_as, "").split(":")[0] + ":"

        val tvLabel = TextView(this).apply {
            text = labelStr
            textSize = 12f
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            alpha = 0.7f
        }

        val tvActiveNames = TextView(this).apply {
            text = displayNames
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            setPadding(0, 2, 0, 0)
        }

        textContainer.addView(tvLabel)
        textContainer.addView(tvActiveNames)

        val btnSwitch = createStyledButton(getString(R.string.select_member), primary = false).apply {
            setOnClickListener { showMemberSelectDialog() }
        }

        layout.addView(textContainer)
        layout.addView(btnSwitch)
        container.addView(layout)
    }

    private fun showMemberSelectDialog() {
        val optionsList = mutableListOf(getString(R.string.no_member_general))
        optionsList.addAll(systemMembers.map { it.name })

        val checkedArray = BooleanArray(optionsList.size)
        val tempSelectedIds = mutableListOf<String>()
        val tempSelectedNames = mutableListOf<String>()

        if (selectedMemberIds.isEmpty()) {
            checkedArray[0] = true
        } else {
            systemMembers.forEachIndexed { index, person ->
                if (selectedMemberIds.contains(person.id)) {
                    checkedArray[index + 1] = true
                    tempSelectedIds.add(person.id)
                    tempSelectedNames.add(person.name)
                }
            }
        }

        var alertDialog: AlertDialog? = null
        var isUpdatingListView = false

        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_member))
            .setMultiChoiceItems(optionsList.toTypedArray(), checkedArray) { _, which, isChecked ->
                if (isUpdatingListView) return@setMultiChoiceItems
                isUpdatingListView = true
                try {
                    val listView = alertDialog?.listView
                    if (which == 0) {
                        if (isChecked) {
                            tempSelectedIds.clear()
                            tempSelectedNames.clear()
                            for (i in 1 until checkedArray.size) {
                                checkedArray[i] = false
                                listView?.setItemChecked(i, false)
                            }
                        } else {
                            if (tempSelectedIds.isEmpty()) {
                                checkedArray[0] = true
                                listView?.setItemChecked(0, true)
                            }
                        }
                    } else {
                        val person = systemMembers[which - 1]
                        if (isChecked) {
                            checkedArray[0] = false
                            listView?.setItemChecked(0, false)
                            if (!tempSelectedIds.contains(person.id)) {
                                tempSelectedIds.add(person.id)
                                tempSelectedNames.add(person.name)
                            }
                        } else {
                            tempSelectedIds.remove(person.id)
                            tempSelectedNames.remove(person.name)
                            if (tempSelectedIds.isEmpty()) {
                                checkedArray[0] = true
                                listView?.setItemChecked(0, true)
                            }
                        }
                    }
                } finally {
                    isUpdatingListView = false
                }
            }
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                selectedMemberIds.clear()
                selectedMemberIds.addAll(tempSelectedIds)
                selectedMemberNames.clear()
                selectedMemberNames.addAll(tempSelectedNames)
                renderHealthPage()
            }
            .setNegativeButton(getString(R.string.cancel), null)

        alertDialog = builder.create()
        alertDialog.show()
        ColorHelper.styleAlertDialog(alertDialog, this)
    }

    private fun renderHydrationCard(logs: List<HealthLogEntry>) {
        val card = createCard()
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        val titleKey = getString(R.string.hydration_counter_title)
        var count = HealthHelper.getHydrationCount(this)
        val target = HealthHelper.getHydrationTarget(this)

        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = titleKey
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnEditTarget = createStyledButton(getString(R.string.edit), primary = false).apply {
            isAllCaps = false
            textSize = 11f
            setOnClickListener {
                showEditHydrationTargetDialog(target)
            }
        }
        titleLayout.addView(title)
        titleLayout.addView(btnEditTarget)

        val density = resources.displayMetrics.density

        val counterRowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }

        val tvStatus = TextView(this)

        val btnMinus = createStyledButton("-", primary = false).apply {
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(
                (48 * density).toInt(),
                (42 * density).toInt()
            )
            setOnClickListener {
                if (count > 0) {
                    count--
                    HealthHelper.setHydrationCount(this@HealthActivity, count)
                    tvStatus.text = "$count / $target"
                }
            }
        }

        tvStatus.apply {
            text = "$count / $target"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding((20 * density).toInt(), 0, (20 * density).toInt(), 0)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            setOnClickListener {
                showEditHydrationTargetDialog(target)
            }
        }

        val btnPlus = createStyledButton("+", primary = false).apply {
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(
                (48 * density).toInt(),
                (42 * density).toInt()
            )
            setOnClickListener {
                count++
                HealthHelper.setHydrationCount(this@HealthActivity, count)
                tvStatus.text = "$count / $target"
                HealthHelper.addLogEntry(this@HealthActivity, HealthLogEntry(
                    memberId = activeMemberIdString,
                    memberName = activeMemberNameString,
                    type = titleKey,
                    value = "$count / $target"
                ))
                renderHealthPage()
            }
        }

        counterRowLayout.addView(btnMinus)
        counterRowLayout.addView(tvStatus)
        counterRowLayout.addView(btnPlus)

        cardLayout.addView(titleLayout)
        cardLayout.addView(counterRowLayout)

        renderCardLogs(cardLayout, logs) { it.type == titleKey }

        card.addView(cardLayout)
        container.addView(card)
    }

    private fun showEditHydrationTargetDialog(currentTarget: Int) {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentTarget.toString())
            setSelection(text.length)
        }
        val dialogContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            addView(et)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.hint_counter_target))
            .setView(dialogContainer)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newTarget = et.text.toString().toIntOrNull()
                if (newTarget != null && newTarget > 0) {
                    HealthHelper.setHydrationTarget(this, newTarget)
                    renderHealthPage()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show().let { ColorHelper.styleAlertDialog(it, this) }
    }
    private fun renderEatenCard(logs: List<HealthLogEntry>) {
        val card = createCard()
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        val titleKey = getString(R.string.eaten_check_title)
        val title = TextView(this).apply {
            text = titleKey
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            setPadding(0, 0, 0, 12)
        }
        cardLayout.addView(title)

        val meals = listOf(
            getString(R.string.meal_breakfast),
            getString(R.string.meal_lunch),
            getString(R.string.meal_dinner)
        )

        val todayMillis = System.currentTimeMillis()
        val allEatenRecords = HealthHelper.loadEatenRecords(this)
        val density = resources.displayMetrics.density

        meals.forEach { meal ->
            val record = HealthHelper.getEatenRecordFromList(allEatenRecords, todayMillis, meal)
            val isChecked = record != null && record.isChecked

            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(1, ColorHelper.getBtnColor(this@HealthActivity) and 0x22FFFFFF)
                    cornerRadius = 8f * density
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, (4 * density).toInt(), 0, (4 * density).toInt()) }
            }

            val checkBox = androidx.appcompat.widget.AppCompatCheckBox(this).apply {
                this.isChecked = isChecked
                buttonTintList = ColorStateList.valueOf(ColorHelper.getBtnColor(this@HealthActivity))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, (2 * density).toInt(), 0) }
                setOnClickListener {
                    val newChecked = this.isChecked
                    if (newChecked) {
                        val newRecord = record ?: EatenMealRecord(
                            mealType = meal,
                            timestamp = System.currentTimeMillis(),
                            memberId = activeMemberIdString,
                            memberName = activeMemberNameString,
                            isChecked = true
                        )
                        newRecord.isChecked = true
                        newRecord.timestamp = System.currentTimeMillis()
                        newRecord.memberId = activeMemberIdString
                        newRecord.memberName = activeMemberNameString
                        HealthHelper.saveOrUpdateEatenRecord(this@HealthActivity, newRecord)
                        HealthHelper.setLastEatenTime(this@HealthActivity, newRecord.timestamp, meal)
                        HealthHelper.addLogEntry(this@HealthActivity, HealthLogEntry(
                            memberId = activeMemberIdString,
                            memberName = activeMemberNameString,
                            type = titleKey,
                            value = meal
                        ))
                    } else {
                        record?.let {
                            it.isChecked = false
                            HealthHelper.saveOrUpdateEatenRecord(this@HealthActivity, it)
                        }
                    }
                    renderHealthPage()
                }
            }

            val tvMealName = TextView(this).apply {
                text = meal
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                layoutParams = LinearLayout.LayoutParams(
                    (75 * density).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, (4 * density).toInt(), 0) }
            }

            val foodText = if (record != null && record.eatenItems.isNotEmpty()) {
                record.eatenItems.joinToString(", ")
            } else {
                "+"
            }

            val btnFood = createStyledButton(foodText, primary = false).apply {
                textSize = 11f
                isAllCaps = false
                setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
                alpha = if (isChecked) 1.0f else 0.6f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0) }
                setOnClickListener {
                    showFoodItemsDialog(meal, record, todayMillis) {
                        renderHealthPage()
                    }
                }
            }

            val timeStr = if (isChecked && record != null) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.timestamp))
            } else {
                "-"
            }

            val tvTime = TextView(this).apply {
                text = timeStr
                textSize = 11f
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                alpha = if (isChecked) 1.0f else 0.5f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    (55 * density).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val mName = if (isChecked && record != null) {
                record.memberName ?: activeMemberNameString ?: "-"
            } else {
                "-"
            }

            val tvMember = TextView(this).apply {
                text = mName
                textSize = 11f
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                alpha = if (isChecked) 1.0f else 0.5f
                gravity = Gravity.END
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(
                    (50 * density).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val longClickListener = View.OnLongClickListener {
                showEditEatenMealDialog(meal, record, todayMillis) {
                    renderHealthPage()
                }
                true
            }

            tvTime.setOnLongClickListener(longClickListener)
            tvMember.setOnLongClickListener(longClickListener)
            rowLayout.setOnLongClickListener(longClickListener)

            rowLayout.addView(checkBox)
            rowLayout.addView(tvMealName)
            rowLayout.addView(btnFood)
            rowLayout.addView(tvTime)
            rowLayout.addView(tvMember)

            cardLayout.addView(rowLayout)
        }

        val btnTimeline = createStyledButton(getString(R.string.btn_eaten_timeline), primary = false).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, (16 * density).toInt(), 0, 0) }
            setOnClickListener {
                startActivity(Intent(this@HealthActivity, EatenTimelineActivity::class.java))
            }
        }

        cardLayout.addView(btnTimeline)

        renderCardLogs(cardLayout, logs) { it.type == titleKey }

        card.addView(cardLayout)
        container.addView(card)
    }

    private fun showEditEatenMealDialog(
        mealName: String,
        record: EatenMealRecord?,
        defaultDateMillis: Long,
        onSaved: () -> Unit
    ) {
        val existingRecord = record ?: EatenMealRecord(
            mealType = mealName,
            timestamp = defaultDateMillis,
            memberId = activeMemberIdString,
            memberName = activeMemberNameString,
            isChecked = true
        )

        val selectedMealMemberIds = mutableListOf<String>()
        val selectedMealMemberNames = mutableListOf<String>()

        if (!existingRecord.memberId.isNullOrEmpty()) {
            selectedMealMemberIds.addAll(existingRecord.memberId!!.split(","))
        }
        if (!existingRecord.memberName.isNullOrEmpty()) {
            selectedMealMemberNames.addAll(existingRecord.memberName!!.split(", "))
        }

        var selectedTimestamp = existingRecord.timestamp

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        val tvMember = TextView(this).apply {
            val displayName = if (selectedMealMemberNames.isNotEmpty()) selectedMealMemberNames.joinToString(", ") else getString(R.string.group_general)
            text = getString(R.string.logged_in_as, displayName)
            textSize = 14f
            setPadding(0, 8, 0, 12)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
        }

        val btnSelectMember = createStyledButton(getString(R.string.select_member), primary = false).apply {
            setOnClickListener {
                val optionsList = mutableListOf(getString(R.string.no_member_general))
                optionsList.addAll(systemMembers.map { it.name })

                val checkedArray = BooleanArray(optionsList.size)
                if (selectedMealMemberIds.isEmpty()) {
                    checkedArray[0] = true
                } else {
                    systemMembers.forEachIndexed { index, person ->
                        if (selectedMealMemberIds.contains(person.id)) {
                            checkedArray[index + 1] = true
                        }
                    }
                }

                val tempMealIds = mutableListOf<String>().apply { addAll(selectedMealMemberIds) }
                val tempMealNames = mutableListOf<String>().apply { addAll(selectedMealMemberNames) }

                var alertDialog: AlertDialog? = null
                var isUpdatingListView = false

                val builder = AlertDialog.Builder(this@HealthActivity)
                    .setTitle(getString(R.string.select_member))
                    .setMultiChoiceItems(optionsList.toTypedArray(), checkedArray) { _, which, isChecked ->
                        if (isUpdatingListView) return@setMultiChoiceItems
                        isUpdatingListView = true
                        try {
                            val listView = alertDialog?.listView
                            if (which == 0) {
                                if (isChecked) {
                                    tempMealIds.clear()
                                    tempMealNames.clear()
                                    for (i in 1 until checkedArray.size) {
                                        checkedArray[i] = false
                                        listView?.setItemChecked(i, false)
                                    }
                                } else {
                                    if (tempMealIds.isEmpty()) {
                                        checkedArray[0] = true
                                        listView?.setItemChecked(0, true)
                                    }
                                }
                            } else {
                                val person = systemMembers[which - 1]
                                if (isChecked) {
                                    checkedArray[0] = false
                                    listView?.setItemChecked(0, false)
                                    if (!tempMealIds.contains(person.id)) {
                                        tempMealIds.add(person.id)
                                        tempMealNames.add(person.name)
                                    }
                                } else {
                                    tempMealIds.remove(person.id)
                                    tempMealNames.remove(person.name)
                                    if (tempMealIds.isEmpty()) {
                                        checkedArray[0] = true
                                        listView?.setItemChecked(0, true)
                                    }
                                }
                            }
                        } finally {
                            isUpdatingListView = false
                        }
                    }
                    .setPositiveButton(getString(R.string.save)) { _, _ ->
                        selectedMealMemberIds.clear()
                        selectedMealMemberIds.addAll(tempMealIds)
                        selectedMealMemberNames.clear()
                        selectedMealMemberNames.addAll(tempMealNames)

                        val displayName = if (selectedMealMemberNames.isNotEmpty()) selectedMealMemberNames.joinToString(", ") else getString(R.string.group_general)
                        tvMember.text = getString(R.string.logged_in_as, displayName)
                    }
                    .setNegativeButton(getString(R.string.cancel), null)

                alertDialog = builder.create()
                alertDialog.show()
                ColorHelper.styleAlertDialog(alertDialog, this@HealthActivity)
            }
        }

        val dateFormat = DateFormat.getDateFormat(this)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val tvDateTime = TextView(this).apply {
            text = "${dateFormat.format(Date(selectedTimestamp))} ${timeFormat.format(Date(selectedTimestamp))}"
            textSize = 14f
            setPadding(0, 16, 0, 12)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
        }

        val btnSelectDateTime = createStyledButton(getString(R.string.label_select_date_time), primary = false).apply {
            setOnClickListener {
                val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                android.app.DatePickerDialog(
                    this@HealthActivity,
                    { _, year, month, dayOfMonth ->
                        cal.set(Calendar.YEAR, year)
                        cal.set(Calendar.MONTH, month)
                        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        android.app.TimePickerDialog(
                            this@HealthActivity,
                            { _, hourOfDay, minute ->
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                cal.set(Calendar.MINUTE, minute)
                                selectedTimestamp = cal.timeInMillis
                                tvDateTime.text = "${dateFormat.format(Date(selectedTimestamp))} ${timeFormat.format(Date(selectedTimestamp))}"
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true
                        ).show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }

        layout.addView(tvMember)
        layout.addView(btnSelectMember)
        layout.addView(tvDateTime)
        layout.addView(btnSelectDateTime)

        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_edit_eaten_meal, mealName))
            .setView(layout)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                existingRecord.memberId = if (selectedMealMemberIds.isNotEmpty()) selectedMealMemberIds.joinToString(",") else null
                existingRecord.memberName = if (selectedMealMemberNames.isNotEmpty()) selectedMealMemberNames.joinToString(", ") else null
                existingRecord.timestamp = selectedTimestamp
                existingRecord.isChecked = true
                HealthHelper.saveOrUpdateEatenRecord(this, existingRecord)
                HealthHelper.setLastEatenTime(this, selectedTimestamp, mealName)
                onSaved()
            }
            .setNegativeButton(getString(R.string.cancel), null)

        if (record != null) {
            builder.setNeutralButton(getString(R.string.delete)) { _, _ ->
                HealthHelper.deleteEatenRecord(this, record.id)
                onSaved()
            }
        }

        builder.show().let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun renderNutritionCard(logs: List<HealthLogEntry>) {
        val card = createCard()
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        val titleKey = getString(R.string.nutrition_schedule_title)
        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = titleKey
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnAdd = createStyledButton(getString(R.string.btn_add), primary = false).apply {
            setOnClickListener { showAddNutritionDialog() }
        }
        titleLayout.addView(title)
        titleLayout.addView(btnAdd)
        cardLayout.addView(titleLayout)
        cardLayout.addView(getLastLogSubtitle(titleKey, logs))

        val entries = HealthHelper.loadNutritionEntries(this)
        val density = resources.displayMetrics.density

        entries.forEach { entry ->
            val entryLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            }

            val tvDay = TextView(this).apply {
                text = entry.dayOfWeek.ifEmpty { "-" }
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                layoutParams = LinearLayout.LayoutParams((85 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val tvMealType = TextView(this).apply {
                text = entry.mealType.ifEmpty { "-" }
                textSize = 12f
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                alpha = 0.85f
                layoutParams = LinearLayout.LayoutParams((80 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val tvDesc = TextView(this).apply {
                text = entry.description.ifEmpty { "-" }
                textSize = 12f
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                setPadding((4 * density).toInt(), 0, (8 * density).toInt(), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val longClickListener = View.OnLongClickListener {
                showEditNutritionDialog(entry)
                true
            }
            entryLayout.setOnLongClickListener(longClickListener)
            tvDay.setOnLongClickListener(longClickListener)
            tvMealType.setOnLongClickListener(longClickListener)
            tvDesc.setOnLongClickListener(longClickListener)

            var deleteCountdown = 8
            val btnDel = createStyledButton("✕", primary = false).apply {
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                setPadding(0, 0, 0, 0)
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    (36 * density).toInt(),
                    (36 * density).toInt()
                )
                setOnClickListener {
                    deleteCountdown--
                    if (deleteCountdown <= 0) {
                        entries.remove(entry)
                        HealthHelper.saveNutritionEntries(this@HealthActivity, entries)
                        renderHealthPage()
                    } else {
                        text = "$deleteCountdown"
                    }
                }
            }

            entryLayout.addView(tvDay)
            entryLayout.addView(tvMealType)
            entryLayout.addView(tvDesc)
            entryLayout.addView(btnDel)
            cardLayout.addView(entryLayout)
        }

        renderCardLogs(cardLayout, logs) { it.type == titleKey }

        card.addView(cardLayout)
        container.addView(card)
    }

    private fun showEditNutritionDialog(entryToEdit: NutritionScheduleEntry) {
        showNutritionDialog(entryToEdit)
    }

    private fun showNutritionDialog(existingEntry: NutritionScheduleEntry? = null) {
        val textColor = ColorHelper.getTextColor(this)
        val btnColor = ColorHelper.getBtnColor(this)
        val density = resources.displayMetrics.density

        var alertDialog: AlertDialog? = null

        val scrollView = ScrollView(this)
        val containerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        scrollView.addView(containerLayout)

        if (existingEntry != null) {
            val entries = HealthHelper.loadNutritionEntries(this)
            val index = entries.indexOfFirst { it.id == existingEntry.id }
            if (index != -1) {
                val moveRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, (12 * density).toInt())
                }

                val btnMoveUp = createStyledButton("⬆ " + getString(R.string.action_move_up), primary = false).apply {
                    isEnabled = index > 0
                    alpha = if (isEnabled) 1.0f else 0.4f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(0, 0, (4 * density).toInt(), 0)
                    }
                    setOnClickListener {
                        if (index > 0) {
                            val temp = entries[index]
                            entries[index] = entries[index - 1]
                            entries[index - 1] = temp
                            HealthHelper.saveNutritionEntries(this@HealthActivity, entries)
                            alertDialog?.dismiss()
                            renderHealthPage()
                        }
                    }
                }

                val btnMoveDown = createStyledButton("⬇ " + getString(R.string.action_move_down), primary = false).apply {
                    isEnabled = index < entries.size - 1
                    alpha = if (isEnabled) 1.0f else 0.4f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins((4 * density).toInt(), 0, 0, 0)
                    }
                    setOnClickListener {
                        if (index < entries.size - 1) {
                            val temp = entries[index]
                            entries[index] = entries[index + 1]
                            entries[index + 1] = temp
                            HealthHelper.saveNutritionEntries(this@HealthActivity, entries)
                            alertDialog?.dismiss()
                            renderHealthPage()
                        }
                    }
                }

                moveRow.addView(btnMoveUp)
                moveRow.addView(btnMoveDown)
                containerLayout.addView(moveRow)

                containerLayout.addView(View(this).apply {
                    setBackgroundColor(btnColor and 0x33FFFFFF)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { setMargins(0, 0, 0, 16) }
                })
            }
        }

        val tvDayHeader = TextView(this).apply {
            text = getString(R.string.hint_nutrition_day)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        containerLayout.addView(tvDayHeader)

        val daysOfWeek = listOf(
            getString(R.string.monday),
            getString(R.string.tuesday),
            getString(R.string.wednesday),
            getString(R.string.thursday),
            getString(R.string.friday),
            getString(R.string.saturday),
            getString(R.string.sunday)
        )
        val selectedDays = mutableSetOf<String>()
        var specificDateString: String? = null

        val existingDayParts = existingEntry?.dayOfWeek?.split(",")?.map { it.trim() } ?: emptyList()
        if (existingEntry != null && existingEntry.dayOfWeek.contains("/")) {
            specificDateString = existingEntry.dayOfWeek
        } else {
            selectedDays.addAll(existingDayParts.filter { daysOfWeek.contains(it) })
        }

        val daysLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (8 * density).toInt())
        }

        val daysRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val daysRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val btnDatePicker = createStyledButton(
            if (!specificDateString.isNullOrEmpty()) "📅 $specificDateString" else "📅 ${getString(R.string.select_date)}",
            primary = false
        )

        daysOfWeek.forEachIndexed { index, dayName ->
            val cb = CheckBox(this).apply {
                text = dayName.take(3)
                setTextColor(textColor)
                isChecked = selectedDays.contains(dayName)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedDays.add(dayName)
                        specificDateString = null
                        btnDatePicker.text = "📅 ${getString(R.string.select_date)}"
                    } else {
                        selectedDays.remove(dayName)
                    }
                }
            }
            if (index < 4) daysRow1.addView(cb) else daysRow2.addView(cb)
        }

        btnDatePicker.setOnClickListener {
            val cal = Calendar.getInstance()
            val dpd = android.app.DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                specificDateString = sdf.format(selectedCal.time)
                btnDatePicker.text = "📅 $specificDateString"
                selectedDays.clear()
                for (row in listOf(daysRow1, daysRow2)) {
                    for (i in 0 until row.childCount) {
                        (row.getChildAt(i) as? CheckBox)?.isChecked = false
                    }
                }
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            dpd.show()
            ColorHelper.styleAlertDialog(dpd, this)
        }

        daysLayout.addView(daysRow1)
        daysLayout.addView(daysRow2)
        daysLayout.addView(btnDatePicker)
        containerLayout.addView(daysLayout)

        containerLayout.addView(View(this).apply {
            setBackgroundColor(btnColor and 0x33FFFFFF)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { setMargins(0, 8, 0, 16) }
        })

        val tvTypeHeader = TextView(this).apply {
            text = getString(R.string.hint_nutrition_type)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        containerLayout.addView(tvTypeHeader)

        val mealTypes = listOf(
            getString(R.string.meal_breakfast),
            getString(R.string.meal_lunch),
            getString(R.string.meal_dinner),
            getString(R.string.meal_snack)
        )
        val selectedTypes = mutableSetOf<String>()
        val existingTypeParts = existingEntry?.mealType?.split(",")?.map { it.trim() } ?: emptyList()
        selectedTypes.addAll(existingTypeParts.filter { mealTypes.contains(it) })

        val typeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (8 * density).toInt())
        }

        mealTypes.forEach { typeName ->
            val cb = CheckBox(this).apply {
                text = typeName
                setTextColor(textColor)
                isChecked = selectedTypes.contains(typeName)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedTypes.add(typeName) else selectedTypes.remove(typeName)
                }
            }
            typeRow.addView(cb)
        }
        val typeScrollView = HorizontalScrollView(this).apply {
            addView(typeRow)
            isFillViewport = true
        }
        containerLayout.addView(typeScrollView)

        containerLayout.addView(View(this).apply {
            setBackgroundColor(btnColor and 0x33FFFFFF)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { setMargins(0, 8, 0, 16) }
        })

        val tvDescHeader = TextView(this).apply {
            text = getString(R.string.hint_nutrition_description)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        containerLayout.addView(tvDescHeader)

        val selectedFoodItems = mutableSetOf<String>()
        val foodItemsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun populateFoodItems() {
            foodItemsContainer.removeAllViews()
            val list = HealthHelper.loadSavedFoodItems(this)
            list.forEach { food ->
                val isChecked = existingEntry?.description?.contains(food, ignoreCase = true) == true
                if (isChecked) selectedFoodItems.add(food)
                val cb = CheckBox(this).apply {
                    text = food
                    setTextColor(textColor)
                    this.isChecked = selectedFoodItems.contains(food)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedFoodItems.add(food) else selectedFoodItems.remove(food)
                    }
                }
                foodItemsContainer.addView(cb)
            }
        }
        populateFoodItems()

        val btnAddFood = createStyledButton(getString(R.string.btn_add_food_item), primary = false).apply {
            setOnClickListener {
                showAddFoodItemDialog {
                    populateFoodItems()
                }
            }
        }

        val initialCustomDesc = existingEntry?.description?.let { desc ->
            var cleaned = desc
            selectedFoodItems.forEach { food ->
                cleaned = cleaned.replace(food, "", ignoreCase = true)
            }
            cleaned.replace("(", "").replace(")", "").trim().trim(',', ' ')
        } ?: ""

        val etCustomDesc = EditText(this).apply {
            hint = getString(R.string.hint_nutrition_description)
            setText(if (existingEntry != null && selectedFoodItems.isEmpty()) existingEntry.description else initialCustomDesc)
            setTextColor(textColor)
            setHintTextColor(textColor and 0x88FFFFFF.toInt())
        }

        containerLayout.addView(foodItemsContainer)
        containerLayout.addView(btnAddFood)
        containerLayout.addView(etCustomDesc)

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existingEntry == null) getString(R.string.dialog_add_nutrition) else getString(R.string.dialog_add_nutrition))
            .setView(scrollView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val finalDay = if (!specificDateString.isNullOrEmpty()) {
                    specificDateString!!
                } else {
                    selectedDays.joinToString(", ")
                }
                val finalType = selectedTypes.joinToString(", ")

                val foodListStr = selectedFoodItems.joinToString(", ")
                val customNoteStr = etCustomDesc.text.toString().trim()

                val finalDesc = buildString {
                    if (foodListStr.isNotEmpty()) append(foodListStr)
                    if (customNoteStr.isNotEmpty()) {
                        if (isNotEmpty()) append(" (").append(customNoteStr).append(")")
                        else append(customNoteStr)
                    }
                }

                val entries = HealthHelper.loadNutritionEntries(this)
                if (existingEntry != null) {
                    val idx = entries.indexOfFirst { it.id == existingEntry.id }
                    if (idx != -1) {
                        entries[idx].dayOfWeek = finalDay
                        entries[idx].mealType = finalType
                        entries[idx].description = finalDesc
                    } else {
                        entries.add(NutritionScheduleEntry(
                            id = existingEntry.id,
                            dayOfWeek = finalDay,
                            mealType = finalType,
                            description = finalDesc
                        ))
                    }
                } else {
                    if (finalDay.isNotEmpty() || finalType.isNotEmpty() || finalDesc.isNotEmpty()) {
                        entries.add(NutritionScheduleEntry(
                            dayOfWeek = finalDay,
                            mealType = finalType,
                            description = finalDesc
                        ))
                    }
                }

                HealthHelper.saveNutritionEntries(this, entries)
                HealthHelper.addLogEntry(this, HealthLogEntry(
                    memberId = activeMemberIdString,
                    memberName = activeMemberNameString,
                    type = getString(R.string.nutrition_schedule_title),
                    value = "$finalDay - $finalType: $finalDesc"
                ))
                renderHealthPage()
            }
            .setNegativeButton(getString(R.string.cancel), null)

        if (existingEntry != null) {
            builder.setNeutralButton(getString(R.string.delete)) { _, _ ->
                val entries = HealthHelper.loadNutritionEntries(this)
                entries.removeAll { it.id == existingEntry.id }
                HealthHelper.saveNutritionEntries(this, entries)
                renderHealthPage()
            }
        }

        alertDialog = builder.create()
        alertDialog.show()
        ColorHelper.styleAlertDialog(alertDialog, this)
    }

    private fun showAddNutritionDialog() {
        val textColor = ColorHelper.getTextColor(this)
        val btnColor = ColorHelper.getBtnColor(this)
        val density = resources.displayMetrics.density

        val scrollView = ScrollView(this)
        val containerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        scrollView.addView(containerLayout)
        
        val tvDayHeader = TextView(this).apply {
            text = getString(R.string.hint_nutrition_day)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        containerLayout.addView(tvDayHeader)

        val daysOfWeek = listOf(
            getString(R.string.monday),
            getString(R.string.tuesday),
            getString(R.string.wednesday),
            getString(R.string.thursday),
            getString(R.string.friday),
            getString(R.string.saturday),
            getString(R.string.sunday)
        )
        val selectedDays = mutableSetOf<String>()
        var specificDateString: String? = null

        val daysLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (8 * density).toInt())
        }

        val daysRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val daysRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val btnDatePicker = createStyledButton("📅 ${getString(R.string.select_date)}", primary = false)

        daysOfWeek.forEachIndexed { index, dayName ->
            val cb = CheckBox(this).apply {
                text = dayName.take(3)
                setTextColor(textColor)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedDays.add(dayName)
                        specificDateString = null
                        btnDatePicker.text = "📅 ${getString(R.string.select_date)}"
                    } else {
                        selectedDays.remove(dayName)
                    }
                }
            }
            if (index < 4) daysRow1.addView(cb) else daysRow2.addView(cb)
        }

        btnDatePicker.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            val dpd = android.app.DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val selectedCal = java.util.Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                specificDateString = sdf.format(selectedCal.time)
                btnDatePicker.text = "📅 $specificDateString"
                selectedDays.clear()
                for (row in listOf(daysRow1, daysRow2)) {
                    for (i in 0 until row.childCount) {
                        (row.getChildAt(i) as? CheckBox)?.isChecked = false
                    }
                }
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH))
            dpd.show()
            ColorHelper.styleAlertDialog(dpd, this)
        }

        daysLayout.addView(daysRow1)
        daysLayout.addView(daysRow2)
        daysLayout.addView(btnDatePicker)
        containerLayout.addView(daysLayout)

        containerLayout.addView(View(this).apply {
            setBackgroundColor(btnColor and 0x33FFFFFF)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { setMargins(0, 8, 0, 16) }
        })

        val tvTypeHeader = TextView(this).apply {
            text = getString(R.string.hint_nutrition_type)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        containerLayout.addView(tvTypeHeader)

        val mealTypes = listOf(
            getString(R.string.meal_breakfast),
            getString(R.string.meal_lunch),
            getString(R.string.meal_dinner),
            getString(R.string.meal_snack)
        )
        val selectedTypes = mutableSetOf<String>()

        val typeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (8 * density).toInt())
        }

        mealTypes.forEach { typeName ->
            val cb = CheckBox(this).apply {
                text = typeName
                setTextColor(textColor)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedTypes.add(typeName) else selectedTypes.remove(typeName)
                }
            }
            typeRow.addView(cb)
        }
        val typeScrollView = HorizontalScrollView(this).apply {
            addView(typeRow)
            isFillViewport = true
        }
        containerLayout.addView(typeScrollView)

        containerLayout.addView(View(this).apply {
            setBackgroundColor(btnColor and 0x33FFFFFF)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { setMargins(0, 8, 0, 16) }
        })

        val tvDescHeader = TextView(this).apply {
            text = getString(R.string.hint_nutrition_description)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        containerLayout.addView(tvDescHeader)

        val selectedFoodItems = mutableSetOf<String>()
        val foodItemsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun populateFoodItems() {
            foodItemsContainer.removeAllViews()
            val list = HealthHelper.loadSavedFoodItems(this)
            list.forEach { food ->
                val cb = CheckBox(this).apply {
                    text = food
                    setTextColor(textColor)
                    isChecked = selectedFoodItems.contains(food)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) selectedFoodItems.add(food) else selectedFoodItems.remove(food)
                    }
                }
                foodItemsContainer.addView(cb)
            }
        }
        populateFoodItems()

        val btnAddFood = createStyledButton(getString(R.string.btn_add_food_item), primary = false).apply {
            setOnClickListener {
                showAddFoodItemDialog {
                    populateFoodItems()
                }
            }
        }

        val etCustomDesc = EditText(this).apply {
            hint = getString(R.string.hint_nutrition_description)
            setTextColor(textColor)
            setHintTextColor(textColor and 0x88FFFFFF.toInt())
        }

        containerLayout.addView(foodItemsContainer)
        containerLayout.addView(btnAddFood)
        containerLayout.addView(etCustomDesc)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_nutrition))
            .setView(scrollView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val finalDay = if (!specificDateString.isNullOrEmpty()) {
                    specificDateString!!
                } else {
                    selectedDays.joinToString(", ")
                }
                val finalType = selectedTypes.joinToString(", ")

                val foodListStr = selectedFoodItems.joinToString(", ")
                val customNoteStr = etCustomDesc.text.toString().trim()

                val finalDesc = buildString {
                    if (foodListStr.isNotEmpty()) append(foodListStr)
                    if (customNoteStr.isNotEmpty()) {
                        if (isNotEmpty()) append(" (").append(customNoteStr).append(")")
                        else append(customNoteStr)
                    }
                }

                if (finalDay.isNotEmpty() || finalType.isNotEmpty() || finalDesc.isNotEmpty()) {
                    val entries = HealthHelper.loadNutritionEntries(this)
                    entries.add(NutritionScheduleEntry(
                        dayOfWeek = finalDay,
                        mealType = finalType,
                        description = finalDesc
                    ))
                    HealthHelper.saveNutritionEntries(this, entries)
                    HealthHelper.addLogEntry(this, HealthLogEntry(
                        memberId = activeMemberIdString,
                        memberName = activeMemberNameString,
                        type = getString(R.string.nutrition_schedule_title),
                        value = "$finalDay - $finalType: $finalDesc"
                    ))
                    renderHealthPage()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show().let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun renderEnergyCard(logs: List<HealthLogEntry>) {
        val card = createCard()
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        val titleKey = getString(R.string.energy_level_title)
        val title = TextView(this).apply {
            text = titleKey
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
        }
        val subtitle = getLastLogSubtitle(titleKey, logs)

        var energyVal = 5
        val tvVal = TextView(this).apply {
            text = "$energyVal / 10"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 4)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
        }

        val seekBar = SeekBar(this).apply {
            max = 10
            progress = energyVal
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    energyVal = p
                    tvVal.text = "$energyVal / 10"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        val btnLog = createStyledButton(getString(R.string.btn_log_entry), primary = true).apply {
            setOnClickListener {
                HealthHelper.addLogEntry(this@HealthActivity, HealthLogEntry(
                    memberId = activeMemberIdString,
                    memberName = activeMemberNameString,
                    type = titleKey,
                    value = "$energyVal / 10"
                ))
                renderHealthPage()
            }
        }

        cardLayout.addView(title)
        cardLayout.addView(subtitle)
        cardLayout.addView(tvVal)
        cardLayout.addView(seekBar)
        cardLayout.addView(btnLog)

        renderCardLogs(cardLayout, logs) { it.type == titleKey }

        card.addView(cardLayout)
        container.addView(card)
    }

    private fun renderRestCard(logs: List<HealthLogEntry>) {
        val card = createCard()
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        val titleKey = getString(R.string.rest_title)
        val title = TextView(this).apply {
            text = titleKey
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
        }
        val subtitle = getLastLogSubtitle(titleKey, logs)

        val startTs = HealthHelper.getRestStartTime(this)
        val btnToggle = createStyledButton(
            if (startTs > 0) getString(R.string.btn_stop_rest) else getString(R.string.btn_start_rest),
            primary = true
        ).apply {
            setOnClickListener {
                val now = System.currentTimeMillis()
                if (startTs > 0) {
                    val durationMin = ((now - startTs) / 60000).toInt()
                    HealthHelper.setRestStartTime(this@HealthActivity, 0L)
                    HealthHelper.addLogEntry(this@HealthActivity, HealthLogEntry(
                        memberId = activeMemberIdString,
                        memberName = activeMemberNameString,
                        type = titleKey,
                        value = "$durationMin min"
                    ))
                } else {
                    HealthHelper.setRestStartTime(this@HealthActivity, now)
                }
                renderHealthPage()
            }
        }

        val quickRestLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(15, 30, 60).forEach { mins ->
            val btnQuick = createStyledButton(getString(R.string.btn_log_rest, mins), primary = false).apply {
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4,0,4,0) }
                setOnClickListener {
                    HealthHelper.addLogEntry(this@HealthActivity, HealthLogEntry(
                        memberId = activeMemberIdString,
                        memberName = activeMemberNameString,
                        type = titleKey,
                        value = "$mins min"
                    ))
                    renderHealthPage()
                }
            }
            quickRestLayout.addView(btnQuick)
        }

        cardLayout.addView(title)
        cardLayout.addView(subtitle)
        cardLayout.addView(btnToggle)
        cardLayout.addView(quickRestLayout)

        renderCardLogs(cardLayout, logs) { it.type == titleKey }

        card.addView(cardLayout)
        container.addView(card)
    }

    private fun renderWeightCard(logs: List<HealthLogEntry>) {
        val card = createCard()
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        val titleKey = getString(R.string.weight_tracker_title)
        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = titleKey
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnLog = createStyledButton(getString(R.string.btn_add), primary = false).apply {
            setOnClickListener { showLogWeightDialog() }
        }
        titleLayout.addView(title)
        titleLayout.addView(btnLog)
        cardLayout.addView(titleLayout)
        cardLayout.addView(getLastLogSubtitle(titleKey, logs))

        renderCardLogs(cardLayout, logs) { it.type == titleKey }

        card.addView(cardLayout)
        container.addView(card)
    }

    private fun showLogWeightDialog() {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.hint_weight_kg)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_log_weight))
            .setView(et)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val valStr = et.text.toString().trim()
                if (valStr.isNotEmpty()) {
                    HealthHelper.addLogEntry(this, HealthLogEntry(
                        memberId = activeMemberIdString,
                        memberName = activeMemberNameString,
                        type = getString(R.string.weight_tracker_title),
                        value = "$valStr kg"
                    ))
                    renderHealthPage()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show().let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun renderMedicationCard(logs: List<HealthLogEntry>) {
        val card = createCard()
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        val titleKey = getString(R.string.medication_title)
        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8)
        }
        val title = TextView(this).apply {
            text = titleKey
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnAdd = createStyledButton(getString(R.string.btn_add), primary = false).apply {
            setOnClickListener { showAddMedicationDialog() }
        }
        titleLayout.addView(title)
        titleLayout.addView(btnAdd)
        cardLayout.addView(titleLayout)
        cardLayout.addView(getLastLogSubtitle(titleKey, logs))

        val medications = HealthHelper.loadMedications(this)
        val todayMillis = System.currentTimeMillis()
        val tz = java.util.TimeZone.getDefault()
        val density = resources.displayMetrics.density

        medications.forEach { med ->
            val isTakenToday =
                med.isTakenToday && med.lastTakenTimestamp != null && HealthHelper.isSameDay(
                    med.lastTakenTimestamp!!,
                    todayMillis,
                    tz
                )
            if (med.isTakenToday != isTakenToday) {
                med.isTakenToday = isTakenToday
                HealthHelper.saveMedications(this, medications)
            }

            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    (8 * density).toInt(),
                    (6 * density).toInt(),
                    (8 * density).toInt(),
                    (6 * density).toInt()
                )
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(1, ColorHelper.getBtnColor(this@HealthActivity) and 0x22FFFFFF)
                    cornerRadius = 8f * density
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, (4 * density).toInt(), 0, (4 * density).toInt()) }
            }

            val checkBox = androidx.appcompat.widget.AppCompatCheckBox(this).apply {
                isChecked = isTakenToday
                buttonTintList =
                    ColorStateList.valueOf(ColorHelper.getBtnColor(this@HealthActivity))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, (2 * density).toInt(), 0) }
                setOnClickListener {
                    val newChecked = this.isChecked
                    med.isTakenToday = newChecked
                    if (newChecked) {
                        med.lastTakenTimestamp = System.currentTimeMillis()
                        HealthHelper.addLogEntry(
                            this@HealthActivity, HealthLogEntry(
                                memberId = activeMemberIdString,
                                memberName = activeMemberNameString,
                                type = titleKey,
                                value = "${med.name ?: ""}${if (!med.dosage.isNullOrEmpty()) " (${med.dosage})" else ""}"
                            )
                        )
                    }
                    HealthHelper.saveMedications(this@HealthActivity, medications)
                    renderHealthPage()
                }
            }

            val timeDisplayStr = if (isTakenToday && med.lastTakenTimestamp != null) {
                SimpleDateFormat(
                    "HH:mm",
                    Locale.getDefault()
                ).format(Date(med.lastTakenTimestamp!!))
            } else if (!med.scheduledTime.isNullOrEmpty()) {
                med.scheduledTime!!
            } else {
                "-"
            }

            val tvTime = TextView(this).apply {
                text = timeDisplayStr
                textSize = 11f
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                alpha = if (isTakenToday) 1.0f else 0.7f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    (52 * density).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, (6 * density).toInt(), 0) }
                setOnClickListener {
                    if (isTakenToday) {
                        showLogMedicationCustomTimeDialog(med)
                    } else {
                        showAddMedicationDialog(existingMed = med)
                    }
                }
            }

            val tvName = TextView(this).apply {
                text = med.name ?: ""
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                alpha = if (isTakenToday) 0.6f else 1.0f
                layoutParams = LinearLayout.LayoutParams(
                    (105 * density).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, (6 * density).toInt(), 0) }
                setOnClickListener { showAddMedicationDialog(existingMed = med) }
            }

            val tvDosage = TextView(this).apply {
                text = if (!med.dosage.isNullOrEmpty()) med.dosage else "-"
                textSize = 12f
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                alpha = if (isTakenToday) 0.5f else 0.85f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setOnClickListener { showAddMedicationDialog(existingMed = med) }
            }

            var deleteCountdown = 8
            val btnDel = createStyledButton("✕", primary = false).apply {
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                setPadding(0, 0, 0, 0)
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    (32 * density).toInt(),
                    (32 * density).toInt()
                )
                setOnClickListener {
                    deleteCountdown--
                    if (deleteCountdown <= 0) {
                        medications.remove(med)
                        HealthHelper.saveMedications(this@HealthActivity, medications)
                        renderHealthPage()
                    } else {
                        text = "$deleteCountdown"
                    }
                }
            }

            val longClickListener = View.OnLongClickListener {
                showAddMedicationDialog(existingMed = med)
                true
            }

            rowLayout.setOnLongClickListener(longClickListener)
            tvName.setOnLongClickListener(longClickListener)
            tvDosage.setOnLongClickListener(longClickListener)
            tvTime.setOnLongClickListener(longClickListener)

            rowLayout.addView(checkBox)
            rowLayout.addView(tvTime)
            rowLayout.addView(tvName)
            rowLayout.addView(tvDosage)
            rowLayout.addView(btnDel)


            cardLayout.addView(rowLayout)
        }

        val btnHistory = createStyledButton(
            getString(R.string.btn_medication_history),
            primary = false
        ).apply {
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (16 * density).toInt(), 0, 0)
            }
            setOnClickListener {
                showMedicationTimeline()
            }
        }

        cardLayout.addView(btnHistory)

        renderCardLogs(cardLayout, logs) { it.type == titleKey }

        card.addView(cardLayout)
        container.addView(card)
    }


        private fun showMedicationTimeline() {
        val medications = HealthHelper.loadMedications(this)
        val allLogs = HealthHelper.loadLogs(this)
        val titleKey = getString(R.string.medication_title)

        val medicationLogs = allLogs.filter { log ->
            log.type == titleKey ||
                    medications.any { med ->
                        val name = med.name.orEmpty()
                        name.isNotBlank() &&
                                log.value == "$name" +
                                if (med.dosage.isNotEmpty()) {
                                    " (${med.dosage})"
                                } else {
                                    ""
                                }
                    }
        }

        val medicationValues = medications.associateWith { med ->
            "${med.name.orEmpty()}" +
                    if (med.dosage.isNotEmpty()) {
                        " (${med.dosage})"
                    } else {
                        ""
                    }
        }

        val relevantLogs = medicationLogs.filter { log ->
            medicationValues.values.any { it == log.value }
        }

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val firstDay = Calendar.getInstance().apply {
            timeInMillis = relevantLogs.minOfOrNull { it.timestamp }
                ?: today.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dates = mutableListOf<Long>()
        val cursor = firstDay.clone() as Calendar

        while (!cursor.after(today)) {
            dates.add(cursor.timeInMillis)
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }

        if (dates.isEmpty()) {
            dates.add(today.timeInMillis)
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val textColor = ColorHelper.getTextColor(this)
        val themeColor = ColorHelper.getBtnColor(this)
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

        val matrix = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(TextView(this).apply {
            text = getString(R.string.medication_timeline_medicine)
            setTextColor(textColor)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(44))
            gravity = Gravity.CENTER_VERTICAL
        })

        dates.forEach { date ->
            header.addView(TextView(this).apply {
                text = dateFormat.format(Date(date))
                setTextColor(textColor)
                textSize = 11f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(44))
            })
        }

        matrix.addView(header)

        medications.forEach { med ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val medValue = medicationValues[med].orEmpty()

            row.addView(TextView(this).apply {
                text = buildString {
                    append(med.name.orEmpty())
                    if (med.dosage.isNotEmpty()) {
                        append("\n")
                        append(med.dosage)
                    }
                }
                setTextColor(textColor)
                textSize = 12f
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(dp(120), dp(48))
                gravity = Gravity.CENTER_VERTICAL
            })

            dates.forEach { date ->
                val taken = relevantLogs.any { log ->
                    log.value == medValue &&
                            HealthHelper.isSameDay(
                                log.timestamp,
                                date,
                                java.util.TimeZone.getDefault()
                            )
                }

                val dot = View(this).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(if (taken) themeColor else Color.TRANSPARENT)
                        setStroke(
                            dp(1),
                            if (taken) themeColor else
                                (textColor and 0x66FFFFFF)
                        )
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        dp(14),
                        dp(14)
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                    contentDescription = getString(
                        if (taken) R.string.medication_timeline_taken
                        else R.string.medication_timeline_not_recorded
                    )
                }

                val cell = FrameLayout(this).apply {
                    addView(
                        dot,
                        FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER)
                    )
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                }

                row.addView(cell)
            }

            matrix.addView(row)

            matrix.addView(View(this).apply {
                setBackgroundColor(themeColor and 0x33FFFFFF)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1)
                )
            })
        }

        val horizontalScroll = HorizontalScrollView(this).apply {
            isFillViewport = false
            addView(matrix)
        }

        val verticalScroll = ScrollView(this).apply {
            addView(horizontalScroll)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.btn_medication_history)
            .setView(verticalScroll)
            .setPositiveButton(R.string.close, null)
            .create()

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.96).toInt(),
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun showAddMedicationDialog(existingMed: MedicationItem? = null) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        val etName = EditText(this).apply {
            hint = getString(R.string.hint_medication_name)
            setText(existingMed?.name ?: "")
        }
        val etDosage = EditText(this).apply {
            hint = getString(R.string.hint_medication_dosage)
            setText(existingMed?.dosage ?: "")
        }

        var selectedTimeStr = existingMed?.scheduledTime ?: ""
        val btnSelectTime = createStyledButton(
            if (selectedTimeStr.isNotEmpty()) "⏰ $selectedTimeStr" else "⏰ ${getString(R.string.label_select_date_time)}",
            primary = false
        ).apply {
            setOnClickListener {
                val initHour = if (selectedTimeStr.contains(":")) selectedTimeStr.split(":")[0].toIntOrNull() ?: 8 else 8
                val initMin = if (selectedTimeStr.contains(":")) selectedTimeStr.split(":")[1].toIntOrNull() ?: 0 else 0
                android.app.TimePickerDialog(
                    this@HealthActivity,
                    { _, hourOfDay, minute ->
                        selectedTimeStr = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                        text = "⏰ $selectedTimeStr"
                    },
                    initHour,
                    initMin,
                    true
                ).show()
            }
        }

        layout.addView(etName)
        layout.addView(etDosage)
        layout.addView(btnSelectTime)

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existingMed == null) getString(R.string.dialog_add_medication) else getString(R.string.dialog_add_medication))
            .setView(layout)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = etName.text.toString().trim()
                val dosage = etDosage.text.toString().trim()
                if (name.isNotEmpty()) {
                    val list = HealthHelper.loadMedications(this)
                    if (existingMed != null) {
                        val idx = list.indexOfFirst { it.id == existingMed.id }
                        if (idx >= 0) {
                            list[idx].name = name
                            list[idx].dosage = dosage
                            list[idx].scheduledTime = selectedTimeStr
                        }
                    } else {
                        list.add(MedicationItem(name = name, dosage = dosage, scheduledTime = selectedTimeStr))
                    }
                    HealthHelper.saveMedications(this, list)
                    renderHealthPage()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)

        val dialog = builder.create()
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun showLogMedicationCustomTimeDialog(med: MedicationItem) {
        var logTimestamp = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        val tvInfo = TextView(this).apply {
            text = "${med.name}${if (med.dosage.isNotEmpty()) " (${med.dosage})" else ""}"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
        }
        val btnSelectDateTime = createStyledButton(dateTimeFormat.format(Date(logTimestamp)), primary = false).apply {
            setOnClickListener {
                android.app.DatePickerDialog(
                    this@HealthActivity,
                    { _, year, month, dayOfMonth ->
                        cal.set(Calendar.YEAR, year)
                        cal.set(Calendar.MONTH, month)
                        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        android.app.TimePickerDialog(
                            this@HealthActivity,
                            { _, hourOfDay, minute ->
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                cal.set(Calendar.MINUTE, minute)
                                logTimestamp = cal.timeInMillis
                                text = dateTimeFormat.format(Date(logTimestamp))
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true
                        ).show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }
        layout.addView(tvInfo)
        layout.addView(btnSelectDateTime)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.btn_take_medication))
            .setView(layout)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                med.isTakenToday = true
                med.lastTakenTimestamp = logTimestamp
                val medications = HealthHelper.loadMedications(this)
                val idx = medications.indexOfFirst { it.id == med.id }
                if (idx >= 0) medications[idx] = med
                HealthHelper.saveMedications(this, medications)

                val titleKey = getString(R.string.medication_title)
                HealthHelper.addLogEntry(this, HealthLogEntry(
                    timestamp = logTimestamp,
                    memberId = activeMemberIdString,
                    memberName = activeMemberNameString,
                    type = titleKey,
                    value = "${med.name}${if (med.dosage.isNotEmpty()) " (${med.dosage})" else ""}"
                ))
                renderHealthPage()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show().let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun showEditLogEntryDialog(log: HealthLogEntry) {
        var selectedTimestamp = log.timestamp
        val selectedLogMemberIds = mutableListOf<String>()
        val selectedLogMemberNames = mutableListOf<String>()

        if (!log.memberId.isNullOrEmpty()) {
            selectedLogMemberIds.addAll(log.memberId!!.split(","))
        }
        if (!log.memberName.isNullOrEmpty()) {
            selectedLogMemberNames.addAll(log.memberName!!.split(", "))
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        val tvMember = TextView(this).apply {
            val displayName = if (selectedLogMemberNames.isNotEmpty()) selectedLogMemberNames.joinToString(", ") else getString(R.string.group_general)
            text = getString(R.string.logged_in_as, displayName)
            textSize = 14f
            setPadding(0, 8, 0, 12)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
        }

        val btnSelectMember = createStyledButton(getString(R.string.select_member), primary = false).apply {
            setOnClickListener {
                val optionsList = mutableListOf(getString(R.string.no_member_general))
                optionsList.addAll(systemMembers.map { it.name })

                val checkedArray = BooleanArray(optionsList.size)
                if (selectedLogMemberIds.isEmpty()) {
                    checkedArray[0] = true
                } else {
                    systemMembers.forEachIndexed { index, person ->
                        if (selectedLogMemberIds.contains(person.id)) {
                            checkedArray[index + 1] = true
                        }
                    }
                }

                val tempIds = mutableListOf<String>().apply { addAll(selectedLogMemberIds) }
                val tempNames = mutableListOf<String>().apply { addAll(selectedLogMemberNames) }

                var alertDialog: AlertDialog? = null
                var isUpdatingListView = false

                val builder = AlertDialog.Builder(this@HealthActivity)
                    .setTitle(getString(R.string.select_member))
                    .setMultiChoiceItems(optionsList.toTypedArray(), checkedArray) { _, which, isChecked ->
                        if (isUpdatingListView) return@setMultiChoiceItems
                        isUpdatingListView = true
                        try {
                            val listView = alertDialog?.listView
                            if (which == 0) {
                                if (isChecked) {
                                    tempIds.clear()
                                    tempNames.clear()
                                    for (i in 1 until checkedArray.size) {
                                        checkedArray[i] = false
                                        listView?.setItemChecked(i, false)
                                    }
                                } else {
                                    if (tempIds.isEmpty()) {
                                        checkedArray[0] = true
                                        listView?.setItemChecked(0, true)
                                    }
                                }
                            } else {
                                val person = systemMembers[which - 1]
                                if (isChecked) {
                                    checkedArray[0] = false
                                    listView?.setItemChecked(0, false)
                                    if (!tempIds.contains(person.id)) {
                                        tempIds.add(person.id)
                                        tempNames.add(person.name)
                                    }
                                } else {
                                    tempIds.remove(person.id)
                                    tempNames.remove(person.name)
                                    if (tempIds.isEmpty()) {
                                        checkedArray[0] = true
                                        listView?.setItemChecked(0, true)
                                    }
                                }
                            }
                        } finally {
                            isUpdatingListView = false
                        }
                    }
                    .setPositiveButton(getString(R.string.save)) { _, _ ->
                        selectedLogMemberIds.clear()
                        selectedLogMemberIds.addAll(tempIds)
                        selectedLogMemberNames.clear()
                        selectedLogMemberNames.addAll(tempNames)

                        val displayName = if (selectedLogMemberNames.isNotEmpty()) selectedLogMemberNames.joinToString(", ") else getString(R.string.group_general)
                        tvMember.text = getString(R.string.logged_in_as, displayName)
                    }
                    .setNegativeButton(getString(R.string.cancel), null)

                alertDialog = builder.create()
                alertDialog.show()
                ColorHelper.styleAlertDialog(alertDialog, this@HealthActivity)
            }
        }

        val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val tvDateTime = TextView(this).apply {
            text = dateTimeFormat.format(Date(selectedTimestamp))
            textSize = 14f
            setPadding(0, 16, 0, 12)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
        }

        val btnSelectDateTime = createStyledButton(getString(R.string.label_select_date_time), primary = false).apply {
            setOnClickListener {
                val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                android.app.DatePickerDialog(
                    this@HealthActivity,
                    { _, year, month, dayOfMonth ->
                        cal.set(Calendar.YEAR, year)
                        cal.set(Calendar.MONTH, month)
                        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        android.app.TimePickerDialog(
                            this@HealthActivity,
                            { _, hourOfDay, minute ->
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                cal.set(Calendar.MINUTE, minute)
                                selectedTimestamp = cal.timeInMillis
                                tvDateTime.text = dateTimeFormat.format(Date(selectedTimestamp))
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true
                        ).show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }

        val etValue = EditText(this).apply {
            setText(log.value)
            hint = "Details / Value"
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            setHintTextColor(ColorHelper.getTextColor(this@HealthActivity) and 0x88FFFFFF.toInt())
        }

        layout.addView(tvMember)
        layout.addView(btnSelectMember)
        layout.addView(tvDateTime)
        layout.addView(btnSelectDateTime)
        layout.addView(etValue)

        AlertDialog.Builder(this)
            .setTitle(log.type)
            .setView(layout)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                log.memberId = if (selectedLogMemberIds.isNotEmpty()) selectedLogMemberIds.joinToString(",") else null
                log.memberName = if (selectedLogMemberNames.isNotEmpty()) selectedLogMemberNames.joinToString(", ") else null
                log.timestamp = selectedTimestamp
                log.value = etValue.text.toString().trim()
                HealthHelper.saveOrUpdateLogEntry(this, log)
                renderHealthPage()
            }
            .setNeutralButton(getString(R.string.delete)) { _, _ ->
                HealthHelper.deleteLogEntry(this, log.id)
                renderHealthPage()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show().let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun renderSensationsCard(logs: List<HealthLogEntry>) {
        val card = createCard()
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        val titleKey = getString(R.string.sensations_title)
        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = titleKey
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnAdd = createStyledButton(getString(R.string.btn_add), primary = false).apply {
            setOnClickListener { showAddSensationDialog() }
        }
        titleLayout.addView(title)
        titleLayout.addView(btnAdd)

        val sensations = HealthHelper.loadSensations(this)
        val sensationNames = sensations.map { it.name }
        cardLayout.addView(titleLayout)
        cardLayout.addView(getLastLogSubtitle(titleKey, logs, sensationNames))

        sensations.forEach { sensation ->
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 4, 0, 12)
            }
            val headerLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            var intensityVal = sensation.intensity
            val tvName = TextView(this).apply {
                text = "${sensation.name}: $intensityVal/10"
                textSize = 13f
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnLog = createStyledButton(getString(R.string.btn_log_entry), primary = true).apply {
                setOnClickListener {
                    HealthHelper.addLogEntry(this@HealthActivity, HealthLogEntry(
                        memberId = activeMemberIdString,
                        memberName = activeMemberNameString,
                        type = sensation.name,
                        value = "$intensityVal / 10"
                    ))
                    renderHealthPage()
                }
            }
            val btnDel = createStyledButton("✕", primary = false).apply {
                setOnClickListener {
                    sensations.remove(sensation)
                    HealthHelper.saveSensations(this@HealthActivity, sensations)
                    renderHealthPage()
                }
            }
            headerLayout.addView(tvName)
            headerLayout.addView(btnLog)
            headerLayout.addView(btnDel)

            val seekBar = SeekBar(this).apply {
                max = 10
                progress = intensityVal
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        intensityVal = p
                        sensation.intensity = p
                        tvName.text = "${sensation.name}: $intensityVal/10"
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {
                        HealthHelper.saveSensations(this@HealthActivity, sensations)
                    }
                })
            }

            itemLayout.addView(headerLayout)
            itemLayout.addView(seekBar)
            cardLayout.addView(itemLayout)
        }

        renderCardLogs(cardLayout, logs) { it.type == titleKey || sensationNames.contains(it.type) }

        card.addView(cardLayout)
        container.addView(card)
    }

    private fun showAddSensationDialog() {
        val et = EditText(this).apply { hint = getString(R.string.hint_sensation_name) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_sensation))
            .setView(et)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) {
                    val list = HealthHelper.loadSensations(this)
                    list.add(PhysicalSensation(name = name))
                    HealthHelper.saveSensations(this, list)
                    renderHealthPage()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show().let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun renderCustomCountersCard(logs: List<HealthLogEntry>) {
        val card = createCard()
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        val titleKey = getString(R.string.custom_counters_title)
        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = titleKey
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnAdd = createStyledButton(getString(R.string.btn_add), primary = false).apply {
            setOnClickListener { showAddCustomCounterDialog() }
        }
        titleLayout.addView(title)
        titleLayout.addView(btnAdd)

        val counters = HealthHelper.loadCustomCounters(this)
        val counterNames = counters.map { it.name }
        cardLayout.addView(titleLayout)
        cardLayout.addView(getLastLogSubtitle(titleKey, logs, counterNames))

        counters.forEach { counter ->
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }
            val tv = TextView(this).apply {
                text = "${counter.name}: ${counter.count} / ${counter.target}"
                textSize = 13f
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnMinus = createStyledButton("-", primary = false).apply {
                setOnClickListener {
                    if (counter.count > 0) {
                        counter.count--
                        HealthHelper.saveCustomCounters(this@HealthActivity, counters)
                        tv.text = "${counter.name}: ${counter.count} / ${counter.target}"
                    }
                }
            }
            val btnPlus = createStyledButton("+", primary = true).apply {
                setOnClickListener {
                    counter.count++
                    HealthHelper.saveCustomCounters(this@HealthActivity, counters)
                    tv.text = "${counter.name}: ${counter.count} / ${counter.target}"
                    HealthHelper.addLogEntry(this@HealthActivity, HealthLogEntry(
                        memberId = activeMemberIdString,
                        memberName = activeMemberNameString,
                        type = counter.name,
                        value = "${counter.count}"
                    ))
                    renderHealthPage()
                }
            }
            val btnDel = createStyledButton("✕", primary = false).apply {
                setOnClickListener {
                    counters.remove(counter)
                    HealthHelper.saveCustomCounters(this@HealthActivity, counters)
                    renderHealthPage()
                }
            }
            itemLayout.addView(tv)
            itemLayout.addView(btnMinus)
            itemLayout.addView(btnPlus)
            itemLayout.addView(btnDel)
            cardLayout.addView(itemLayout)
        }

        renderCardLogs(cardLayout, logs) { it.type == titleKey || counterNames.contains(it.type) }

        card.addView(cardLayout)
        container.addView(card)
    }

    private fun showAddCustomCounterDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        val etName = EditText(this).apply { hint = getString(R.string.hint_counter_name) }
        val etTarget = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.hint_counter_target)
        }
        layout.addView(etName)
        layout.addView(etTarget)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_counter))
            .setView(layout)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = etName.text.toString().trim()
                val target = etTarget.text.toString().toIntOrNull() ?: 8
                if (name.isNotEmpty()) {
                    val list = HealthHelper.loadCustomCounters(this)
                    list.add(CustomCounter(name = name, target = target))
                    HealthHelper.saveCustomCounters(this, list)
                    renderHealthPage()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show().let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun renderCustomSlidersCard(logs: List<HealthLogEntry>) {
        val card = createCard()
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        val titleKey = getString(R.string.custom_sliders_title)
        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = titleKey
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ColorHelper.getTextColor(this@HealthActivity))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnAdd = createStyledButton(getString(R.string.btn_add), primary = false).apply {
            setOnClickListener { showAddCustomSliderDialog() }
        }
        titleLayout.addView(title)
        titleLayout.addView(btnAdd)

        val sliders = HealthHelper.loadCustomSliders(this)
        val sliderNames = sliders.map { it.name }
        cardLayout.addView(titleLayout)
        cardLayout.addView(getLastLogSubtitle(titleKey, logs, sliderNames))

        sliders.forEach { slider ->
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 4, 0, 12)
            }
            val headerLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            var valProgress = slider.value
            val tvName = TextView(this).apply {
                text = "${slider.name}: $valProgress%"
                textSize = 13f
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnLog = createStyledButton(getString(R.string.btn_log_entry), primary = true).apply {
                setOnClickListener {
                    HealthHelper.addLogEntry(this@HealthActivity, HealthLogEntry(
                        memberId = activeMemberIdString,
                        memberName = activeMemberNameString,
                        type = slider.name,
                        value = "$valProgress%"
                    ))
                    renderHealthPage()
                }
            }
            val btnDel = createStyledButton("✕", primary = false).apply {
                setOnClickListener {
                    sliders.remove(slider)
                    HealthHelper.saveCustomSliders(this@HealthActivity, sliders)
                    renderHealthPage()
                }
            }
            headerLayout.addView(tvName)
            headerLayout.addView(btnLog)
            headerLayout.addView(btnDel)

            val seekBar = SeekBar(this).apply {
                max = 100
                progress = valProgress
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        valProgress = p
                        slider.value = p
                        tvName.text = "${slider.name}: $valProgress%"
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {
                        HealthHelper.saveCustomSliders(this@HealthActivity, sliders)
                    }
                })
            }

            itemLayout.addView(headerLayout)
            itemLayout.addView(seekBar)
            cardLayout.addView(itemLayout)
        }

        renderCardLogs(cardLayout, logs) { it.type == titleKey || sliderNames.contains(it.type) }

        card.addView(cardLayout)
        container.addView(card)
    }

    private fun showAddCustomSliderDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        val etName = EditText(this).apply { hint = getString(R.string.hint_slider_name) }
        val etMin = EditText(this).apply { hint = getString(R.string.hint_min_label) }
        val etMax = EditText(this).apply { hint = getString(R.string.hint_max_label) }
        layout.addView(etName)
        layout.addView(etMin)
        layout.addView(etMax)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_slider))
            .setView(layout)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = etName.text.toString().trim()
                val minL = etMin.text.toString().trim()
                val maxL = etMax.text.toString().trim()
                if (name.isNotEmpty()) {
                    val list = HealthHelper.loadCustomSliders(this)
                    list.add(CustomSlider(name = name, minLabel = minL, maxLabel = maxL))
                    HealthHelper.saveCustomSliders(this, list)
                    renderHealthPage()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show().let { ColorHelper.styleAlertDialog(it, this) }
    }

    private fun showFoodItemsDialog(
        mealName: String,
        record: EatenMealRecord?,
        defaultDateMillis: Long,
        onSaved: () -> Unit
    ) {
        val existingRecord = record ?: EatenMealRecord(
            mealType = mealName,
            timestamp = defaultDateMillis,
            memberId = activeMemberIdString,
            memberName = activeMemberNameString,
            isChecked = true
        )

        val savedFoodList = HealthHelper.loadSavedFoodItems(this)
        val selectedItems = existingRecord.eatenItems.toMutableSet()

        var listChanged = false
        selectedItems.forEach { item ->
            if (!savedFoodList.contains(item)) {
                savedFoodList.add(item)
                listChanged = true
            }
        }
        if (listChanged) {
            HealthHelper.saveSavedFoodItems(this, savedFoodList)
        }

        fun openDialog() {
            val checkedArray = BooleanArray(savedFoodList.size) { i ->
                selectedItems.contains(savedFoodList[i])
            }

            val builder = AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_eaten_items, mealName))

            if (savedFoodList.isNotEmpty()) {
                builder.setMultiChoiceItems(savedFoodList.toTypedArray(), checkedArray) { _, which: Int, isChecked: Boolean ->
                    val item = savedFoodList[which]
                    if (isChecked) selectedItems.add(item) else selectedItems.remove(item)
                }
            }

            builder.setPositiveButton(getString(R.string.save)) { _, _ ->
                existingRecord.eatenItems = selectedItems.toList()
                existingRecord.isChecked = true
                HealthHelper.saveOrUpdateEatenRecord(this, existingRecord)
                onSaved()
            }
                .setNeutralButton(getString(R.string.btn_add_food_item)) { _, _ ->
                    showAddFoodItemDialog { openDialog() }
                }
                .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                    existingRecord.eatenItems = selectedItems.toList()
                    HealthHelper.saveOrUpdateEatenRecord(this, existingRecord)
                    onSaved()
                }

            val dialog = builder.create()
            dialog.show()
            ColorHelper.styleAlertDialog(dialog, this)

            if (savedFoodList.isNotEmpty()) {
                dialog.listView?.setOnItemLongClickListener { _, _, position, _ ->
                    val itemToRemove = savedFoodList[position]
                    AlertDialog.Builder(this)
                        .setTitle(itemToRemove)
                        .setMessage(getString(R.string.delete) + "?")
                        .setPositiveButton(getString(R.string.delete)) { _, _ ->
                            savedFoodList.removeAt(position)
                            selectedItems.remove(itemToRemove)
                            HealthHelper.saveSavedFoodItems(this, savedFoodList)
                            existingRecord.eatenItems = selectedItems.toList()
                            HealthHelper.saveOrUpdateEatenRecord(this, existingRecord)

                            dialog.dismiss()
                            openDialog()
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show().let { ColorHelper.styleAlertDialog(it, this) }
                    true
                }
            }
        }

        openDialog()
    }

    private fun showAddFoodItemDialog(onAdded: () -> Unit) {
        val et = EditText(this).apply { hint = getString(R.string.hint_food_item_name) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_add_food_item))
            .setView(et)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newItem = et.text.toString().trim()
                if (newItem.isNotEmpty()) {
                    val list = HealthHelper.loadSavedFoodItems(this)
                    if (!list.contains(newItem)) {
                        list.add(newItem)
                        HealthHelper.saveSavedFoodItems(this, list)
                    }
                }
                onAdded()
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> onAdded() }
            .show().let { ColorHelper.styleAlertDialog(it, this) }
    }
}