package com.example.interliplural_multiplatform.InterliPlural.DataModule

import kotlinx.serialization.Serializable

@Serializable
data class FrontHistory(
    val memberId : String,
    val frontStartedAt : String,
    val frontStoppedAt : String,
    val frontingNote : String
)
