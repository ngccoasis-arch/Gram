pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GramPinnedTdlib"
            url = uri("prebuilts/tdlib-maven")
            content { includeGroup("org.telegram") }
        }
    }
}

rootProject.name = "Gram"
include(":app", ":core-tdlib", ":core-media")

