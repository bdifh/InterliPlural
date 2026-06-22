package com.interli.plural

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

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
