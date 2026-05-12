plugins {
    id("org.eclipse.odi.build.internal.base")
    id("com.adarshr.test-logger")
}

description = "CDI TCK runner"

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
        excludeGroups("cdi-full", "javaee-full")
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
