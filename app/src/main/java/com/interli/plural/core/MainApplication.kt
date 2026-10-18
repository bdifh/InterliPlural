package com.interli.plural.core

import android.app.Application
import android.content.Context
import android.os.Build.VERSION.SDK_INT
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.util.CoilUtils
import okhttp3.OkHttpClient

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setupCoil(this)
    }

    companion object {
        fun setupCoil(context: Context) {
            val sharedPref = context.getSharedPreferences("settings_prefs", MODE_PRIVATE)
            val enableGifs = sharedPref.getBoolean("enable_gifs", true)

            val imageLoader = ImageLoader.Builder(context)
                .componentRegistry {
                    if (enableGifs) {
                        if (SDK_INT >= 28) {
                            add(ImageDecoderDecoder(context))
                        }
                        add(GifDecoder())
                    }
                }
                .okHttpClient {
                    OkHttpClient.Builder()
                        .cache(CoilUtils.createDefaultCache(context))
                        .build()
                }
                .availableMemoryPercentage(0.25)
                .crossfade(true)
                .allowHardware(false)
                .build()
            Coil.setImageLoader(imageLoader)
        }
    }
}
