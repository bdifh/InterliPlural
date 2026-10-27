package com.interli.plural.features.health

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.TimeZone

object HealthHelper {
    private const val PREFS_NAME = "health_prefs"
    private val gson = Gson()

    fun loadSettings(context: Context): HealthSettings {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString("settings", null) ?: return HealthSettings()
        return try {
            gson.fromJson(json, HealthSettings::class.java) ?: HealthSettings()
        } catch (_: Exception) {
            HealthSettings()
        }
    }

    fun saveSettings(context: Context, settings: HealthSettings) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString("settings", gson.toJson(settings)).apply()
    }

    fun loadEnergyRatingSettings(context: Context): EnergyRatingSettings {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString("energy_rating_settings", null)
            ?: return EnergyRatingSettings()

        return try {
            gson.fromJson(json, EnergyRatingSettings::class.java)
                ?: EnergyRatingSettings()
        } catch (_: Exception) {
            EnergyRatingSettings()
        }
    }

    fun saveEnergyRatingSettings(
        context: Context,
        settings: EnergyRatingSettings
    ) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putString("energy_rating_settings", gson.toJson(settings))
            .apply()
    }

    fun loadEnergyHistory(context: Context): MutableList<HealthLogEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val type = object : TypeToken<MutableList<HealthLogEntry>>() {}.type

        val historyJson = prefs.getString("energy_history", "[]")
        val history: MutableList<HealthLogEntry> =
            try {
                gson.fromJson(historyJson, type) ?: mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }

        if (!prefs.getBoolean("energy_history_migrated", false)) {
            val oldTypes = setOf("Energy Level", "Energieniveau")
            val existingIds = history.map { it.id }.toMutableSet()

            loadLogs(context)
                .filter { it.type in oldTypes && existingIds.add(it.id) }
                .forEach { history.add(it) }

            prefs.edit()
                .putString("energy_history", gson.toJson(history))
                .putBoolean("energy_history_migrated", true)
                .apply()
        }

        return history.sortedByDescending { it.timestamp }.toMutableList()
    }

    fun addEnergyHistoryEntry(context: Context, entry: HealthLogEntry) {
        val history = loadEnergyHistory(context)
        history.add(0, entry)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("energy_history", gson.toJson(history))
            .apply()
    }

    fun loadCustomCounters(context: Context): MutableList<CustomCounter> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString("custom_counters", "[]")
        val type = object : TypeToken<MutableList<CustomCounter>>() {}.type
        return try { gson.fromJson(json, type) ?: mutableListOf() } catch (_: Exception) { mutableListOf() }
    }

    fun saveCustomCounters(context: Context, list: List<CustomCounter>) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString("custom_counters", gson.toJson(list)).apply()
    }

    fun loadEatenMealTypes(
        context: Context,
        defaultMeals: List<String>
    ): MutableList<String> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString("eaten_meal_types", null)
        val type = object : TypeToken<MutableList<String>>() {}.type

        val meals: MutableList<String> = try {
            if (json == null) {
                defaultMeals.toMutableList()
            } else {
                gson.fromJson(json, type) ?: defaultMeals.toMutableList()
            }
        } catch (_: Exception) {
            defaultMeals.toMutableList()
        }

        if (loadEatenMealListSnapshots(context).isEmpty()) {
            saveEatenMealListSnapshot(
                context,
                getStartOfDayMillis(System.currentTimeMillis()),
                meals
            )
        }

        return meals
    }

    fun saveEatenMealTypes(context: Context, meals: List<String>) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString("eaten_meal_types", gson.toJson(meals)).apply()

        saveEatenMealListSnapshot(
            context,
            getStartOfDayMillis(System.currentTimeMillis()),
            meals
        )
    }

    fun loadEatenMealTypesForDate(
        context: Context,
        dateMillis: Long,
        defaultMeals: List<String>
    ): List<String> {
        var snapshots = loadEatenMealListSnapshots(context)

        if (snapshots.isEmpty()) {
            loadEatenMealTypes(context, defaultMeals)
            snapshots = loadEatenMealListSnapshots(context)
        }

        val requestedDay = getStartOfDayMillis(dateMillis)

        return snapshots
            .filter { it.dateStartMillis <= requestedDay }
            .maxByOrNull { it.dateStartMillis }
            ?.meals
            ?: defaultMeals
    }

    private fun loadEatenMealListSnapshots(
        context: Context
    ): MutableList<EatenMealListSnapshot> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString("eaten_meal_list_snapshots", "[]") ?: "[]"
        val type = object : TypeToken<MutableList<EatenMealListSnapshot>>() {}.type

        return try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveEatenMealListSnapshot(
        context: Context,
        dateStartMillis: Long,
        meals: List<String>
    ) {
        val snapshots = loadEatenMealListSnapshots(context)
        val index = snapshots.indexOfFirst {
            it.dateStartMillis == dateStartMillis
        }
        val snapshot = EatenMealListSnapshot(
            dateStartMillis = dateStartMillis,
            meals = meals.toList()
        )

        if (index >= 0) {
            snapshots[index] = snapshot
        } else {
            snapshots.add(snapshot)
        }

        snapshots.sortBy { it.dateStartMillis }

        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putString("eaten_meal_list_snapshots", gson.toJson(snapshots))
            .apply()
    }

    private fun getStartOfDayMillis(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun loadCustomSliders(context: Context): MutableList<CustomSlider> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("custom_sliders", "[]")
        val type = object : TypeToken<MutableList<CustomSlider>>() {}.type

        val sliders: MutableList<CustomSlider> = try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }

        var changed = false

        sliders.forEach { slider ->
            if (slider.ratingSettings == null) {
                val oldValue = slider.value.coerceIn(0, 100)
                slider.value = ((oldValue * 9 + 50) / 100) + 1

                val settings = EnergyRatingSettings()

                if (slider.minLabel.isNotBlank()) {
                    settings.labels["1"] = slider.minLabel
                }
                if (slider.maxLabel.isNotBlank()) {
                    settings.labels["10"] = slider.maxLabel
                }

                slider.ratingSettings = settings
                changed = true
            }
        }

        if (changed) {
            saveCustomSliders(context, sliders)
        }

        return sliders
    }

    fun saveCustomSliders(context: Context, list: List<CustomSlider>) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString("custom_sliders", gson.toJson(list)).apply()
    }

    fun loadSensations(context: Context): MutableList<PhysicalSensation> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString("sensations", "[]")
        val type = object : TypeToken<MutableList<PhysicalSensation>>() {}.type
        return try { gson.fromJson(json, type) ?: mutableListOf() } catch (_: Exception) { mutableListOf() }
    }

    fun saveSensations(context: Context, list: List<PhysicalSensation>) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString("sensations", gson.toJson(list)).apply()
    }

    fun loadMedications(context: Context): MutableList<MedicationItem> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString("medications", "[]")
        val type = object : TypeToken<MutableList<MedicationItem>>() {}.type
        return try { gson.fromJson(json, type) ?: mutableListOf() } catch (_: Exception) { mutableListOf() }
    }

    fun saveMedications(context: Context, list: List<MedicationItem>) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString("medications", gson.toJson(list)).apply()
    }

    fun loadNutritionEntries(context: Context): MutableList<NutritionScheduleEntry> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString("nutrition_entries", "[]")
        val type = object : TypeToken<MutableList<NutritionScheduleEntry>>() {}.type
        return try { gson.fromJson(json, type) ?: mutableListOf() } catch (_: Exception) { mutableListOf() }
    }

    fun saveNutritionEntries(context: Context, list: List<NutritionScheduleEntry>) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString("nutrition_entries", gson.toJson(list)).apply()
    }

    fun loadLogs(context: Context): MutableList<HealthLogEntry> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString("logs", "[]")
        val type = object : TypeToken<MutableList<HealthLogEntry>>() {}.type
        return try { gson.fromJson(json, type) ?: mutableListOf() } catch (_: Exception) { mutableListOf() }
    }

    fun saveLogs(context: Context, list: List<HealthLogEntry>) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString("logs", gson.toJson(list)).apply()
    }

    fun deleteLogEntry(context: Context, logId: String) {
        val logs = loadLogs(context)
        logs.removeAll { it.id == logId }
        saveLogs(context, logs)
    }

    fun saveOrUpdateLogEntry(context: Context, entry: HealthLogEntry) {
        val logs = loadLogs(context)
        val idx = logs.indexOfFirst { it.id == entry.id }
        if (idx >= 0) {
            logs[idx] = entry
        } else {
            logs.add(0, entry)
        }
        saveLogs(context, logs)
    }

    fun getLastLogForType(context: Context, type: String): HealthLogEntry? {
        val logs = loadLogs(context)
        return logs.find { it.type == type }
    }

    fun getHydrationCount(context: Context): Int {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = sp.getLong("hydration_last_date", 0L)
        val today = System.currentTimeMillis()
        if (lastDate == 0L) {
            sp.edit().putLong("hydration_last_date", today).apply()
        } else if (!isSameDay(lastDate, today)) {
            sp.edit().putInt("hydration_count", 0).putLong("hydration_last_date", today).apply()
            return 0
        }
        return sp.getInt("hydration_count", 0)
    }

    fun setHydrationCount(context: Context, count: Int) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putInt("hydration_count", count).putLong("hydration_last_date", System.currentTimeMillis()).apply()
    }

    fun getHydrationTarget(context: Context): Int {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getInt("hydration_target", 8)
    }

    fun setHydrationTarget(context: Context, target: Int) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putInt("hydration_target", target).apply()
    }

    fun getLastEatenTime(context: Context): Pair<Long, String>? {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ts = sp.getLong("last_eaten_ts", 0L)
        val type = sp.getString("last_eaten_type", "") ?: ""
        if (ts == 0L) return null
        return Pair(ts, type)
    }

    fun setLastEatenTime(context: Context, ts: Long, mealType: String) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putLong("last_eaten_ts", ts).putString("last_eaten_type", mealType).apply()
    }

    fun getRestStartTime(context: Context): Long {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getLong("rest_start_ts", 0L)
    }

    fun setRestStartTime(context: Context, ts: Long) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putLong("rest_start_ts", ts).apply()
    }

    fun loadEatenRecords(context: Context): MutableList<EatenMealRecord> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString("eaten_records", "[]") ?: "[]"
        val type = object : TypeToken<MutableList<EatenMealRecord>>() {}.type
        return try { gson.fromJson(json, type) ?: mutableListOf() } catch (_: Exception) { mutableListOf() }
    }

    fun saveEatenRecords(context: Context, list: List<EatenMealRecord>) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString("eaten_records", gson.toJson(list)).apply()
    }

    fun saveOrUpdateEatenRecord(context: Context, record: EatenMealRecord) {
        val list = loadEatenRecords(context)
        val index = list.indexOfFirst { it.id == record.id }
        if (index >= 0) {
            list[index] = record
        } else {
            list.add(0, record)
        }
        saveEatenRecords(context, list)
    }

    fun deleteEatenRecord(context: Context, recordId: String) {
        val list = loadEatenRecords(context)
        list.removeAll { it.id == recordId }
        saveEatenRecords(context, list)
    }

    fun addLogEntry(context: Context, entry: HealthLogEntry) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val logs = loadLogs(context)
        logs.add(0, entry)
        if (logs.size > 100) {
            logs.subList(100, logs.size).clear()
        }
        sp.edit().putString("logs", gson.toJson(logs)).apply()
    }

    fun isSameDay(ts1: Long, ts2: Long, tz: TimeZone = TimeZone.getDefault()): Boolean {
        val day1 = (ts1 + tz.getOffset(ts1)) / 86400000L
        val day2 = (ts2 + tz.getOffset(ts2)) / 86400000L
        return day1 == day2
    }

    fun getEatenRecordFromList(records: List<EatenMealRecord>, dayMillis: Long, mealType: String): EatenMealRecord? {
        val tz = TimeZone.getDefault()
        return records.filter { isSameDay(it.timestamp, dayMillis, tz) && it.mealType.equals(mealType, ignoreCase = true) }
            .maxByOrNull { it.timestamp }
    }

    fun getEatenRecordForDayAndMeal(context: Context, dayMillis: Long, mealType: String): EatenMealRecord? {
        val records = loadEatenRecords(context)
        return getEatenRecordFromList(records, dayMillis, mealType)
    }

    fun loadSavedFoodItems(context: Context): MutableList<String> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString("saved_food_items", "[]") ?: "[]"
        val type = object : TypeToken<MutableList<String>>() {}.type
        return try { gson.fromJson(json, type) ?: mutableListOf() } catch (_: Exception) { mutableListOf() }
    }

    fun saveSavedFoodItems(context: Context, list: List<String>) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString("saved_food_items", gson.toJson(list)).apply()
    }
}