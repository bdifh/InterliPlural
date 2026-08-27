package com.example.interliplural_multiplatform.InterliPlural.DataModule

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.FileStorage
import java.io.File

fun createAndroidDataStore(context: Context): DataStore<Preferences> {
    return createDataStore(
        storage = FileStorage(
            serializer = PreferencesSerializer,
            produceFile = {
                File(
                    context.filesDir,
                    dataStoreFileName
                )
            }
        )
    )
}

private lateinit var appContext: Context

fun initializeDataStore(context: Context) {
    appContext = context.applicationContext
}

actual fun getDataStores(): DataStore<Preferences> {
    return createDataStore(appContext)
}