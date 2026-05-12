plugins {
    id("org.eclipse.odi.build.internal.module")
}

description = "ODI CDI"

dependencies {
    annotationProcessor(project(":micronaut-odi-processor-cdi"))

    implementation(mn.micronaut.context)
    implementation(project(":micronaut-odi-core"))
    implementation(libs.cdi.api)

    testAnnotationProcessor(project(":micronaut-odi-processor-cdi"))

    testImplementation(libs.javax.annotation.api)
    testImplementation(mn.logback)
    testImplementation(project(":micronaut-odi-test-junit5"))
}
