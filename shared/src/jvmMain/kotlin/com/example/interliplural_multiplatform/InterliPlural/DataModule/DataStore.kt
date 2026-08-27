package com.example.interliplural_multiplatform.InterliPlural.DataModule

package com.example.interliplural_multiplatform.InterliPlural.DataModule

import androidx.datastore.core.DataStore
import androidx.datastore.core.FileStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import java.io.File

actual fun getDataStore(): DataStore<Preferences> {
    return createDataStore(
        storage = FileStorage(
            serializer = PreferencesSerializer,
            produceFile = {
                File(
                    System.getProperty("user.home"),
                    dataStoreFileName
                )
            }
        )
    )
}