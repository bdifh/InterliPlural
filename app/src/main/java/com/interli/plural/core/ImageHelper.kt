package com.interli.plural.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.interli.plural.Person
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageHelper {
    suspend fun downloadAndSaveProfilePicture(context: Context, url: String, personId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val loader = Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap()
                    return@withContext saveBitmapToInternalStorage(context, bitmap, personId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
    }
    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap, personId: String): String? {
        return try {
            val fileName = "profile_${personId}_${System.currentTimeMillis()}.png"
            val file = File(context.filesDir, fileName)
            context.filesDir.listFiles { f -> f.name.startsWith("profile_${personId}_") }?.forEach { it.delete() }
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
