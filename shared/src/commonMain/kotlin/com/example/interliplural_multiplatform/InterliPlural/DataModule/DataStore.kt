package com.example.interliplural_multiplatform.InterliPlural.DataModule

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.Storage
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

internal const val dataStoreFileName = "interli.preferences_pb"

private val membersKey = stringPreferencesKey("members")
private val frontingMembersKey = stringPreferencesKey("frontingMembers")

fun createDataStore(storage: Storage<Preferences>): DataStore<Preferences> {
    return DataStoreFactory.create(
        storage = storage
    )
}

fun loadMembers(dataStore: DataStore<Preferences>): Flow<List<Member>> {
    return dataStore.data.map { preferences ->
        val json = preferences[membersKey]
        if (json == null) {
            emptyList()
        } else {
            Json.decodeFromString<List<Member>>(json)
        }
    }
}

suspend fun saveMembers(
    dataStore: DataStore<Preferences>,
    members: List<Member>
) {
    dataStore.edit { preferences ->
        preferences[membersKey] = Json.encodeToString(members)
    }
}

fun loadFrontingMembers(dataStore: DataStore<Preferences>): Flow<List<String>> {
    return dataStore.data.map { preferences ->
        val json = preferences[frontingMembersKey]
        if (json == null) {
            emptyList()
        } else {
            Json.decodeFromString<List<String>>(json)
        }
    }
}

suspend fun saveFrontingMembers(
    dataStore: DataStore<Preferences>,
    frontingMembers: List<String>
) {
    dataStore.edit { preferences ->
        preferences[frontingMembersKey] = Json.encodeToString(frontingMembers)
    }
}

expect fun getDataStores(): DataStore<Preferences>