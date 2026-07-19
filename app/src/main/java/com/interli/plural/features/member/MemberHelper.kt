package com.interli.plural.features.member

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.interli.plural.CustomField
import com.interli.plural.Group
import com.interli.plural.Person
import com.interli.plural.SysmediaProfile

object MemberHelper {
    fun loadAllPeople(context: Context): MutableList<Person> {
        val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val gson = Gson()
        val peopleJson = sharedPref.getString("people_list", "[]")
        val normalPeople: List<Person> = gson.fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type) ?: emptyList()
        val sysmediaPeopleJson = sharedPref.getString("sysmedia_people_list", "[]")
        val sysmediaOnlyPeople: List<Person> = gson.fromJson(sysmediaPeopleJson, object : TypeToken<List<Person>>() {}.type) ?: emptyList()
        val people = (normalPeople + sysmediaOnlyPeople).distinctBy { it.id }.toMutableList()
        people.forEach {
            if (it.sysmediaProfile == null) it.sysmediaProfile = SysmediaProfile()
            if (it.groupIds == null) it.groupIds = mutableListOf()
            if (it.customFields == null) it.customFields = mutableMapOf()
            if (it.hiddenFields == null) it.hiddenFields = mutableListOf()
            if (it.preferences == null) it.preferences = mutableListOf()
        }
        return people
    }
    fun migrateFields(context: Context, people: List<Person>) {
        val settingsPref = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val fieldsJson = settingsPref.getString("custom_fields", "[]") ?: "[]"
        val customFields: List<CustomField> = Gson().fromJson(fieldsJson, object : TypeToken<List<CustomField>>() {}.type) ?: emptyList()
        people.forEach { person ->
            val fields = person.customFields ?: return@forEach
            val newFields = fields.toMutableMap()
            val hidden = person.hiddenFields ?: mutableListOf()
            val newHidden = hidden.toMutableList()
            var changed = false
            customFields.forEach { fieldDef ->
                val id = fieldDef.id
                if (id != null) {
                    if (!hidden.contains(id) && hidden.contains(fieldDef.name)) {
                        newHidden.add(id)
                        newHidden.remove(fieldDef.name)
                        changed = true
                    }
                    if (newFields.containsKey(id) && newFields[id] == fieldDef.template) {
                        newFields.remove(id)
                        changed = true
                    }
                }
            }
            if (changed) {
                person.customFields = newFields
                person.hiddenFields = newHidden
            }
        }
    }
    fun savePeople(context: Context, people: List<Person>) {
        val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val gson = GsonBuilder().disableHtmlEscaping().create()
        val existingSysmediaJson = sharedPref.getString("sysmedia_people_list", "[]")
        val existingSysmedia: List<Person> = Gson().fromJson(existingSysmediaJson, object : TypeToken<List<Person>>() {}.type) ?: emptyList()
        val newNormal = people.filter { !it.isSysmediaOnly }
        val newSysmedia = people.filter { it.isSysmediaOnly }
        val editor = sharedPref.edit()
        editor.putString("people_list", gson.toJson(newNormal))
        if (newSysmedia.isNotEmpty()) {
            editor.putString("sysmedia_people_list", gson.toJson(newSysmedia))
        } else {
            if (people.isEmpty()) {
                editor.putString("sysmedia_people_list", "[]")
            } else {
                editor.putString("sysmedia_people_list", gson.toJson(existingSysmedia))
            }
        }
        editor.commit()
    }
    fun getSortedPeople(people: List<Person>, groups: List<Group>, includeArchived: Boolean = false): List<Person> {
        val sortedList = mutableListOf<Person>()
        val filteredPeople = if (includeArchived) {
            people.filter { !it.isSysmediaOnly }
        } else {
            people.filter { !it.isArchived && !it.isSysmediaOnly }
        }
        val unassigned = filteredPeople.filter { it.safeGroupIds.isEmpty() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        sortedList.addAll(unassigned)
        val rootGroups = groups.filter { it.parentGroupId == null }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        rootGroups.forEach { group ->
            addPeopleFromGroup(group, filteredPeople, groups, sortedList)
        }
        return sortedList
    }
    fun restoreDeletedMember(context: Context, name: String, id: String?) {
        val people = loadAllPeople(context)
        if (people.any { it.id == id || it.name == name }) return
        val newPerson = Person(
            id = id ?: java.util.UUID.randomUUID().toString(),
            name = name,
            isArchived = false
        )
        people.add(newPerson)
        savePeople(context, people)
    }
    private fun addPeopleFromGroup(group: Group, people: List<Person>, groups: List<Group>, result: MutableList<Person>) {
        val groupMembers = people.filter { it.safeGroupIds.contains(group.id) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        result.addAll(groupMembers)
        val subGroups = groups.filter { it.parentGroupId == group.id }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        subGroups.forEach { subGroup ->
            addPeopleFromGroup(subGroup, people, groups, result)
        }
    }
}
