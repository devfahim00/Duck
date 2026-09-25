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
// a real CPython 3.13 + yt-dlp + curl_cffi for arm64-v8a. This is a SEPARATE
// top-level block, not android.defaultConfig.python{} - that DSL is
// Groovy-only and deprecated since Chaquopy 15; .kts files must use this
// chaquopy{} block instead.
//
// WHY --no-deps AND THE HAND-PINNED LIST: curl_cffi (0.15.0+) declares a
// runtime dependency on cffi>=2.0.0 - a pin that exists for FREE-THREADED
// CPython support (lexiforest/curl_cffi PR #697), not because the GIL-build
// code needs cffi 2.x. But no cffi 2.x Android wheels exist anywhere: PyPI
// publishes zero cffi android wheels, and Chaquopy's own index
// (chaquo.com/pypi-13.1) tops out at cffi 1.17.1 for
// cp313/android_24/arm64_v8a. pip's resolver therefore backtracks through
// every curl_cffi release and fails with ResolutionImpossible at
// installReleasePythonRequirements.
//
// Fix: disable dependency resolution entirely and pin the closed dependency
// set by hand. The set is small and fully known:
//   * yt-dlp             - no required deps (brotli/websockets/etc. are
//                          optional extras we deliberately don't bundle)
//   * curl_cffi          - needs cffi + certifi (both pinned below)
//   * cffi 1.17.1        - needs pycparser + chaquopy-libffi (both below);
//                          its _cffi_backend.so is ABI-compatible with
//                          curl_cffi's GIL-build _wrapper.abi3.so (neither
//                          exports _cffi_* symbols to the other: the wrapper
//                          is fully self-contained, both only need
//                          libpython3.13.so, which Chaquopy ships)
//   * certifi, pycparser, chaquopy-libffi - no deps of their own
// Once Chaquopy's index ships cffi 2.x for android_24_arm64_v8a, this can
// go back to plain install("yt-dlp"); install("curl_cffi") with no options().
chaquopy {
    defaultConfig {
        version = "3.13"
        pip {
            options("--no-deps")
            install("yt-dlp==2026.8.19")
            install("curl_cffi==0.16.3")   // android_24_arm64_v8a wheel from PyPI
            install("cffi==1.17.1")        // android_24_arm64_v8a wheel from chaquo.com/pypi-13.1
            install("chaquopy-libffi")     // cffi's bundled libffi, same Chaquopy index
            install("pycparser")           // cffi runtime dep (pure Python, PyPI)
            install("certifi")             // curl_cffi runtime dep (pure Python, PyPI)
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
