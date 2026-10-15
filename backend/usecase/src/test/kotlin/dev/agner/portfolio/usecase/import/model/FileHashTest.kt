package dev.agner.portfolio.usecase.import.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class FileHashTest : StringSpec({

    // Known-good SHA-256 of "abc" (FIPS 180-2 test vector), an independent source of truth.
    "sha256Hex matches the published digest of 'abc'" {
        "abc".toByteArray().sha256Hex() shouldBe
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    }

    "sha256Hex is stable across calls" {
        val bytes = "B3 export".toByteArray()

        bytes.sha256Hex() shouldBe bytes.sha256Hex()
    }
})
