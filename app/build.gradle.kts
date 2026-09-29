import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val credentialProperties = Properties().apply {
    listOf(rootProject.file("local.properties"), rootProject.file("telegram.properties"))
        .filter { it.isFile }
        .forEach { file -> file.inputStream().use(::load) }
}

fun telegramCredential(gradleName: String, environmentName: String, fallback: String): String =
    providers.gradleProperty(gradleName).orNull?.takeIf(String::isNotBlank)
        ?: providers.environmentVariable(environmentName).orNull?.takeIf(String::isNotBlank)
        ?: credentialProperties.getProperty(gradleName)?.takeIf(String::isNotBlank)
        ?: fallback

fun quotedBuildConfigValue(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val telegramApiIdText = telegramCredential("telegram.apiId", "TELEGRAM_API_ID", "0")
val telegramApiId = telegramApiIdText.toIntOrNull()
    ?: error("telegram.apiId/TELEGRAM_API_ID must be a decimal integer")
val telegramApiHash = telegramCredential("telegram.apiHash", "TELEGRAM_API_HASH", "not-configured")
val telegramCredentialsConfigured = telegramApiId > 0 && telegramApiHash != "not-configured"

val tdlibVersions = Properties().apply {
    rootProject.file("gradle/tdlib.versions.properties").inputStream().use(::load)
}
val tdlibAndroidAbis = tdlibVersions.getProperty("TDLIB_ANDROID_ABIS")
    .split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)
require(tdlibAndroidAbis.isNotEmpty()) { "TDLIB_ANDROID_ABIS must contain at least one ABI" }

android {
    namespace = "com.gram.client"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gram.client"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId.toString())
        buildConfigField("String", "TELEGRAM_API_HASH", quotedBuildConfigValue(telegramApiHash))
        buildConfigField("boolean", "TELEGRAM_API_CREDENTIALS_CONFIGURED", telegramCredentialsConfigured.toString())
        ndk {
            abiFilters += tdlibAndroidAbis
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core-tdlib"))
    implementation(project(":core-media"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
