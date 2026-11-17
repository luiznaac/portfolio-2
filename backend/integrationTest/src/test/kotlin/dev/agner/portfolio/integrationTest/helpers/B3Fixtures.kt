package dev.agner.portfolio.integrationTest.helpers

import java.io.File

// Gradle runs this module's tests from `backend/integrationTest`, so the B3 exports in the sibling
// module's test resources resolve one directory up. One loader keeps the missing-fixture failure
// message the same for every export.
internal fun b3NegociacaoFixture(): ByteArray = b3Fixture("b3-negociacao.xlsx")

// A B3 Movimentação export with one VALE3 split on the same ex-date a brapi payload restates at a
// different factor — the shared natural key the history run's precedence check reads.
internal fun b3SplitFixture(): ByteArray = b3Fixture("b3-movimentacao-split-2020.xlsx")

private fun b3Fixture(name: String): ByteArray {
    val fixture = File("../http-api/src/test/resources/$name")
    check(fixture.isFile) { "B3 fixture not found at ${fixture.absolutePath}" }
    return fixture.readBytes()
}
