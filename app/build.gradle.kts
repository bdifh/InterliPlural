plugins {
    alias(libs.plugins.android.application)
}

// ------- VERSION DISPLAY !!!! --------
val versionDisplayed = "V002-3e" // CHANGE THIS ONE WHEN MAKING UPDATES
// ------- VERSION DISPLAY !!!! --------

android {
    namespace = "com.interli.plural"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.interli.plural"
        minSdk = 24
        targetSdk = 35
        versionCode = 24 // DO NOT TOUCH
        versionName = "V002-1a" // DO NOT TOUCH = lowest easy downgrade version.

        buildConfigField("String", "VERSION_DISPLAYED", "\"$versionDisplayed\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isDebuggable = true
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.coil-kt:coil:1.4.0")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:image:4.6.2")
    implementation("io.noties.markwon:image-coil:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("InterliPlural-${versionDisplayed}.apk")
        }
    }
}