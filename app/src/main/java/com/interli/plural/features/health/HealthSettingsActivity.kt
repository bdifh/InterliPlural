package com.interli.plural.features.health

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.interli.plural.R
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import java.util.Collections

class HealthSettingsActivity : BaseActivity() {

    private lateinit var settings: HealthSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_settings)
        setupNavigationDrawer()

        settings = HealthHelper.loadSettings(this)

        val switchHydration = findViewById<SwitchCompat>(R.id.switchHydration)
        val switchEaten = findViewById<SwitchCompat>(R.id.switchEaten)
        val switchNutrition = findViewById<SwitchCompat>(R.id.switchNutrition)
        val switchEnergy = findViewById<SwitchCompat>(R.id.switchEnergy)
        val switchRest = findViewById<SwitchCompat>(R.id.switchRest)
        val switchSensations = findViewById<SwitchCompat>(R.id.switchSensations)
        val switchCustomSliders = findViewById<SwitchCompat>(R.id.switchCustomSliders)
        val switchCustomCounters = findViewById<SwitchCompat>(R.id.switchCustomCounters)
        val switchMedication = findViewById<SwitchCompat>(R.id.switchMedication)
        val switchWeight = findViewById<SwitchCompat>(R.id.switchWeight)

        switchHydration.isChecked = settings.showHydration
        switchEaten.isChecked = settings.showEatenCheck
        switchNutrition.isChecked = settings.showNutritionSchedule
        switchEnergy.isChecked = settings.showEnergySlider
        switchRest.isChecked = settings.showRest
        switchSensations.isChecked = settings.showSensations
        switchCustomSliders.isChecked = settings.showCustomSliders
        switchCustomCounters.isChecked = settings.showCustomCounters
        switchMedication.isChecked = settings.showMedication
        switchWeight.isChecked = settings.showWeight

        switchHydration.setOnCheckedChangeListener { _, c -> settings.showHydration = c; save() }
        switchEaten.setOnCheckedChangeListener { _, c -> settings.showEatenCheck = c; save() }
        switchNutrition.setOnCheckedChangeListener { _, c -> settings.showNutritionSchedule = c; save() }
        switchEnergy.setOnCheckedChangeListener { _, c -> settings.showEnergySlider = c; save() }
        switchRest.setOnCheckedChangeListener { _, c -> settings.showRest = c; save() }
        switchSensations.setOnCheckedChangeListener { _, c -> settings.showSensations = c; save() }
        switchCustomSliders.setOnCheckedChangeListener { _, c -> settings.showCustomSliders = c; save() }
        switchCustomCounters.setOnCheckedChangeListener { _, c -> settings.showCustomCounters = c; save() }
        switchMedication.setOnCheckedChangeListener { _, c -> settings.showMedication = c; save() }
        switchWeight.setOnCheckedChangeListener { _, c -> settings.showWeight = c; save() }

        findViewById<MaterialButton>(R.id.btnReorderCards)?.setOnClickListener { showReorderCardsDialog() }
        findViewById<MaterialButton>(R.id.btnAddCustomCounter).setOnClickListener { showAddCustomCounterDialog() }
        findViewById<MaterialButton>(R.id.btnAddCustomSlider).setOnClickListener { showAddCustomSliderDialog() }
        findViewById<MaterialButton>(R.id.btnAddSensation).setOnClickListener { showAddSensationDialog() }
        findViewById<MaterialButton>(R.id.btnAddMedication).setOnClickListener { showAddMedicationDialog() }
        findViewById<MaterialButton>(R.id.btnSaveHealthSettings)?.setOnClickListener {
            save()
            android.widget.Toast.makeText(this, getString(R.string.settings_saved), android.widget.Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun save() {
        HealthHelper.saveSettings(this, settings)
    }

    private fun showReorderCardsDialog() {
        val cardNamesMap = mapOf(
            "hydration" to getString(R.string.switch_hydration),
            "eaten" to getString(R.string.switch_eaten),
            "nutrition" to getString(R.string.switch_nutrition),
            "energy" to getString(R.string.switch_energy),
            "rest" to getString(R.string.switch_rest),
            "weight" to getString(R.string.switch_weight),
            "medication" to getString(R.string.switch_medication),
            "sensations" to getString(R.string.switch_sensations),
            "counters" to getString(R.string.switch_custom_counters),
            "sliders" to getString(R.string.switch_custom_sliders)
        )

        val currentOrder = settings.getNormalizedCardOrder().toMutableList()
        val density = resources.displayMetrics.density

        class CardViewHolder(val view: LinearLayout, val tvHandle: TextView, val tvName: TextView) :
            RecyclerView.ViewHolder(view)

        var itemTouchHelper: ItemTouchHelper? = null

        val adapter = object : RecyclerView.Adapter<CardViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
                val row = LinearLayout(this@HealthSettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
                    layoutParams = RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT,
                        RecyclerView.LayoutParams.WRAP_CONTENT
                    )
                }

                val handle = TextView(this@HealthSettingsActivity).apply {
                    text = "☰"
                    textSize = 18f
                    setTextColor(ColorHelper.getTextColor(this@HealthSettingsActivity))
                    alpha = 0.6f
                    setPadding(0, 0, (16 * density).toInt(), 0)
                }

                val name = TextView(this@HealthSettingsActivity).apply {
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(ColorHelper.getTextColor(this@HealthSettingsActivity))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                row.addView(handle)
                row.addView(name)

                return CardViewHolder(row, handle, name)
            }

            override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
                val cardKey = currentOrder[position]
                holder.tvName.text = cardNamesMap[cardKey] ?: cardKey

                val dragStartListener = View.OnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        itemTouchHelper?.startDrag(holder)
                    }
                    false
                }

                holder.tvHandle.setOnTouchListener(dragStartListener)
                holder.view.setOnTouchListener(dragStartListener)
            }

            override fun getItemCount() = currentOrder.size
        }

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@HealthSettingsActivity)
            this.adapter = adapter
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }

        itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                Collections.swap(currentOrder, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })

        itemTouchHelper.attachToRecyclerView(recyclerView)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.action_reorder)
            .setView(recyclerView)
            .setPositiveButton(R.string.save) { _, _ ->
                settings.cardOrder = currentOrder
                HealthHelper.saveSettings(this, settings)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
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
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show().let { ColorHelper.styleAlertDialog(it, this) }
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
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show().let { ColorHelper.styleAlertDialog(it, this) }
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
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show().let { ColorHelper.styleAlertDialog(it, this) }
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
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show().let { ColorHelper.styleAlertDialog(it, this) }
    }
}