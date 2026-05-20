import java.io.ByteArrayOutputStream
import java.io.File
import org.gradle.api.tasks.JavaExec

plugins {
    id("org.eclipse.odi.build.internal.base")
    id("com.adarshr.test-logger")
}

description = "CDI TCK runner"

val generatedCdiTckSources = layout.buildDirectory.dir("generated/sources/cdiTck/java/test")

val cdiSignatureApi by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val cdiSignatureTck by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val cdiSignatureTool by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    annotationProcessor(project(":micronaut-odi-processor-cdi"))

    implementation(project(":micronaut-odi-cdi"))
    implementation(project(":micronaut-odi-processor-cdi"))
    implementation(libs.cdi.api)
    implementation(libs.jul.to.slf4j) {
        version {
            require(mn.versions.slf4j.get())
        }
    }
    implementation(libs.cdi.tck.api)
    implementation(libs.cdi.tck.impl)
    implementation(mn.logback)
    implementation(mn.micronaut.inject.java)

    testAnnotationProcessor(project(":micronaut-odi-processor-cdi"))

    testImplementation(libs.cdi.tck.impl) {
        artifact {
            classifier = "sources"
        }
    }
    testCompileOnly(libs.cdi.tck.impl) {
        artifact {
            classifier = "suite"
            type = "xml"
        }
    }

    cdiSignatureApi(libs.cdi.api)
    cdiSignatureTck(libs.cdi.tck.impl)
    cdiSignatureTool("jakarta.tck:sigtest-maven-plugin:2.6")
}

val observingBeanSource = "org/jboss/cdi/tck/tests/se/events/lifecycle/ObservingBean.java"
val addedBeanClassesProperty = "org.eclipse.odi.cdi.se.added-bean-classes"

val unpackCdiTckSources by tasks.registering {
    val outputFile = generatedCdiTckSources.map { it.file(observingBeanSource) }
    inputs.files(configurations.testRuntimeClasspath)
    outputs.file(outputFile)
    doLast {
        val sourceJar = configurations.testRuntimeClasspath.get()
            .single { it.name.contains("cdi-tck-core-impl") && it.name.endsWith("-sources.jar") }
        val sourceFile = zipTree(sourceJar).matching {
            include(observingBeanSource)
        }.singleFile
        val source = sourceFile.readText()
            .replace(
                "import jakarta.enterprise.context.ApplicationScoped;\n",
                "import jakarta.enterprise.context.ApplicationScoped;\nimport io.micronaut.context.annotation.Requires;\n"
            )
            .replace(
                "@ApplicationScoped\npublic class ObservingBean",
                "@Requires(property = \"$addedBeanClassesProperty\", pattern = \".*org.jboss.cdi.tck.tests.se.events.lifecycle.ObservingBean.*\")\n@ApplicationScoped\npublic class ObservingBean"
            )
        val targetFile = outputFile.get().asFile
        targetFile.parentFile.mkdirs()
        targetFile.writeText(source)
    }
}

sourceSets {
    test {
        java.srcDir(generatedCdiTckSources)
    }
}

tasks.named("compileTestJava") {
    dependsOn(unpackCdiTckSources)
}

testlogger {
    showExceptions = false
    showStackTraces = false
    showFullStackTraces = false
    showStandardStreams = false
    showPassedStandardStreams = false
    showSkippedStandardStreams = false
    showFailedStandardStreams = false
}

fun Test.configureCdiLiteTck() {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    setScanForTestClasses(false)
    useTestNG {
        excludeGroups("cdi-full", "integration", "javaee-full", "se")
    }
    systemProperty("org.jboss.cdi.tck.libraryDirectory", layout.buildDirectory.dir("tck-lib").get().asFile.absolutePath)
}

tasks.register<Test>("fullTckTest") {
    configureCdiLiteTck()
    useTestNG {
        doFirst {
            val testSuiteLocation = configurations.testCompileClasspath.get().filter {
                it.name.contains("cdi-tck-core-impl") && it.name.contains("xml")
            }.asPath
            suites(File(testSuiteLocation))
        }
    }
}

