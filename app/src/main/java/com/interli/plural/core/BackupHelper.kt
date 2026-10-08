package com.interli.plural.core

import android.content.Context
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object BackupHelper {
    fun updateAutoBackupSchedule(context: Context) {
        val sp = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val enabled = sp.getBoolean("auto_backup_enabled", false)
        val frequency = sp.getString("auto_backup_frequency", "daily") ?: "daily"
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag("AUTO_BACKUP")
        if (enabled) {
            val repeatInterval = when (frequency) {
                "weekly" -> 7L
                "monthly" -> 30L
                else -> 1L
            }
            val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(repeatInterval, TimeUnit.DAYS)
                .addTag("AUTO_BACKUP")
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .build()
            workManager.enqueueUniquePeriodicWork(
                "AUTO_BACKUP_TASK",
                ExistingPeriodicWorkPolicy.UPDATE,
                backupRequest
            )
        }
    }

    fun createBackupJson(context: Context, selections: BooleanArray? = null): String {
        val stringWriter = StringWriter()
        val writer = JsonWriter(stringWriter)
        writer.setIndent("  ")
        val dataPrefs = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val settingsPrefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val healthPrefs = context.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)

        writer.beginObject()
        writer.name("data")
        writer.beginObject()

        val exportAll = selections == null
        val exportFront = exportAll || selections!![0]
        val exportMood = exportAll || selections!![1]
        val exportNotes = exportAll || selections!![2]
        val exportTodo = exportAll || selections!![3]
        val exportRelations = exportAll || selections!![4]
        val exportSettings = exportAll || selections!![5]
        val exportImages = exportAll || selections!![6]
        val exportCalendar = exportAll || (selections != null && selections.size > 7 && selections[7])
        val exportHealth = exportAll || (selections != null && selections.size > 8 && selections[8])

        val frontKeys = listOf(
            "people_list", "sysmedia_people_list", "groups_list", "sessions_list",
            "last_fronter_name", "current_fronters", "subsystem_data", "subsystem_sessions",
            "identity_groups", "collapsed_mood_groups"
        )
        val moodKeys = listOf(
            "mood_entries", "mood_color_1", "mood_color_2", "mood_color_3",
            "mood_color_4", "mood_color_5", "activity_groups", "identity_groups", "collapsed_mood_groups"
        )
        val notesKeys = listOf("diary_notes", "diary_bundles", "sysmedia_posts", "sysmedia_notifications", "sysmedia_dms", "sysmedia_chat_groups")
        val todoKeys = listOf("todo_lists", "todo_bundles")
        val relationsKeys = listOf("relations_environments", "relations_data")
        val calendarKeys = listOf("calendar_events")

        dataPrefs.all.forEach { (k, v) ->
            val shouldExport = when {
                frontKeys.contains(k) && moodKeys.contains(k) -> exportFront || exportMood
                frontKeys.contains(k) -> exportFront
                moodKeys.contains(k) -> exportMood
                notesKeys.contains(k) -> exportNotes
                todoKeys.contains(k) -> exportTodo
                relationsKeys.contains(k) -> exportRelations
                calendarKeys.contains(k) -> exportCalendar
                else -> exportAll
            }

            if (shouldExport) {
                writer.name(k)
                writeJsonValue(writer, v)
            }
        }
        writer.endObject()

        if (exportSettings) {
            writer.name("settings")
            writer.beginObject()
            settingsPrefs.all.forEach { (k, v) ->
                writer.name(k)
                writeJsonValue(writer, v)
            }
            writer.endObject()
        }

        if (exportHealth) {
            writer.name("health")
            writer.beginObject()
            healthPrefs.all.forEach { (k, v) ->
                writer.name(k)
                writeJsonValue(writer, v)
            }
            writer.endObject()
        }

        writer.name("images")
        writer.beginObject()
        writer.endObject()

        writer.endObject()
        writer.close()
        return stringWriter.toString()
    }

    private fun writeJsonValue(writer: JsonWriter, v: Any?) {
        when (v) {
            null -> writer.nullValue()
            is String -> writer.value(v)
            is Boolean -> writer.value(v)
            is Number -> writer.value(v)
            is Set<*> -> {
                writer.beginArray()
                v.forEach { item -> writer.value(item.toString()) }
                writer.endArray()
            }
            is List<*> -> {
                writer.beginArray()
                v.forEach { item -> writeJsonValue(writer, item) }
                writer.endArray()
            }
            is Map<*, *> -> {
                writer.beginObject()
                v.forEach { (key, value) ->
                    writer.name(key.toString())
                    writeJsonValue(writer, value)
                }
                writer.endObject()
            }
            else -> writer.value(v.toString())
        }
    }

    fun createBackupZip(context: Context, outStream: java.io.OutputStream, selections: BooleanArray? = null) {
        val zipOut = java.util.zip.ZipOutputStream(outStream)
        val json = createBackupJson(context, selections)
        zipOut.putNextEntry(java.util.zip.ZipEntry("backup.json"))
        zipOut.write(json.toByteArray())
        zipOut.closeEntry()

        val exportImages = selections == null || selections[6]
        if (exportImages) {
            val filesDir = context.filesDir
            filesDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    try {
                        zipOut.putNextEntry(java.util.zip.ZipEntry("files/${file.name}"))
                        java.io.FileInputStream(file).use { it.copyTo(zipOut) }
                        zipOut.closeEntry()
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
        zipOut.close()
    }

    fun saveAutoBackup(context: Context): Boolean {
        try {
            val folder = File(context.getExternalFilesDir(null), "backups")
            if (!folder.exists()) folder.mkdirs()
            val todayPrefix = "auto_backup_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}"
            val existingToday = folder.listFiles { f -> f.name.startsWith(todayPrefix) }
            if (existingToday != null && existingToday.isNotEmpty()) {
                return true
            }
            val fileName = "${todayPrefix}_${SimpleDateFormat("HHmm", Locale.getDefault()).format(Date())}.zip"
            val file = File(folder, fileName)
            FileOutputStream(file).use { createBackupZip(context, it) }
            val files = folder.listFiles { f -> f.name.startsWith("auto_backup_") }?.sortedBy { it.lastModified() }
            if (files != null && files.size > 10) {
                files.take(files.size - 10).forEach { it.delete() }
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun getAutoBackups(context: Context): List<File> {
        val folder = File(context.getExternalFilesDir(null), "backups")
        return folder.listFiles { f -> f.name.endsWith(".json") || f.name.endsWith(".zip") }?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun restoreBackup(context: Context, inputStream: InputStream) {
        val bis = BufferedInputStream(inputStream)
        bis.mark(1024)
        val header = ByteArray(4)
        val read = bis.read(header)
        bis.reset()
        val isZip = read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
        if (isZip) {
            val zipIn = ZipInputStream(bis)
            var entry: ZipEntry? = zipIn.getNextEntry()
            while (entry != null) {
                if (entry.name == "backup.json") {
                    val tempFile = File(context.cacheDir, "temp_backup.json")
                    FileOutputStream(tempFile).use { zipIn.copyTo(it) }
                    restoreFromJson(context, tempFile.inputStream())
                    tempFile.delete()
                } else if (entry.name.startsWith("files/")) {
                    val fileName = entry.name.substring(6)
                    val outFile = File(context.filesDir, fileName)
                    FileOutputStream(outFile).use { zipIn.copyTo(it) }
                }
                zipIn.closeEntry()
                entry = zipIn.getNextEntry()
            }
            zipIn.close()
        } else {
            restoreFromJson(context, bis)
        }
    }

    private fun restoreFromJson(context: Context, inputStream: InputStream) {
        val reader = JsonReader(InputStreamReader(inputStream))
        val gson = Gson()
        val dataPrefs = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val settingsPrefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val healthPrefs = context.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "data" -> {
                    reader.beginObject()
                    val editor = dataPrefs.edit()
                    editor.clear()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        try {
                            restorePreferenceValue(editor, key, reader, gson)
                        } catch (e: Exception) {
                            reader.skipValue()
                        }
                    }
                    editor.commit()
                    reader.endObject()
                }
                "settings" -> {
                    reader.beginObject()
                    val editor = settingsPrefs.edit()
                    editor.clear()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        try {
                            restorePreferenceValue(editor, key, reader, gson)
                        } catch (e: Exception) {
                            reader.skipValue()
                        }
                    }
                    editor.commit()
                    reader.endObject()
                }
                "health" -> {
                    reader.beginObject()
                    val editor = healthPrefs.edit()
                    editor.clear()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        try {
                            restorePreferenceValue(editor, key, reader, gson)
                        } catch (e: Exception) {
                            reader.skipValue()
                        }
                    }
                    editor.commit()
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        reader.close()

        com.interli.plural.widgets.CurrentFronterWidget.sendRefreshBroadcast(context)
        com.interli.plural.widgets.MoodAverageWidget.sendRefreshBroadcast(context)
        com.interli.plural.widgets.MoodLogWidget.sendRefreshBroadcast(context)
        com.interli.plural.widgets.TodoWidgetProvider.sendRefreshBroadcast(context)
        com.interli.plural.widgets.CalendarDayWidgetProvider.sendRefreshBroadcast(context)
        com.interli.plural.widgets.CalendarWeekWidgetProvider.sendRefreshBroadcast(context)
        com.interli.plural.widgets.CalendarMonthWidgetProvider.sendRefreshBroadcast(context)
    }

    private fun restorePreferenceValue(
        editor: android.content.SharedPreferences.Editor,
        key: String,
        reader: JsonReader,
        gson: Gson
    ) {
        when (reader.peek()) {
            com.google.gson.stream.JsonToken.STRING -> {
                editor.putString(key, reader.nextString())
            }
            com.google.gson.stream.JsonToken.BOOLEAN -> {
                editor.putBoolean(key, reader.nextBoolean())
            }
            com.google.gson.stream.JsonToken.NUMBER -> {
                val numStr = reader.nextString()
                if (numStr.contains(".")) {
                    val f = numStr.toFloatOrNull() ?: 0f
                    editor.putFloat(key, f)
                } else {
                    val l = numStr.toLongOrNull() ?: 0L
                    if (key == "font_size_multiplier") {
                        editor.putFloat(key, l.toFloat())
                    } else if (key.startsWith("last_viewed_") || key.endsWith("_timestamp") || key.endsWith("_ts")) {
                        editor.putLong(key, l)
                    } else if (l in Int.MIN_VALUE..Int.MAX_VALUE) {
                        editor.putInt(key, l.toInt())
                    } else {
                        editor.putLong(key, l)
                    }
                }
            }
            com.google.gson.stream.JsonToken.BEGIN_ARRAY -> {
                val parsed = gson.fromJson<Any>(reader, object : TypeToken<List<Any?>>() {}.type)
                if (key == "collapsed_mood_groups" && parsed is List<*>) {
                    editor.putStringSet(key, parsed.filterIsInstance<String>().toSet())
                } else {
                    editor.putString(key, gson.toJson(parsed))
                }
            }
            com.google.gson.stream.JsonToken.BEGIN_OBJECT -> {
                val parsed = gson.fromJson<Any>(reader, object : TypeToken<Map<String, Any?>>() {}.type)
                editor.putString(key, gson.toJson(parsed))
            }
            com.google.gson.stream.JsonToken.NULL -> {
                reader.nextNull()
                editor.remove(key)
            }
            else -> reader.skipValue()
        }
    }
}