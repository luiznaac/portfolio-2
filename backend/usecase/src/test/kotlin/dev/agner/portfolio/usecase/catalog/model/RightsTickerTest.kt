package dev.agner.portfolio.usecase.catalog.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

// The B3 subscription-right code rule: a right is the underlying's code with its final class digit
// replaced by 2. A stock right is five characters (PETR4 -> PETR2); a unit/FII right is the six-
// character `XXXX11` -> `XXXX12`. A BDR's `XXXX32` ends in 32, so it is never a right.
class RightsTickerTest : StringSpec({

    "derives the rights code from a stock and a FII" {
        "PETR4".rightsTicker() shouldBe "PETR2"
        "RBRP11".rightsTicker() shouldBe "RBRP12"
    }

    "recognizes the rights shape without mistaking a BDR" {
        "PETR2".isRightsTicker() shouldBe true
        "RBRP12".isRightsTicker() shouldBe true
        "JBSS32".isRightsTicker() shouldBe false
        "PETR4".isRightsTicker() shouldBe false
        "HGLG11".isRightsTicker() shouldBe false
    }
})
