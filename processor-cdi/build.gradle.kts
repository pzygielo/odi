import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("org.eclipse.odi.build.internal.module")
}

description = "ODI Processor CDI"

dependencies {
    api(mn.micronaut.inject.java)

    implementation(project(":micronaut-odi-core"))
    implementation(mn.micronaut.context)
    implementation(libs.cdi.api)
    implementation(libs.cdi.lang.model)
    implementation(libs.cdi.tck.lang.model)
    implementation(libs.javax.annotation.api)

    testImplementation(mn.micronaut.inject.java.test)

    testImplementation(libs.cdi.api2)
    testImplementation(libs.helidon.config.mp)
    testImplementation(libs.smallrye.fault.tolerance)

    testImplementation(mn.micronaut.test.spock)
    testImplementation(mn.micronaut.test.junit5)
}

tasks.withType<JavaCompile>().configureEach {
    options.forkOptions.jvmArgs?.add("-ea")
}

tasks.withType<GroovyCompile>().configureEach {
    options.forkOptions.jvmArgs?.add("-ea")
}
