package com.interli.plural

import android.content.Context
import android.graphics.Color
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

object OpenPluralImportHelper {

    data class ImportResult(val membersCount: Int, val sessionsCount: Int)

    fun importFromUri(context: Context, uri: Uri): ImportResult? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val reader = InputStreamReader(inputStream)
            val gson = Gson()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val root: Map<String, Any> = gson.fromJson(reader, type) ?: return null

            val members = root["members"] as? List<Map<String, Any>> ?: emptyList()
            val frontPeriods = root["front_periods"] as? List<Map<String, Any>> ?: emptyList()

            val people = MemberHelper.loadAllPeople(context)
            val amIdToInternalId = mutableMapOf<String, String>()
            var membersCount = 0
            var sessionsCount = 0

            // 1. Import Members
            members.forEach { m ->
                val opId = m["id"] as? String ?: ""
                val name = m["name"] as? String ?: context.getString(R.string.label_unknown)
                
                if (opId.isNotEmpty() && people.none { it.manualId == opId }) {
                    val desc = m["description"] as? String ?: ""
                    val colorHex = m["color"] as? String ?: m["colour"] as? String
                    val avatar = m["avatar"] as? String
                    val pronouns = m["pronouns"] as? String

                    val p = Person(
                        name = name,
                        manualId = opId,
                        profileInfo = buildString {
                            if (!pronouns.isNullOrBlank()) append("Pronouns: $pronouns\n")
                            append(desc)
                        },
                        profilePictureUri = avatar,
                        profileColor = parseHexColor(colorHex)
                    )
                    people.add(p)
                    amIdToInternalId[opId] = p.id
                    membersCount++
                } else if (opId.isNotEmpty()) {
                    people.find { it.manualId == opId }?.let { amIdToInternalId[opId] = it.id }
                }
            }
            if (membersCount > 0) {
                MemberHelper.savePeople(context, people)
            }

            // 2. Import Front Periods
            if (frontPeriods.isNotEmpty()) {
                val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
                val sessionsJson = sharedPref.getString("sessions_list", "[]") ?: "[]"
                val sessions: MutableList<FrontSession> = gson.fromJson(sessionsJson, object : TypeToken<MutableList<FrontSession>>() {}.type) ?: mutableListOf()

                frontPeriods.forEach { fp ->
                    val startedAt = fp["started_at"] as? String
                    val endedAt = fp["ended_at"] as? String
                    val memberIds = fp["members"] as? List<String>
                    val note = fp["comment"] as? String

                    val startMs = parseIsoDate(startedAt)
                    val endMs = parseIsoDate(endedAt)

                    if (startMs > 0 && memberIds != null) {
                        memberIds.forEach { opId ->
                            val internalId = amIdToInternalId[opId]
                            val person = people.find { it.id == internalId || it.manualId == opId }
                            
                            if (person != null && sessions.none { it.startTime == startMs && it.personId == person.id }) {
                                sessions.add(FrontSession(
                                    personName = person.name,
                                    startTime = startMs,
                                    endTime = if (endMs > 0) endMs else null,
                                    personId = person.id,
                                    note = note
                                ))
                                sessionsCount++
                            }
                        }
                    }
                }
                if (sessionsCount > 0) {
                    sharedPref.edit().putString("sessions_list", gson.toJson(sessions)).apply()
                }
            }

            return ImportResult(membersCount, sessionsCount)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseHexColor(hex: String?): Int {
        if (hex.isNullOrBlank()) return -6934396
        return try {
            Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
        } catch (e: Exception) { -6934396 }
    }

    private fun parseIsoDate(isoStr: String?): Long {
        if (isoStr.isNullOrBlank()) return 0L
        return try {
            val format = if (isoStr.contains(".")) {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            } else {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            }
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(isoStr)?.time ?: 0L
        } catch (e: Exception) { 0L }
    }
}
