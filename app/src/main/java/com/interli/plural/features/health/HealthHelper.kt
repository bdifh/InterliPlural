package com.interli.plural.features.health

import android.content.Context
import android.icu.util.TimeZone
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

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

    fun loadCustomSliders(context: Context): MutableList<CustomSlider> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString("custom_sliders", "[]")
        val type = object : TypeToken<MutableList<CustomSlider>>() {}.type
        return try { gson.fromJson(json, type) ?: mutableListOf() } catch (_: Exception) { mutableListOf() }
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

    fun getLastLogForType(context: Context, type: String): HealthLogEntry? {
        val logs = loadLogs(context)
        return logs.find { it.type == type }
    }

    fun getHydrationCount(context: Context): Int {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getInt("hydration_count", 0)
    }

    fun setHydrationCount(context: Context, count: Int) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putInt("hydration_count", count).apply()
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