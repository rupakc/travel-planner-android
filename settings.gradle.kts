pluginManagement {
    includeBuild("build-logic")
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
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "travel-planner-android"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(
    ":core:common",
    ":core:model",
    ":core:network",
    ":core:database",
    ":core:datastore",
    ":core:data",
    ":core:domain",
    ":core:designsystem",
    ":core:ui",
    ":core:notifications",
    ":core:testing",
)
include(
    ":feature:auth",
    ":feature:onboarding",
    ":feature:home",
    ":feature:search",
    ":feature:results",
    ":feature:plan",
    ":feature:chat",
    ":feature:alerts",
    ":feature:settings",
    ":feature:nearby",
    ":feature:play",
    ":feature:account",
    ":feature:trips",
    ":feature:share",
    ":feature:social",
)
