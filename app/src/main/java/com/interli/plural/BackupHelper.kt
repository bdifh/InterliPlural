package com.interli.plural

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import androidx.work.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
                .setConstraints(Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .build())
                .build()

            workManager.enqueueUniquePeriodicWork(
                "AUTO_BACKUP_TASK",
                ExistingPeriodicWorkPolicy.UPDATE,
                backupRequest
            )
        }
    }

    fun createBackupJson(context: Context): String {
        val stringWriter = StringWriter()
        val writer = JsonWriter(stringWriter)
        writer.setIndent("  ")

        val dataPrefs = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val settingsPrefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

        writer.beginObject()

        writer.name("data")
        writer.beginObject()
        dataPrefs.all.forEach { (k, v) ->
            writer.name(k)
            when (v) {
                is String -> writer.value(v)
                is Boolean -> writer.value(v)
                is Number -> writer.value(v)
                is Set<*> -> {
                    writer.beginArray()
                    v.forEach { item -> writer.value(item.toString()) }
                    writer.endArray()
                }
                else -> writer.value(v.toString())
            }
        }
        writer.endObject()

        writer.name("settings")
        writer.beginObject()
        settingsPrefs.all.forEach { (k, v) ->
            writer.name(k)
            when (v) {
                is String -> writer.value(v)
                is Boolean -> writer.value(v)
                is Number -> writer.value(v)
                is Set<*> -> {
                    writer.beginArray()
                    v.forEach { item -> writer.value(item.toString()) }
                    writer.endArray()
                }
                else -> writer.value(v.toString())
            }
        }
        writer.endObject()

        writer.name("images")
        writer.beginObject()
        val people = MemberHelper.loadAllPeople(context)
        people.forEach { person ->
            val avatarUri = person.sysmediaProfile?.profilePictureUri ?: person.profilePictureUri
            avatarUri?.let { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    val inputStream = if (uriStr.startsWith("content://")) {
                        context.contentResolver.openInputStream(uri)
                    } else {
                        val file = if (uriStr.startsWith("file://")) File(uri.path!!) else File(uriStr)
                        if (file.exists()) FileInputStream(file) else null
                    }

                    inputStream?.use { input ->
                        val bytes = input.readBytes()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        writer.name(person.id)
                        writer.value(base64)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        writer.endObject()

        writer.endObject()
        writer.close()
        return stringWriter.toString()
    }

    fun createBackupZip(context: Context, outStream: OutputStream) {
        val zipOut = ZipOutputStream(outStream)

        val json = createBackupJson(context)
        zipOut.putNextEntry(ZipEntry("backup.json"))
        zipOut.write(json.toByteArray())
        zipOut.closeEntry()

        val filesDir = context.filesDir
        filesDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                try {
                    zipOut.putNextEntry(ZipEntry("files/${file.name}"))
                    FileInputStream(file).use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                } catch (e: Exception) { e.printStackTrace() }
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

        var dataMap: Map<String, Any>? = null
        var settingsMap: Map<String, Any>? = null
        var imagesMap: Map<String, String>? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "data" -> dataMap = gson.fromJson(reader, object : TypeToken<Map<String, Any>>() {}.type)
                "settings" -> settingsMap = gson.fromJson(reader, object : TypeToken<Map<String, Any>>() {}.type)
                "images" -> imagesMap = gson.fromJson(reader, object : TypeToken<Map<String, String>>() {}.type)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        reader.close()

        val dataPrefs = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val settingsPrefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

        dataMap?.let { map ->
            val editor = dataPrefs.edit()
            editor.clear()
            map.forEach { (k, v) ->
                when (v) {
                    is String -> editor.putString(k, v)
                    is Boolean -> editor.putBoolean(k, v)
                    is Double -> {
                        if (v == v.toLong().toDouble()) {
                            val l = v.toLong()
                            if (l in Int.MIN_VALUE..Int.MAX_VALUE) editor.putInt(k, l.toInt())
                            else editor.putLong(k, l)
                        } else editor.putFloat(k, v.toFloat())
                    }
                    is List<*> -> {
                        if (v.isNotEmpty() && v.all { it is String }) {
                            editor.putStringSet(k, v.filterIsInstance<String>().toSet())
                        } else {
                            editor.putString(k, gson.toJson(v))
                        }
                    }
                }
            }
            editor.commit()
        }

        settingsMap?.let { map ->
            val editor = settingsPrefs.edit()
            editor.clear()
            map.forEach { (k, v) ->
                when (v) {
                    is String -> editor.putString(k, v)
                    is Boolean -> editor.putBoolean(k, v)
                    is Double -> {
                        if (v == v.toLong().toDouble()) {
                            val l = v.toLong()
                            if (l in Int.MIN_VALUE..Int.MAX_VALUE) editor.putInt(k, l.toInt())
                            else editor.putLong(k, l)
                        } else editor.putFloat(k, v.toFloat())
                    }
                    is List<*> -> {
                        if (v.isNotEmpty() && v.all { it is String }) {
                            editor.putStringSet(k, v.filterIsInstance<String>().toSet())
                        } else {
                            editor.putString(k, gson.toJson(v))
                        }
                    }
                }
            }
            editor.commit()
        }

        if (imagesMap != null && imagesMap.isNotEmpty()) {
            val people = MemberHelper.loadAllPeople(context)
            var peopleChanged = false

            imagesMap.forEach { (personId, base64) ->
                try {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val file = File(context.filesDir, "profile_${personId}_${System.currentTimeMillis()}.jpg")
                    context.filesDir.listFiles { f -> f.name.startsWith("profile_${personId}_") }?.forEach { it.delete() }
                    FileOutputStream(file).use { it.write(bytes) }

                    people.find { it.id == personId }?.let { person ->
                        val newUri = Uri.fromFile(file).toString()
                        if (person.isSysmediaOnly || person.sysmediaProfile?.handle != null) {
                            if (person.sysmediaProfile == null) person.sysmediaProfile = SysmediaProfile()
                            person.sysmediaProfile?.profilePictureUri = newUri
                        } else {
                            person.profilePictureUri = newUri
                        }
                        peopleChanged = true
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            if (peopleChanged) {
                MemberHelper.savePeople(context, people)
            }
        }
    }
}