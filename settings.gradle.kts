pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com.android.*")
                includeGroupByRegex("com.google.*")
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
        mavenCentral()
    }
}

rootProject.name = "pet"
include(":app")
include(":core:core-common")
include(":core:core-domain")
include(":core:core-data")
include(":core:core-eventbus")
include(":pet:pet-float")
include(":pet:pet-render")
include(":pet:pet-behavior")
include(":pet:pet-service")
include(":algorithm:algorithm-rl")
include(":algorithm:algorithm-sentiment")
include(":algorithm:algorithm-cv")
include(":algorithm:algorithm-path")
include(":algorithm:algorithm-prediction")
