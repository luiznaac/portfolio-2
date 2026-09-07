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

dependencies {
    implementation(project(":http-api"))
    implementation(project(":persistence"))
    implementation(project(":usecase"))
    implementation(project(":gateway"))

    implementation(libs.spring.boot)
    implementation(libs.snakeyaml)
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
            }
        }
    }
}
