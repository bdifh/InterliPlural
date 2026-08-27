@file:JvmName("JvmDataStore")

package com.example.interliplural_multiplatform.InterliPlural.DataModule

import androidx.datastore.preferences.core.PreferencesFileSerializer

import androidx.datastore.core.DataStore
import androidx.datastore.core.FileStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import java.io.File

actual fun getDataStores(): DataStore<Preferences> {
    return createDataStore(
        storage = FileStorage(
            serializer = PreferencesFileSerializer,
            produceFile = {
                File(
                    System.getProperty("user.home"),
                    dataStoreFileName
                )
            }
        )
    )
}