import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val tdlibVersions = Properties().apply {
    rootProject.file("gradle/tdlib.versions.properties").inputStream().use(::load)
}
val pinnedTdlibCommit = tdlibVersions.getProperty("TDLIB_COMMIT")
val tdlibMavenRoot = rootProject.file("prebuilts/tdlib-maven")
val tdlibAar = tdlibMavenRoot.resolve(
    "org/telegram/tdlib-android/$pinnedTdlibCommit/tdlib-android-$pinnedTdlibCommit.aar"
)
val tdlibMetadata = tdlibMavenRoot.resolve("tdlib-metadata.properties")
val tdlibMode = providers.gradleProperty("gramTdlibMode").orElse("demo").get()
require(tdlibMode == "demo" || tdlibMode == "production") {
    "gramTdlibMode must be either 'demo' or 'production'"
}
val useProductionTdlib = tdlibMode == "production"
if (useProductionTdlib) {
    require(tdlibAar.isFile) {
        "Production mode requires the pinned TDLib Maven artifact. Run scripts/download-tdlib-artifact.sh in CI."
    }
    require(tdlibMetadata.isFile) {
        "Production mode requires prebuilts/tdlib-maven/tdlib-metadata.properties"
    }
    val metadata = Properties().apply { tdlibMetadata.inputStream().use(::load) }
    require(metadata.getProperty("TDLIB_COMMIT") == pinnedTdlibCommit) {
        "TDLib AAR commit does not match the repository pin $pinnedTdlibCommit"
    }
    val digest = MessageDigest.getInstance("SHA-256")
    tdlibAar.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
    require(actualSha256.equals(metadata.getProperty("TDLIB_AAR_SHA256"), ignoreCase = true)) {
        "TDLib AAR checksum does not match tdlib-metadata.properties"
    }
}

android {
    namespace = "com.gram.core.tdlib"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "HAS_TDLIB", useProductionTdlib.toString())
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.named("main") {
        java.srcDir(if (useProductionTdlib) "src/tdlib/kotlin" else "src/demo/kotlin")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    if (useProductionTdlib) implementation("org.telegram:tdlib-android:$pinnedTdlibCommit")
    testImplementation(libs.junit)
}
