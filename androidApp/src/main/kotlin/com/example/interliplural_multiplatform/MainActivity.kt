package com.example.interliplural_multiplatform

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.interliplural_multiplatform.InterliPlural.DataModule.createAndroidDataStore
import com.example.interliplural_multiplatform.InterliPlural.DataModule.createDataStore
import com.example.interliplural_multiplatform.InterliPlural.Navigating.SideMenu
import com.example.interliplural_multiplatform.InterliPlural.PluralModule.InterliFrontpage
import com.example.interliplural_multiplatform.InterliPlural.DataModule.initializeDataStore

class MainActivity : ComponentActivity() {

   // private val dataStore by lazy {
      //  createAndroidDataStore(applicationContext)
   // }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeDataStore(this)

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