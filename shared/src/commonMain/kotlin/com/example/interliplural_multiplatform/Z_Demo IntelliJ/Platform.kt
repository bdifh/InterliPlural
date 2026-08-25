package com.example.interliplural_multiplatform

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform