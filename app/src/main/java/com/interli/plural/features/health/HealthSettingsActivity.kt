package com.interli.plural.features.health

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.interli.plural.R
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper

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

        findViewById<MaterialButton>(R.id.btnAddCustomCounter).setOnClickListener { showAddCustomCounterDialog() }
        findViewById<MaterialButton>(R.id.btnAddCustomSlider).setOnClickListener { showAddCustomSliderDialog() }
        findViewById<MaterialButton>(R.id.btnAddSensation).setOnClickListener { showAddSensationDialog() }
        findViewById<MaterialButton>(R.id.btnAddMedication).setOnClickListener { showAddMedicationDialog() }
    }

    private fun save() {
        HealthHelper.saveSettings(this, settings)
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