package com.example.interliplural_multiplatform

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.interliplural_multiplatform.InterliPlural.Navigating.SideMenu
import com.example.interliplural_multiplatform.InterliPlural.PluralModule.InterliFrontpage

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "InterliPlural_Multiplatform",
    ) {
        Row() {
            // InterliFrontpage()
            SideMenu()
        }
    }
}