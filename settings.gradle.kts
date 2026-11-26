pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Robolectric's very large test jars come from Google's mirror of Maven Central,
        // because Central applies a per-IP download limit to them.
        exclusiveContent {
            forRepository { maven("https://maven-central.storage-download.googleapis.com/maven2/") }
            filter { includeGroup("org.robolectric") }
        }
        mavenCentral()
    }
}

rootProject.name = "Mittsu"
include(":app")
