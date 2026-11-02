package dev.agner.portfolio.integrationTest.helpers

import java.io.File

// Gradle runs this module's tests from `backend/integrationTest`, so the fixture in the sibling
// module's test resources (http-api/src/test/resources) resolves one directory up.
internal fun b3NegociacaoFixture(): ByteArray {
    val fixture = File("../http-api/src/test/resources/b3-negociacao.xlsx")
    check(fixture.isFile) { "B3 fixture not found at ${fixture.absolutePath}" }
    return fixture.readBytes()
}
