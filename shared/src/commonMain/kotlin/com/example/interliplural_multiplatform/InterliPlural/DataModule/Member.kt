package com.example.interliplural_multiplatform.InterliPlural.DataModule

import kotlinx.serialization.Serializable

@Serializable
data class Member(
    val id: String,
    val name: String,
    val color: String,
)
