package com.interli.plural

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.util.CoilUtils
import okhttp3.OkHttpClient

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configure Coil globally with optimized caching
        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .cache(CoilUtils.createDefaultCache(this))
                    .build()
            }
            .availableMemoryPercentage(0.25) // Use 25% of available RAM for image cache
            .crossfade(true)
            .allowHardware(false) // Hardware bitmaps can cause issues in some contexts (like VMs)
            .build()
        
        Coil.setImageLoader(imageLoader)
    }
}
