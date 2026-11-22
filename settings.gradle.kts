pluginManagement {
    repositories {
        google()
        // Regional Google Cloud mirror for Maven Central. This avoids CI failures
        // when repo.maven.apache.org returns HTTP 403.
        maven {
            name = "MavenCentralAsiaMirror"
            url = uri("https://maven-central-asia.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Keep the mirror before Maven Central so Gradle can use it as fallback.
        maven {
            name = "MavenCentralAsiaMirror"
            url = uri("https://maven-central-asia.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
    }
}

rootProject.name = "SmartNet"
include(":app")
