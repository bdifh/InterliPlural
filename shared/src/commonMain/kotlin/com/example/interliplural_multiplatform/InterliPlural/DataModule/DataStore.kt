package com.example.interliplural_multiplatform.InterliPlural.DataModule

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences

private val MEMBERS_KEY = stringPreferencesKey("members")
private val FRONTING_MEMBERS_KEY = stringPreferencesKey("fronting_members")
private val FRONT_HISTORY_KEY = stringPreferencesKey("front_history")

object AppDataStore {
private lateinit var dataStore: DataStore<Preferences>
}