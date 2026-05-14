import me.champeau.gradle.igp.gitRepositories
import org.gradle.api.GradleException
import org.gradle.api.initialization.ConfigurableIncludedBuild

pluginManagement {
    plugins {
        id("io.micronaut.build.shared.settings") version providers.gradleProperty("micronautSharedSettingVersion").get()
        id("me.champeau.includegit") version "0.3.2"
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("me.champeau.includegit")
    id("io.micronaut.build.shared.settings")
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "odi-project"

micronautBuild {
    useStandardizedProjectNames = true
    nonStandardProjectPathPrefixes.add(":docs-examples")
    nonStandardProjectPathPrefixes.add(":docs-examples:")
}

fun booleanGradleProperty(name: String): Boolean? {
    val value = providers.gradleProperty(name).orNull ?: return null
    return value.toBooleanStrictOrNull()
        ?: throw GradleException("Expected Gradle property '$name' to be 'true' or 'false' but got '$value'")
}

fun ConfigurableIncludedBuild.substituteMicronautCore() {
    dependencySubstitution {
        listOf(
            "aop",
            "buffer-netty",
            "context",
            "context-propagation",
            "core",
            "core-bom" to "micronaut-core-bom",
            "core-processor",
            "core-reactive",
            "discovery-core",
            "function",
            "function-client",
            "function-web",
            "graal",
            "http",
            "http-client",
            "http-client-core",
            "http-client-jdk",
            "http-netty",
            "http-netty-http3",
            "http-server",
            "http-server-netty",
            "http-server-tck",
            "http-tck",
            "http-validation",
            "inject",
            "inject-groovy",
            "inject-groovy-test",
            "inject-java",
            "inject-java-helper",
            "inject-java-helper2",
            "inject-java-test",
            "inject-kotlin",
            "inject-kotlin-test",
            "inject-test-utils",
            "jackson-core",
            "jackson-databind",
            "json-core",
            "management",
            "messaging",
            "module-info",
            "module-info-runtime",
            "retry",
            "router",
            "runtime",
            "runtime-osx",
            "websocket"
        ).map { entry ->
            when (entry) {
                is Pair<*, *> -> entry.first.toString() to entry.second.toString()
                else -> entry.toString() to "micronaut-$entry"
            }
        }.forEach { (_, moduleName) ->
            substitute(module("io.micronaut:$moduleName")).using(project(":$moduleName"))
        }
    }
}

val includeMicronautCore = booleanGradleProperty("odi.include.micronaut.core") ?: true
if (includeMicronautCore) {
    val localMicronautCore = providers.gradleProperty("local.git.odi.micronaut-core").orNull
        ?: providers.environmentVariable("LOCAL_GIT_MICRONAUT_CORE").orNull
        ?: "/Users/graemerocher/dev/micronaut/core.cdi"
    val localMicronautCoreDir = file(localMicronautCore)
    if (localMicronautCoreDir.isDirectory && localMicronautCoreDir.resolve(".git").exists()) {
        includeBuild(localMicronautCoreDir) {
            name = "micronaut-core"
            substituteMicronautCore()
        }
    } else {
        gitRepositories {
            include("micronaut-core-cdi") {
                uri.set("https://github.com/micronaut-projects/micronaut-core.git")
                branch.set("5.1.x")
                includeBuild {
                    name = "micronaut-core"
                    substituteMicronautCore()
                }
            }
        }
    }
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
include(":tck-runner")
include(":odi-cdi")
include(":odi-processor-mp")
include(":docs-examples:cdi-lite-build-extension")
include(":docs-examples:cdi-lite-java")

project(":odi-core").projectDir = file("core")
project(":odi-processor-cdi").projectDir = file("processor-cdi")
project(":odi-test-junit5").projectDir = file("test-junit5")
project(":odi-cdi").projectDir = file("cdi")
project(":odi-processor-mp").projectDir = file("processor-mp")
