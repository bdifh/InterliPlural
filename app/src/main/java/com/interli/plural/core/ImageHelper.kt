package com.interli.plural.core

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object ImageHelper {
    /**
     * Downloadt een afbeelding (of GIF) van een URL en slaat deze op in de interne opslag.
     * Behoudt de originele bestandsindeling (belangrijk voor bewegende GIFs).
     */
    suspend fun downloadAndSaveProfilePicture(context: Context, url: String, personId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val extension = if (url.lowercase().endsWith(".gif")) "gif" else "png"
                val fileName = "profile_${personId}_${System.currentTimeMillis()}.$extension"
                val file = File(context.filesDir, fileName)

                context.filesDir.listFiles { f -> f.name.startsWith("profile_${personId}_") }?.forEach { it.delete() }

                val connection = URL(url).openConnection()
                connection.connect()

                connection.getInputStream().use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                Uri.fromFile(file).toString()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}