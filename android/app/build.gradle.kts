plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ua.otboi.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ua.otboi.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.3.0"
        // Откуда приложение берёт обновления: репозиторий, в котором его собрали (GitHub Actions
        // передаёт GITHUB_REPOSITORY сам). Пусто — автообновление выключено.
        val updateRepo = (project.findProperty("updateRepo") as String?) ?: System.getenv("GITHUB_REPOSITORY") ?: ""
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    // Ключ подписи приходит из секретов GitHub (OTBOI_KEYSTORE_B64 / OTBOI_KEYSTORE_PASSWORD),
    // чтобы каждое обновление ставилось поверх старой версии. Без ключа — обычная debug-подпись.
    val keystorePath: String? = System.getenv("OTBOI_KEYSTORE")
    val hasKeystore = keystorePath != null && file(keystorePath).exists()
    signingConfigs {
        create("release") {
            if (hasKeystore) {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("OTBOI_KEYSTORE_PASSWORD")
                keyAlias = "otboi"
                keyPassword = System.getenv("OTBOI_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (hasKeystore) "release" else "debug")
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
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "failed")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jsoup:jsoup:1.18.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
