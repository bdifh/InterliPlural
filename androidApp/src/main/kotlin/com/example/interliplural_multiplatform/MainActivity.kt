package com.example.interliplural_multiplatform

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.interliplural_multiplatform.InterliPlural.Navigating.SideMenu
import com.example.interliplural_multiplatform.InterliPlural.PluralModule.InterliFrontpage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // InterliFrontpage()
            SideMenu()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}