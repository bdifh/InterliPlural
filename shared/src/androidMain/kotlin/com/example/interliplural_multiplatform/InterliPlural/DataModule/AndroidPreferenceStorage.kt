package com.example.interliplural_multiplatform.InterliPlural.DataModule

import android.content.Context
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import okio.FileSystem
import okio.Path.Companion.toPath

class PreferenceStorage(
    private val context: Context
) : OkioStorage<Preferences>(
    fileSystem = FileSystem.SYSTEM,
    serializer = PreferencesSerializer,
    producePath = {
        context.filesDir
            .resolve("data_MemberInAppBehaviour.preferences_pb")
            .absolutePath
            .toPath()
    }
)