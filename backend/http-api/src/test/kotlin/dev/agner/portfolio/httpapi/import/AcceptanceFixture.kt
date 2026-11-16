package dev.agner.portfolio.httpapi.import

// The committed acceptance artifacts are test resources, so a parser test pins the exact file the
// acceptance runbook uploads. This is the acceptance loader beside B3Fixtures, the classpath loader
// for the parsers' own fixtures.
internal fun loadAcceptanceFixture(name: String): AcceptanceFixture {
    val bytes = checkNotNull(AcceptanceFixture::class.java.classLoader.getResource("acceptance/$name")) {
        "Acceptance fixture $name not found in test resources"
    }.readBytes()
    return AcceptanceFixture(name, bytes)
}

internal data class AcceptanceFixture(val name: String, val bytes: ByteArray)
