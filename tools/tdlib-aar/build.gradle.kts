plugins {
    id("com.android.library") version "8.13.2"
}

val tdlibOutputDir = providers.gradleProperty("tdlibOutputDir")
    .map(::file)
    .orNull
    ?: error("Pass -PtdlibOutputDir=<official example/android/tdlib directory>")
val tdlibAbis = providers.gradleProperty("tdlibAbis")
    .orElse("arm64-v8a")
    .get()
    .split(',')
    .map { it.trim() }
    .filter { it.isNotEmpty() }

require(tdlibOutputDir.resolve("java/org/drinkless/tdlib/Client.java").isFile) {
    "Official TDLib generated Client.java is missing from $tdlibOutputDir"
}
require(tdlibOutputDir.resolve("java/org/drinkless/tdlib/TdApi.java").isFile) {
    "Official TDLib generated TdApi.java is missing from $tdlibOutputDir"
}
tdlibAbis.forEach { abi ->
    require(tdlibOutputDir.resolve("libs/$abi/libtdjni.so").isFile) {
        "Official TDLib JNI library is missing for $abi"
    }
}

android {
    namespace = "org.drinkless.tdlib.prebuilt"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        ndk {
            abiFilters += tdlibAbis
        }
    }

    sourceSets.named("main") {
        java.srcDir(tdlibOutputDir.resolve("java"))
        jniLibs.srcDir(tdlibOutputDir.resolve("libs"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
