package com.interli.plural.features.health

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.interli.plural.Person
import com.interli.plural.R
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.member.MemberHelper
import java.util.Calendar
import java.util.Date

class EatenTimelineActivity : BaseActivity() {

    private lateinit var container: LinearLayout
    private var systemMembers: List<Person> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootDrawer = DrawerLayout(this).apply {
            id = View.generateViewId()
            fitsSystemWindows = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val toolbar = MaterialToolbar(this).apply {
            id = R.id.topAppBar
            title = getString(R.string.eaten_timeline_title)
            val typedValue = android.util.TypedValue()
            val actionBarHeight = if (theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
                android.util.TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
            } else {
                (56 * resources.displayMetrics.density).toInt()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                actionBarHeight
            )
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        }

        val scrollView = NestedScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding(32, 32, 32, 32)
        }

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        scrollView.addView(container)
        mainLayout.addView(toolbar)
        mainLayout.addView(scrollView)

        val navView = NavigationView(this).apply {
            id = R.id.navigationView
            layoutParams = DrawerLayout.LayoutParams(
                DrawerLayout.LayoutParams.WRAP_CONTENT,
                DrawerLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.START
            }
            inflateMenu(R.menu.main_menu)
        }

        rootDrawer.addView(mainLayout)
        rootDrawer.addView(navView)

        setContentView(rootDrawer)
        setupNavigationDrawer()

