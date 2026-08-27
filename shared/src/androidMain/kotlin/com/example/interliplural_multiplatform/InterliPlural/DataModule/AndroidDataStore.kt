package com.example.interliplural_multiplatform.InterliPlural.DataModule

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.FileStorage
import androidx.datastore.preferences.core.PreferencesFileSerializer
import java.io.File

fun createAndroidDataStore(context: Context): DataStore<Preferences> {
    return createDataStore(
        storage = FileStorage(
            serializer = PreferencesFileSerializer,
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
    return createDataStore(
        FileStorage(
            serializer = PreferencesFileSerializer,
            produceFile = {
                File(
                    appContext.filesDir,
                    dataStoreFileName
                )
            }
        )
    )
}