package com.interli.plural.core

import android.app.Application
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
        val imageLoader = ImageLoader.Builder(this)
            .componentRegistry {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder(this@MainApplication))
                } else {
                    add(GifDecoder())
                }
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .cache(CoilUtils.createDefaultCache(this))
                    .build()
            }
            .availableMemoryPercentage(0.25)
            .crossfade(true)
            .allowHardware(false)
            .build()
        Coil.setImageLoader(imageLoader)
    }
}