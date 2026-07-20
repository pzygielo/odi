pluginManagement {
    plugins {
        id("io.micronaut.build.shared.settings") version providers.gradleProperty("micronautSharedSettingVersion").get()
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.micronaut.build.shared.settings")
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "odi-project"

micronautBuild {
    useStandardizedProjectNames = true
    nonStandardProjectPathPrefixes.add(":docs-examples")
    nonStandardProjectPathPrefixes.add(":docs-examples:")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("mn") {
            from(files("gradle/mn.libs.versions.toml"))
        }
    }
    repositories {
        mavenCentral()
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            mavenContent {
                snapshotsOnly()
            }
        }
    }
}

include(":odi-core")
include(":odi-processor-cdi")
include(":odi-test-junit5")
include(":odi-tck-runner")
include(":odi-cdi")
include(":docs-examples:cdi-lite-build-extension")
include(":docs-examples:cdi-lite-java")

project(":odi-core").projectDir = file("core")
project(":odi-processor-cdi").projectDir = file("processor-cdi")
project(":odi-test-junit5").projectDir = file("test-junit5")
project(":odi-cdi").projectDir = file("cdi")
project(":odi-tck-runner").projectDir = file("tck-runner")
