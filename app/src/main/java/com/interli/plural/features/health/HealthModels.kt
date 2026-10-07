package com.interli.plural.features.health

import java.util.UUID

data class HealthSettings(
    var showHydration: Boolean = true,
    var showEatenCheck: Boolean = true,
    var showEnergySlider: Boolean = true,
    var showRest: Boolean = true,
    var showSensations: Boolean = true,
    var showCustomSliders: Boolean = true,
    var showCustomCounters: Boolean = true,
    var showMedication: Boolean = false,
    var showWeight: Boolean = false,
    var showNutritionSchedule: Boolean = true
)

data class CustomCounter(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var count: Int = 0,
    var target: Int = 8,
    var unit: String = "x"
)

data class CustomSlider(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var minLabel: String = "",
    var maxLabel: String = "",
    var value: Int = 50
)

data class PhysicalSensation(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var intensity: Int = 0 // 0 - 10
)

data class MedicationItem(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var dosage: String = "",
    var isTakenToday: Boolean = false,
    var lastTakenTimestamp: Long? = null
)

data class NutritionScheduleEntry(
    val id: String = UUID.randomUUID().toString(),
    var dayOfWeek: String = "",
    var mealType: String = "",
    var description: String = ""
)

data class HealthLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val memberId: String? = null,
    val memberName: String? = null,
    val type: String = "",
    val value: String = "",
    val note: String = ""
)

data class EatenMealRecord(
    val id: String = UUID.randomUUID().toString(),
    var mealType: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var memberId: String? = null,
    var memberName: String? = null,
    var isChecked: Boolean = true,
    var eatenItems: List<String> = emptyList()
)