fun xmlEscape(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

tasks.register<JavaExec>("cdiSignatureTest") {
    group = "verification"
    description = "Runs the Jakarta CDI API signature test using the TCK-provided signature file."

    val cdiVersion = libs.cdi.tck.impl.get().versionConstraint.requiredVersion
    val outputDir = layout.buildDirectory.dir("reports/cdi-signature-test")
    val signatureFile = outputDir.map { it.file("cdi-api-jdk17.sig") }
    val signatureReport = outputDir.map { it.file("signature-test-report.txt") }
    val metadataFile = outputDir.map { it.file("signature-test.properties") }
    val junitReport = layout.buildDirectory.file("test-results/cdiSignatureTest/TEST-cdi-signature-test.xml")
    val output = ByteArrayOutputStream()
    lateinit var extractedSignatureFile: File
    lateinit var tckJar: File
    lateinit var apiArtifacts: List<File>

    inputs.files(cdiSignatureApi)
    inputs.files(cdiSignatureTck)
    inputs.files(cdiSignatureTool)
    outputs.dir(outputDir)
    outputs.file(junitReport)

    classpath = cdiSignatureTool
    mainClass.set("com.sun.tdk.signaturetest.Main")
    standardOutput = output
    errorOutput = output
    isIgnoreExitValue = true

    doFirst {
        val outputDirectory = outputDir.get().asFile
        outputDirectory.mkdirs()

        tckJar = cdiSignatureTck.resolve()
            .singleOrNull { it.name == "cdi-tck-core-impl-$cdiVersion.jar" }
            ?: error("Could not resolve cdi-tck-core-impl-$cdiVersion.jar")
        copy {
            from(zipTree(tckJar)) {
                include("cdi-api-jdk17.sig")
            }
            into(outputDirectory)
        }

        extractedSignatureFile = signatureFile.get().asFile
        if (!extractedSignatureFile.isFile) {
            error("Could not extract ${extractedSignatureFile.name} from ${tckJar.name}")
        }

        apiArtifacts = cdiSignatureApi.resolve().sortedBy { it.name }
        val apiClasspath = apiArtifacts.joinToString(File.pathSeparator) { it.absolutePath }
        val signatureArgs = listOf(
            "Test",
            "-FileName", extractedSignatureFile.absolutePath,
            "-static",
            "-b",
            "-Mode", "bin",
            "-ApiVersion", cdiVersion,
            "-PackageWithoutSubpackages", "jakarta.decorator",
            "-Package", "jakarta.enterprise",
            "-PackageWithoutSubpackages", "jakarta.interceptor",
            "-BootCP", "17",
            "-Classpath", apiClasspath
        )
        args(signatureArgs)
    }

    doLast {
        val rawReport = output.toString(Charsets.UTF_8)
        val sanitizedReport = rawReport
            .lineSequence()
            .filterNot { it.contains("SignatureTest.args:") }
            .joinToString(System.lineSeparator())
            .trimEnd() + System.lineSeparator()
        val passed = rawReport.contains("STATUS:Passed.")
        signatureReport.get().asFile.writeText(sanitizedReport)
        metadataFile.get().asFile.writeText(
            """
            status=${if (passed) "passed" else "failed"}
            tests=1
            failures=${if (passed) 0 else 1}
            errors=0
            skipped=0
            sigtestTool=jakarta.tck:sigtest-maven-plugin:2.6
            signatureFile=${extractedSignatureFile.name}
            signatureSource=${tckJar.name}
            bootCpRelease=17
            packages=jakarta.decorator,jakarta.enterprise.**,jakarta.interceptor
            apiArtifacts=${apiArtifacts.joinToString(",") { it.name }}
            report=${signatureReport.get().asFile.name}
            """.trimIndent() + System.lineSeparator()
        )

        val junitFile = junitReport.get().asFile
        junitFile.parentFile.mkdirs()
        val failureElement = if (passed) {
            ""
        } else {
            """
              <failure type="junit.framework.AssertionFailedError" message="CDI signature test failed">${xmlEscape(sanitizedReport)}</failure>
            """.trimIndent()
        }
        junitFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="CDI signature test" tests="1" failures="${if (passed) 0 else 1}" errors="0" skipped="0" time="0.0">
              <testcase classname="jakarta.enterprise.cdi.signature" name="cdi-api-jdk17" time="0.0">
            ${if (failureElement.isBlank()) "" else "    $failureElement"}
              </testcase>
            </testsuite>
            """.trimIndent() + System.lineSeparator()
        )

        if (!passed) {
            throw GradleException("CDI signature test failed; see ${signatureReport.get().asFile}")
        }
    }
}

tasks.register<Test>("singleTest") {
    configureCdiLiteTck()
    val suiteFile = layout.buildDirectory.file("generated-testng/singleTest.xml")
    val testClass = providers.gradleProperty("tckSingleClass")
        .orElse("org.jboss.cdi.tck.tests.event.fires.FireEventTest")
    val testMethod = providers.gradleProperty("tckSingleMethod")
    inputs.property("tckSingleClass", testClass)
    inputs.property("tckSingleMethod", testMethod.orElse(""))
    doFirst {
        val file = suiteFile.get().asFile
        file.parentFile.mkdirs()
        val methodFilter = testMethod.orNull?.let {
            """
                    <methods>
                      <include name="$it"/>
                    </methods>
            """.trimIndent()
        } ?: ""
        file.writeText(
            """
            <!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd" >
            <suite name="Sample Suite" verbose="0" configfailurepolicy="continue">
              <listeners>
                <listener class-name="org.jboss.cdi.tck.impl.testng.SingleTestClassMethodInterceptor"/>
                <listener class-name="org.jboss.cdi.tck.impl.testng.ConfigurationLoggingListener"/>
                <listener class-name="org.jboss.cdi.tck.impl.testng.ProgressLoggingTestListener"/>
                <listener class-name="org.testng.reporters.SuiteHTMLReporter"/>
                <listener class-name="org.testng.reporters.FailedReporter"/>
                <listener class-name="org.testng.reporters.XMLReporter"/>
                <listener class-name="org.testng.reporters.EmailableReporter"/>
                <listener class-name="org.testng.reporters.TestHTMLReporter"/>
              </listeners>
              <test name="Sample Test">
                <classes>
                  <class name="${testClass.get()}">
            $methodFilter
                  </class>
                </classes>
              </test>
            </suite>
            """.trimIndent()
        )
    }
    useTestNG {
        suites(suiteFile.get().asFile)
    }
}

tasks.test {
    configureCdiLiteTck()
    useTestNG {
        suites(file("failingTests.xml"))
    }
}
