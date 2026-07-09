package com.interli.plural

import android.content.Context
import android.util.Base64
import android.util.Base64InputStream
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream

object AmpersandImportHelper {

    fun importFromUri(context: Context, uri: android.net.Uri): Boolean {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return false

            val pbIs = PushbackInputStream(inputStream, 1)
            val firstByte = pbIs.read()
            if (firstByte == -1) return false
            pbIs.unread(firstByte)

            val finalStream = if (firstByte == '{'.toInt() || firstByte == '['.toInt()) {
                pbIs
            } else {
                Base64InputStream(pbIs, Base64.DEFAULT)
            }

            val reader = InputStreamReader(finalStream)
            val gson = Gson()
            val type = object : TypeToken<Map<String, Any>>() {}.type

            val root: Map<String, Any> = gson.fromJson(reader, type) ?: return false

            val members = findListInMap(root, "members") ?: findListInMap(root, "alters")
            if (members == null) return false

            val people = MemberHelper.loadAllPeople(context)
            val settingsPref = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            val groupsJson = settingsPref.getString("groups_list", "[]")
            val currentGroups: MutableList<Group> = gson.fromJson(groupsJson, object : TypeToken<MutableList<Group>>() {}.type) ?: mutableListOf()

            val amIdToInternalId = mutableMapOf<String, String>()

            members.filterIsInstance<Map<String, Any>>().forEach { m ->
                val amId = m["id"]?.toString() ?: m["uuid"]?.toString() ?: ""
                val name = m["display_name"]?.toString() ?: m["name"]?.toString() ?: context.getString(R.string.label_unknown)

                if (people.none { it.manualId == amId }) {
                    val pronouns = m["pronouns"]?.toString()
                    val desc = m["description"]?.toString() ?: m["bio"]?.toString()
                    val colorHex = m["color"]?.toString()
                    val avatar = m["avatar_url"]?.toString()

                    val p = Person(
                        name = name,
                        manualId = amId,
                        profileInfo = buildString {
                            if (!pronouns.isNullOrBlank()) append(context.getString(R.string.label_pronouns_colon, pronouns) + "\n")
                            append(desc ?: "")
                        },
                        profilePictureUri = avatar,
                        profileColor = parseHexColor(colorHex)
                    )
                    people.add(p)
                    if (amId.isNotEmpty()) amIdToInternalId[amId] = p.id
                }
            }

            val groups = findListInMap(root, "groups")
            groups?.filterIsInstance<Map<String, Any>>()?.forEach { g ->
                val gName = g["name"]?.toString() ?: context.getString(R.string.label_imported_group)
                val gMembers = (g["members"] as? List<*>)?.filterIsInstance<String>()

                var targetGroup = currentGroups.find { it.name == gName }
                if (targetGroup == null) {
                    targetGroup = Group(name = gName)
                    currentGroups.add(targetGroup)
                }

                gMembers?.forEach { amMemberId ->
                    val internalId = amIdToInternalId[amMemberId]
                    people.find { it.id == internalId }?.let { person ->
                        if (person.groupIds == null) person.groupIds = mutableListOf()
                        if (!person.groupIds!!.contains(targetGroup.id)) {
                            person.groupIds!!.add(targetGroup.id)
                        }
                    }
                }
            }

            MemberHelper.savePeople(context, people)
            settingsPref.edit().putString("groups_list", Gson().toJson(currentGroups)).apply()

            val history = findListInMap(root, "front_history") ?: findListInMap(root, "switches") ?: findListInMap(root, "frontHistory")
            if (history != null) {
                val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
                val sessionsJson = sharedPref.getString("sessions_list", "[]") ?: "[]"
                val sessions: MutableList<FrontSession> = gson.fromJson(sessionsJson, object : TypeToken<MutableList<FrontSession>>() {}.type) ?: mutableListOf()

                history.filterIsInstance<Map<String, Any>>().forEach { h ->
                    val start = (h["startTime"] as? Number ?: h["start"] as? Number ?: h["timestamp"] as? Number)?.toLong() ?: 0L
                    val end = (h["endTime"] as? Number ?: h["end"] as? Number)?.toLong()
                    val amMemberId = h["memberId"] as? String ?: h["member"] as? String ?: (h["members"] as? List<*>)?.firstOrNull()?.toString()
                    val note = h["note"] as? String

                    if (start > 0 && amMemberId != null) {
                        val internalId = amIdToInternalId[amMemberId]
                        val person = people.find { it.id == internalId || it.manualId == amMemberId }
                        if (person != null && sessions.none { it.startTime == start && it.personId == person.id }) {
                            sessions.add(FrontSession(
                                personName = person.name,
                                startTime = start,
                                endTime = if (end != null && end > 0) end else null,
                                personId = person.id,
                                note = note
                            ))
                        }
                    }
                }
                sharedPref.edit().putString("sessions_list", gson.toJson(sessions)).apply()
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            inputStream?.close()
        }
    }

    private fun findListInMap(map: Map<String, Any>, key: String): List<*>? {
        if (map.containsKey(key) && map[key] is List<*>) return map[key] as List<*>

        for (value in map.values) {
            if (value is Map<*, *>) {
                val result = findListInMap(value as Map<String, Any>, key)
                if (result != null) return result
            }
        }
        return null
    }

    private fun parseHexColor(hex: String?): Int {
        if (hex.isNullOrBlank()) return -6934396
        return try {
            android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
        } catch (e: Exception) { -6934396 }
    }
}