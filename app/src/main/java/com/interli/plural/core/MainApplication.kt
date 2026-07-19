package com.interli.plural.core

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.util.CoilUtils
import okhttp3.OkHttpClient

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val imageLoader = ImageLoader.Builder(this)
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
