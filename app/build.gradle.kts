plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "com.devfahim00.duck"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.devfahim00.duck"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.2.0"

        // Ship arm64-v8a only: covers virtually all modern Android phones and
        // keeps the APK roughly 3x smaller than a universal build.
        // Also the ONLY ABI curl_cffi currently publishes an Android wheel
        // for (curl_cffi-*-android_24_arm64_v8a.whl on PyPI) - convenient
        // overlap, not a coincidence we engineered.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

    }

    signingConfigs {
        // Populated in CI from the KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS /
        // KEY_PASSWORD env vars (see .github/workflows/android.yml). Left unset for
        // local dev builds — assembleDebug is unaffected; assembleRelease locally
        // needs the same env vars exported by hand.
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_FILE")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        // youtubedl-android stores python/ffmpeg/aria2c as lib*.zip.so and executes
        // them from nativeLibraryDir at runtime, so libs must be extracted.
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// STAGE 1 of the curl_cffi migration (see ChaquopyDiagnostics.kt). Not wired
// into the real engine yet - this only proves Chaquopy can resolve + package
// a real CPython 3.13 + yt-dlp + curl_cffi for arm64-v8a. Python 3.13 chosen
// because that's the version curl_cffi actually publishes an
// "android_24_arm64_v8a" wheel for on PyPI (verified by hand: `pip download
// curl_cffi --platform android_24_arm64_v8a --python-version 3.13 --abi
// cp313 --only-binary=:all:` resolves
// curl_cffi-0.16.3-cp313-cp313-android_24_arm64_v8a.whl, MIT licensed,
// Requires-Python >=3.10). This is a SEPARATE top-level block, not
// android.defaultConfig.python{} - that DSL is Groovy-only and deprecated
// since Chaquopy 15; .kts files must use this chaquopy{} block instead.
chaquopy {
    defaultConfig {
        version = "3.13"
        pip {
            install("yt-dlp")
            install("curl_cffi")
        }
    }
}

dependencies {
    val youtubedlAndroid = "0.18.1"

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:aria2c:$youtubedlAndroid")
}
