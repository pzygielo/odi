# Open DI

Open DI (ODI) is a CDI Lite implementation backed by Micronaut's compile-time dependency injection runtime.

ODI targets CDI Lite rather than the CDI Full runtime extension, decorator, and passivation surface. The implementation uses Micronaut bean definitions, annotation processing, generated proxies, and build-compatible extensions to provide CDI APIs on top of a Micronaut-backed runtime.

The CDI Lite TCK passes with CDI Full exclusions only.

## Classpath shape

Keep the ODI processor on the annotation processor path and keep the CDI runtime on the application classpath:

```kotlin
dependencies {
    annotationProcessor("org.eclipse.odi:micronaut-odi-processor-cdi:<version>")

    implementation("org.eclipse.odi:micronaut-odi-cdi:<version>")
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.1.0")
}
```

## Useful commands

```bash
./gradlew :docs-examples:cdi-lite-java:test
./gradlew :micronaut-tck-runner:test
./gradlew publishGuide
```
