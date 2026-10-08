package com.interli.plural.features.health

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
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

            val valStr = if (log.value.isNotEmpty()) "${log.type} (${log.value})" else log.type

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
        val todayLogs = allLogs.filter { HealthHelper.isSameDay(it.timestamp, todayMillis) }

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

        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_member))
            .setMultiChoiceItems(optionsList.toTypedArray(), checkedArray) { _, which, isChecked ->
                val listView = alertDialog?.listView
                if (which == 0) {
                    if (isChecked) {
                        tempSelectedIds.clear()
                        tempSelectedNames.clear()
                        for (i in 1 until checkedArray.size) {
                            checkedArray[i] = false
                            listView?.setItemChecked(i, false)
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

                val builder = AlertDialog.Builder(this@HealthActivity)
                    .setTitle(getString(R.string.select_member))
                    .setMultiChoiceItems(optionsList.toTypedArray(), checkedArray) { _, which, isChecked ->
                        val listView = alertDialog?.listView
                        if (which == 0) {
                            if (isChecked) {
                                tempMealIds.clear()
                                tempMealNames.clear()
                                for (i in 1 until checkedArray.size) {
                                    checkedArray[i] = false
                                    listView?.setItemChecked(i, false)
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
        val timeFormat = DateFormat.getTimeFormat(this)

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
        entries.forEach { entry ->
            val entryLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }
            val tv = TextView(this).apply {
                text = "${entry.dayOfWeek} [${entry.mealType}]: ${entry.description}"
                textSize = 13f
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnDel = createStyledButton("✕", primary = false).apply {
                setOnClickListener {
                    entries.remove(entry)
                    HealthHelper.saveNutritionEntries(this@HealthActivity, entries)
                    renderHealthPage()
                }
            }
            entryLayout.addView(tv)
            entryLayout.addView(btnDel)
            cardLayout.addView(entryLayout)
        }

        renderCardLogs(cardLayout, logs) { it.type == titleKey }

        card.addView(cardLayout)
        container.addView(card)
    }

    private fun showAddNutritionDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        val etDay = EditText(this).apply { hint = getString(R.string.hint_nutrition_day) }
        val etType = EditText(this).apply { hint = getString(R.string.hint_field_title) }
        val etDesc = EditText(this).apply { hint = getString(R.string.hint_nutrition_description) }
        layout.addView(etDay)
        layout.addView(etType)
        layout.addView(etDesc)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_nutrition))
            .setView(layout)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val day = etDay.text.toString().trim()
                val type = etType.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (desc.isNotEmpty()) {
                    val entries = HealthHelper.loadNutritionEntries(this)
                    entries.add(NutritionScheduleEntry(dayOfWeek = day, mealType = type, description = desc))
                    HealthHelper.saveNutritionEntries(this, entries)
                    HealthHelper.addLogEntry(this, HealthLogEntry(
                        memberId = activeMemberIdString,
                        memberName = activeMemberNameString,
                        type = getString(R.string.nutrition_schedule_title),
                        value = "$day - $desc"
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
        medications.forEach { med ->
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }
            val tv = TextView(this).apply {
                text = "${med.name} (${med.dosage})"
                textSize = 13f
                setTextColor(ColorHelper.getTextColor(this@HealthActivity))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnTake = createStyledButton(getString(R.string.btn_take_medication), primary = true).apply {
                setOnClickListener {
                    med.isTakenToday = true
                    med.lastTakenTimestamp = System.currentTimeMillis()
                    HealthHelper.saveMedications(this@HealthActivity, medications)
                    HealthHelper.addLogEntry(this@HealthActivity, HealthLogEntry(
                        memberId = activeMemberIdString,
                        memberName = activeMemberNameString,
                        type = titleKey,
                        value = "${med.name} (${med.dosage})"
                    ))
                    renderHealthPage()
                }
            }
            val btnDel = createStyledButton("✕", primary = false).apply {
                setOnClickListener {
                    medications.remove(med)
                    HealthHelper.saveMedications(this@HealthActivity, medications)
                    renderHealthPage()
                }
            }
            itemLayout.addView(tv)
            itemLayout.addView(btnTake)
            itemLayout.addView(btnDel)
            cardLayout.addView(itemLayout)
        }

        renderCardLogs(cardLayout, logs) { it.type == titleKey }

        card.addView(cardLayout)
        container.addView(card)
    }

    private fun showAddMedicationDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        val etName = EditText(this).apply { hint = getString(R.string.hint_medication_name) }
        val etDosage = EditText(this).apply { hint = getString(R.string.hint_medication_dosage) }
        layout.addView(etName)
        layout.addView(etDosage)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_medication))
            .setView(layout)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = etName.text.toString().trim()
                val dosage = etDosage.text.toString().trim()
                if (name.isNotEmpty()) {
                    val list = HealthHelper.loadMedications(this)
                    list.add(MedicationItem(name = name, dosage = dosage))
                    HealthHelper.saveMedications(this, list)
                    renderHealthPage()
                }
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