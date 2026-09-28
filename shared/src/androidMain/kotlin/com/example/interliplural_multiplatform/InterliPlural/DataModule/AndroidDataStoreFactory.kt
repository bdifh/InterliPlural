package com.example.interliplural_multiplatform.InterliPlural.DataModule

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import android.content.Context

private lateinit var appContext: Context

fun prepareDataStore(context: Context) {
    appContext = context.applicationContext
}

actual fun createDataStore(): DataStore<Preferences> {
    return PreferenceDataStoreFactory.create(
        storage = PreferenceStorage(appContext)
    )
}
