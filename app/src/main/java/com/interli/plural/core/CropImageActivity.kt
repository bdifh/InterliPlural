package com.interli.plural.core

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.CropImageView
import com.interli.plural.R
import java.io.File
import java.io.FileOutputStream

class CropImageActivity : BaseActivity() {
    private lateinit var cropImageView: CropImageView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_image)
        cropImageView = findViewById(R.id.cropImageView)
        val btnSave = findViewById<Button>(R.id.btnSaveCrop)
        val btnCancel = findViewById<Button>(R.id.btnCancelCrop)
        val uriString = intent.getStringExtra("image_uri")
        if (uriString != null) {
            cropImageView.setImageUri(Uri.parse(uriString))
        } else {
            finish()
        }
        btnCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        btnSave.setOnClickListener {
            val cropped = cropImageView.getCroppedBitmap()
            if (cropped != null) {
                val uri = saveCroppedImage(cropped)
                if (uri != null) {
                    val resultIntent = Intent()
                    resultIntent.putExtra("cropped_uri", uri.toString())
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
            }
        }
    }
    private fun saveCroppedImage(bitmap: Bitmap): Uri? {
        return try {
            val file = File(cacheDir, "cropped_${System.currentTimeMillis()}.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
