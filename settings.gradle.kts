pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // HMS Core SDK 仓库（放在最后）
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // HMS Core SDK 仓库（放在最后）
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
}

rootProject.name = "AmoraLink"
include(":app")
