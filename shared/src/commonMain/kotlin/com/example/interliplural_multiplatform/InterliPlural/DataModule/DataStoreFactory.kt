package com.example.interliplural_multiplatform.InterliPlural.DataModule

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

expect fun createDatastore() : DataStore<Preferences>

class DataStoreFactory {
}