        systemMembers = MemberHelper.loadAllPeople(this).filter { !it.isArchived }
    }

    override fun onResume() {
        super.onResume()
        renderTimeline()
    }

    private fun renderTimeline() {
        container.removeAllViews()
        val allRecords = HealthHelper.loadEatenRecords(this)
        val density = resources.displayMetrics.density

        val dayGroupMap = mutableMapOf<Long, MutableList<EatenMealRecord>>()

        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = todayCal.timeInMillis
        dayGroupMap[todayStart] = mutableListOf()

        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayStart = yesterdayCal.timeInMillis

        allRecords.forEach { record ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = record.timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = cal.timeInMillis
            dayGroupMap.getOrPut(startOfDay) { mutableListOf() }.add(record)
        }

        val sortedDays = dayGroupMap.keys.sortedDescending()
        val meals = listOf(
            getString(R.string.meal_breakfast),
            getString(R.string.meal_lunch),
            getString(R.string.meal_dinner)
        )

        val daySdf = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.getDefault())

        sortedDays.forEach { dayMillis ->
            val recordsForDay = dayGroupMap[dayMillis] ?: mutableListOf()
            val isToday = dayMillis == todayStart
            val isYesterday = dayMillis == yesterdayStart

            val card = MaterialCardView(this).apply {
                radius = 16f
                cardElevation = 2f
                useCompatPadding = true
                setCardBackgroundColor(ColorHelper.getBgColor(this@EatenTimelineActivity))
                strokeColor = ColorHelper.getBtnColor(this@EatenTimelineActivity) and 0x44FFFFFF
                strokeWidth = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, (16 * density).toInt()) }
            }

            val cardLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 20, 20, 20)
            }

            val formattedDate = daySdf.format(Date(dayMillis))
            val dayTitleStr = when {
                isToday -> "Vandaag, $formattedDate"
                isYesterday -> "Gisteren, $formattedDate"
                else -> formattedDate
            }

            val tvDayHeader = TextView(this).apply {
                text = dayTitleStr
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ColorHelper.getTextColor(this@EatenTimelineActivity))
                setPadding(0, 0, 0, 12)
            }
            cardLayout.addView(tvDayHeader)

            meals.forEach { meal ->
                val record = HealthHelper.getEatenRecordFromList(recordsForDay, dayMillis, meal)
                val isChecked = record != null && record.isChecked

                val rowLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.TRANSPARENT)
                        setStroke(1, ColorHelper.getBtnColor(this@EatenTimelineActivity) and 0x22FFFFFF)
                        cornerRadius = 8f * density
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, (4 * density).toInt(), 0, (4 * density).toInt()) }
                }

                // 1. Vinkje
                val checkBox = androidx.appcompat.widget.AppCompatCheckBox(this).apply {
                    this.isChecked = isChecked
                    buttonTintList = ColorStateList.valueOf(ColorHelper.getBtnColor(this@EatenTimelineActivity))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, (2 * density).toInt(), 0) }
                    setOnClickListener {
                        val newChecked = this.isChecked
                        if (newChecked) {
                            val newTs = if (isToday) System.currentTimeMillis() else dayMillis + 12 * 3600000L
                            val newRecord = record ?: EatenMealRecord(
                                mealType = meal,
                                timestamp = newTs,
                                isChecked = true
                            )
                            newRecord.isChecked = true
                            HealthHelper.saveOrUpdateEatenRecord(this@EatenTimelineActivity, newRecord)
                        } else {
                            record?.let {
                                it.isChecked = false
                                HealthHelper.saveOrUpdateEatenRecord(this@EatenTimelineActivity, it)
                            }
                        }
                        renderTimeline()
                    }
                }

                // 2. Benaming Maaltijd (75dp)
                val tvMealName = TextView(this).apply {
                    text = meal
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(ColorHelper.getTextColor(this@EatenTimelineActivity))
                    layoutParams = LinearLayout.LayoutParams(
                        (75 * density).toInt(),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, (4 * density).toInt(), 0) }
                }

                // 3. De Knop (weight = 1f)
                val foodText = if (record != null && record.eatenItems.isNotEmpty()) {
                    record.eatenItems.joinToString(", ")
                } else {
                    "+"
                }

                val btnFood = MaterialButton(this).apply {
                    text = foodText
                    textSize = 11f
                    isAllCaps = false
                    setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
                    alpha = if (isChecked) 1.0f else 0.6f
                    styleButton(this, primary = false)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply { setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0) }
                    setOnClickListener {
                        showFoodItemsDialog(meal, record, dayMillis) {
                            renderTimeline()
                        }
                    }
                }

                // 4. Tijd (55dp)
                val timeStr = if (isChecked && record != null) {
                    DateFormat.getTimeFormat(this).format(Date(record.timestamp))
                } else {
                    "-"
                }

                val tvTime = TextView(this).apply {
                    text = timeStr
                    textSize = 11f
                    setTextColor(ColorHelper.getTextColor(this@EatenTimelineActivity))
                    alpha = if (isChecked) 1.0f else 0.5f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        (55 * density).toInt(),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                // 5. Lid (50dp)
                val mName = if (isChecked && record != null) {
                    record.memberName ?: getString(R.string.unnamed_field)
                } else {
                    "-"
                }

                val tvMember = TextView(this).apply {
                    text = mName
                    textSize = 11f
                    setTextColor(ColorHelper.getTextColor(this@EatenTimelineActivity))
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
                    showEditEatenMealDialog(meal, record, dayMillis) {
                        renderTimeline()
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

            card.addView(cardLayout)
            container.addView(card)
        }
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
            isChecked = true
        )

        var selectedMemberId = existingRecord.memberId
        var selectedMemberName = existingRecord.memberName ?: getString(R.string.unnamed_field)
        var selectedTimestamp = existingRecord.timestamp

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        val tvMember = TextView(this).apply {
            text = getString(R.string.logged_in_as, selectedMemberName)
            textSize = 14f
            setPadding(0, 8, 0, 12)
            setTextColor(ColorHelper.getTextColor(this@EatenTimelineActivity))
        }

        val btnSelectMember = MaterialButton(this).apply {
            text = getString(R.string.select_member)
            styleButton(this, primary = false)
            setOnClickListener {
                if (systemMembers.isNotEmpty()) {
                    val names = systemMembers.map { it.name }.toTypedArray()
                    AlertDialog.Builder(this@EatenTimelineActivity)
                        .setTitle(getString(R.string.select_member))
                        .setItems(names) { _, which ->
                            val m = systemMembers[which]
                            selectedMemberId = m.id
                            selectedMemberName = m.name
                            tvMember.text = getString(R.string.logged_in_as, selectedMemberName)
                        }
                        .show().let { ColorHelper.styleAlertDialog(it, this@EatenTimelineActivity) }
                }
            }
        }

        val dateFormat = DateFormat.getDateFormat(this)
        val timeFormat = DateFormat.getTimeFormat(this)

        val tvDateTime = TextView(this).apply {
            text = "${dateFormat.format(Date(selectedTimestamp))} ${timeFormat.format(Date(selectedTimestamp))}"
            textSize = 14f
            setPadding(0, 16, 0, 12)
            setTextColor(ColorHelper.getTextColor(this@EatenTimelineActivity))
        }

        val btnSelectDateTime = MaterialButton(this).apply {
            text = getString(R.string.label_select_date_time)
            styleButton(this, primary = false)
            setOnClickListener {
                val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                android.app.DatePickerDialog(
                    this@EatenTimelineActivity,
                    { _, year, month, dayOfMonth ->
                        cal.set(Calendar.YEAR, year)
                        cal.set(Calendar.MONTH, month)
                        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        android.app.TimePickerDialog(
                            this@EatenTimelineActivity,
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
                existingRecord.memberId = selectedMemberId
                existingRecord.memberName = selectedMemberName
                existingRecord.timestamp = selectedTimestamp
                existingRecord.isChecked = true
                HealthHelper.saveOrUpdateEatenRecord(this, existingRecord)
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

    private fun styleButton(btn: MaterialButton, primary: Boolean = true) {
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
    private fun showFoodItemsDialog(
        mealName: String,
        record: EatenMealRecord?,
        defaultDateMillis: Long,
        onSaved: () -> Unit
    ) {
        val existingRecord = record ?: EatenMealRecord(
            mealType = mealName,
            timestamp = defaultDateMillis,
            isChecked = true
        )

        val savedFoodList = HealthHelper.loadSavedFoodItems(this)
        val selectedItems = existingRecord.eatenItems.toMutableSet()

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
                .setNegativeButton(getString(R.string.cancel), null)

            builder.show().let { ColorHelper.styleAlertDialog(it, this) }
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