import java.util.concurrent.TimeUnit

dependencies {
    testImplementation(project(":application"))
    // Reaches KtorConfig etc. transitively via :application already, but MigrationSchemaTest
    // needs persistence's own migrate()/allTables directly — depend on it explicitly rather
    // than lean on the transitive edge.
    testImplementation(project(":persistence"))
    testImplementation(project(":http-api"))
    testImplementation(project(":gateway"))
    testImplementation(project(":usecase"))
    testImplementation(libs.bundles.testDependencies)
    testImplementation(libs.kotest.extensions.spring)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers)
    testImplementation(libs.wiremock)
    implementation(libs.jackson.kotlin)

    // persistence declares these as `implementation`, so they don't come along transitively;
    // MigrationSchemaTest calls Database.connect/transaction/MigrationUtils directly.
    testImplementation(libs.exposed.core)
    testImplementation(libs.exposed.jdbc)
    testImplementation(libs.exposed.migration.core)
    testImplementation(libs.exposed.migration.jdbc)
}

// Why this guard exists: backend/AGENTS.md §Testing ("Import tests have two patterns").
val noApachePoi by tasks.registering {
    group = "verification"
    description = "Fails when Apache POI enters :integrationTest's compile classpath."

    val compileClasspath = configurations.named("testCompileClasspath")

    doLast {
        val poiArtifacts = compileClasspath.get().resolvedConfiguration.resolvedArtifacts
            .filter { it.moduleVersion.id.group == "org.apache.poi" }
        if (poiArtifacts.isNotEmpty()) {
            throw GradleException(
                ":integrationTest must not compile Apache POI, found " +
                    poiArtifacts.joinToString { it.moduleVersion.id.toString() } +
                    ". See backend/AGENTS.md §Testing for the fixture and staging patterns to use instead.",
            )
        }
    }
}

// Wired before the compile itself, not as a sibling of `test`: the compile is what runs out of
// memory, so a guard that ran after it would never report.
tasks.named("compileTestKotlin") {
    dependsOn(noApachePoi)
}

// Testcontainers only discovers an unreachable daemon once a spec's first container starts, which
// surfaces as an opaque DockerClientProviderStrategy error buried in the HTML report. Probe the
// daemon first so the whole integration lane fails in seconds with a message that says what to do.
val dockerPreflight by tasks.registering {
    group = "verification"
    description = "Fails fast, with an actionable message, when the Docker daemon is unavailable."

    doLast {
        fun dockerUnavailable(reason: String, detail: String = "", cause: Throwable? = null): Nothing =
            throw GradleException(
                """
                The integration tests need $reason
                Start Docker and retry, or run the Docker-free unit suite with: npm run be:unit
                $detail
                """.trimIndent(),
                cause,
            )

        val process = try {
            ProcessBuilder("docker", "info").redirectErrorStream(true).start()
        } catch (exception: java.io.IOException) {
            dockerUnavailable("Docker, but the `docker` CLI is not on PATH.", cause = exception)
        }
        // A wedged daemon would otherwise hang the preflight itself — the failure mode this task exists to cut short.
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = if (finished) process.exitValue() else null
        if (exitCode != 0) {
            val detail = output.lines()
                .filter { it.contains("error", ignoreCase = true) }
                .take(3)
                .ifEmpty { output.trim().lines().takeLast(3) }
                .joinToString("\n")
            dockerUnavailable(
                reason = if (exitCode == null) {
                    "a running Docker daemon, but `docker info` did not return in 30s."
                } else {
                    "a running Docker daemon, but `docker info` exited $exitCode."
                },
                detail = detail,
            )
        }
    }
}

tasks.test {
    dependsOn(dockerPreflight)
    // docker-java (3.4.2, pulled in by Testcontainers 1.21.3) negotiates an API version below the
    // Docker 29 daemon's 1.40 minimum, failing before any spec runs with an opaque
    // DockerClientProviderStrategy "Could not find a valid Docker environment". Pinning 1.44 names
    // the floor this lane works against without an ad-hoc JAVA_TOOL_OPTIONS on every host.
    systemProperty("api.version", "1.44")
}
