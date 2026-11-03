pluginManagement {
    repositories {
        google()
        // Maven Central'ın Google aynası (rate-limit'e karşı), ardından Central
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Maven Central'ın Google aynası (rate-limit'e karşı), ardından Central
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "Ajanda"
include(":app")
