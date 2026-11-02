package dev.agner.portfolio.httpapi.import

import java.io.File

// The committed acceptance artifacts are read from the repo's docs/ tree rather than copied into
// test resources, so a parser test pins the exact file the acceptance runbook uploads. This is the
// docs-tree loader beside B3Fixtures, the classpath loader for the parsers' own fixtures.
internal fun acceptanceDocsFixture(name: String): File {
    val docsDir = File(
        System.getProperty("docsDir")
            ?: error("docsDir system property is not set; see http-api/build.gradle.kts"),
    )
    return docsDir.resolve("acceptance/fixtures/$name")
}
