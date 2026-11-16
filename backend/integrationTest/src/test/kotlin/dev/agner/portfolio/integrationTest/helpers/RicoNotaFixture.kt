package dev.agner.portfolio.integrationTest.helpers

import java.io.File

// The committed synthetic Rico nota — the same bytes the parser test pins and the acceptance
// runbook uploads. Gradle runs this module's tests from `backend/integrationTest`, so the repo's
// docs tree resolves two directories up.
internal fun ricoNotaFixture(): ByteArray = docsFixture("rico-nota-2019.pdf")

// A real text PDF that is not a Rico note: the HTTP import must reject it and stage nothing.
internal fun nonRicoPdfFixture(): ByteArray = docsFixture("xp-relatorio-2026-10.pdf")

private fun docsFixture(name: String): ByteArray {
    val fixture = File("../../docs/acceptance/fixtures/$name")
    check(fixture.isFile) { "acceptance fixture not found at ${fixture.absolutePath}" }
    return fixture.readBytes()
}
