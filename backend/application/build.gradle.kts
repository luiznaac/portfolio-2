import org.gradle.jvm.application.tasks.CreateStartScripts

plugins {
    application
}

application {
    mainClass.set("dev.agner.portfolio.application.BootKt")
    applicationDefaultJvmArgs = listOf(
        "-server",
        "-XX:+UseNUMA",
        "-XX:+UseG1GC",
        "-XX:+UseStringDeduplication",
    )
}

val writeBuildInfo by tasks.registering(WriteProperties::class) {
    destinationFile.set(layout.buildDirectory.file("generated/build-info/build-info.properties"))
    property("version", project.version.toString())
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/build-info"))
}

tasks.processResources {
    dependsOn(writeBuildInfo)
}

dependencies {
    implementation(project(":http-api"))
    implementation(project(":persistence"))
    implementation(project(":usecase"))
    implementation(project(":gateway"))

    implementation(libs.spring.boot)
    implementation(libs.snakeyaml)
    implementation(libs.logback.classic)

    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.exporter.logging)
    implementation(libs.opentelemetry.runtime.telemetry)
    implementation(libs.opentelemetry.logback.appender)
    implementation(libs.logstash.logback.encoder)

    testImplementation(libs.bundles.testDependencies)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.opentelemetry.sdk.testing)
}

// A second start script, `bin/migrate`, alongside the app's own `bin/application` — same
// classpath (every module's jar is already in lib/), different main class: the standalone Flyway
// migrator in persistence/migration/Migrator.kt. deploy/entrypoint.sh runs this before the app,
// since KtorConfig blocks the main thread for the process's lifetime and so never reaches a point
// where the app itself could safely run a migration on the way up.
val migrateStartScripts = tasks.register<CreateStartScripts>("migrateStartScripts") {
    mainClass.set("dev.agner.portfolio.persistence.migration.MigratorKt")
    applicationName = "migrate"
    outputDir = layout.buildDirectory.dir("migrateScripts").get().asFile
    classpath = tasks.named<CreateStartScripts>("startScripts").get().classpath
}

distributions {
    main {
        contents {
            from(migrateStartScripts) {
                into("bin")
                filePermissions { unix("rwxr-xr-x") }
            }
        }
    }
}
