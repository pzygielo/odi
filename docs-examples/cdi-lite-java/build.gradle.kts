plugins {
    application
    id("org.eclipse.odi.build.internal.docs-example-java")
}

description = "ODI CDI Lite Java documentation example"

application {
    mainClass.set("org.eclipse.odi.docs.cdi.CheckoutApplication")
}

dependencies {
    annotationProcessor(project(":docs-examples:cdi-lite-build-extension"))
    implementation(project(":docs-examples:cdi-lite-build-extension"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Amicronaut.cdi.build.compatible.extensions=true")
}
