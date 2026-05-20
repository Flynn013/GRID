plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "ai.grid"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.grid"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
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

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    androidResources {
        noCompress += "so"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/**",
                "OSGI-INF/**",
                "**/*.kotlin_module",
                "**/*.version",
                "**/LICENSE.txt"
            )
        }
    }
}

dependencies {
    // ── Godot Engine AAR ─────────────────────────────────────────────────────────
    // Built by CI (build-grid-apk.yml) from platform/android/java/lib.
    // Place compiled artifact at grid-android/app/libs/godot-lib.release.aar.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // ── Core Android ─────────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.browser:browser:1.8.0")

    // ── Jetpack Compose ───────────────────────────────────────────────────────────
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Markdown rendering (chat messages) ────────────────────────────────────────
    implementation("com.halilibo.compose-richtext:richtext-commonmark:1.0.0-alpha01")
    implementation("com.halilibo.compose-richtext:richtext-ui-material3:1.0.0-alpha01")

    // ── Coroutines / Serialization ────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ── HTTP — LLM API calls (Anthropic / Gemini) ─────────────────────────────────
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(platform("io.ktor:ktor-bom:3.0.3"))
    implementation("io.ktor:ktor-client-okhttp")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    // ── Local LLM fallback (LiteRT — Google AI Edge, from SPL-NTR) ───────────────
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")

    // ── VCS ───────────────────────────────────────────────────────────────────────
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r")

    // ── Code / tree-sitter ────────────────────────────────────────────────────────
    implementation("com.itsaky.androidide.treesitter:android-tree-sitter:4.0.0")
    implementation("com.itsaky.androidide.treesitter:tree-sitter-kotlin:4.0.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
