package dev.agner.portfolio.integrationTest.helpers

import java.io.File

// The committed synthetic Rico nota — the same bytes the parser test pins and the acceptance
// runbook uploads. Gradle runs this module's tests from `backend/integrationTest`, so the sibling
// module's test resources resolve one directory up.
internal fun ricoNotaFixture(): ByteArray = docsFixture("rico-nota-2019.pdf")

// The 2018–2021 synthetic broker note: four notes carrying the rename's old code, the rights sale, the FII
// and the closing 2021 trade, with two descriptions the alias seed does not know.
internal fun ricoHistoryFixture(): ByteArray = docsFixture("rico-nota-2018-2021.pdf")

// A real text PDF that is not a Rico note: the HTTP import must reject it and stage nothing.
internal fun nonRicoPdfFixture(): ByteArray = docsFixture("xp-relatorio-2026-10.pdf")

private fun docsFixture(name: String): ByteArray {
    val fixture = File("../http-api/src/test/resources/acceptance/$name")
    check(fixture.isFile) { "acceptance fixture not found at ${fixture.absolutePath}" }
    return fixture.readBytes()
}
