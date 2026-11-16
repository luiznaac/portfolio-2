package dev.agner.portfolio.usecase.broker.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

// The alias lookup key: broker notes print an asset description rather than a ticker, and the same
// paper arrives with a B3 class/marker suffix ("FII BCFF11 CI", "ITAUUNIBANCO PN N1"). Stripping
// the markers makes the description a stable key, so a later note with the same asset resolves
// without a new alias.
class BrokerDescriptionNormalizationTest : StringSpec({

    "strips the B3 marker tokens and collapses whitespace" {
        "FII BCFF11 CI".normalizedBrokerDescription() shouldBe "FII BCFF11"
        "FII KNRI11 ER".normalizedBrokerDescription() shouldBe "FII KNRI11"
        "ITAUUNIBANCO PN N1".normalizedBrokerDescription() shouldBe "ITAUUNIBANCO PN"
        "ENGIE BRASIL ON NM".normalizedBrokerDescription() shouldBe "ENGIE BRASIL ON"
        "FLEURY ON NM".normalizedBrokerDescription() shouldBe "FLEURY ON"
    }

    "strips the hash markers, attached or apart" {
        "PETR4 #".normalizedBrokerDescription() shouldBe "PETR4"
        "PETR4 #2".normalizedBrokerDescription() shouldBe "PETR4"
        "PETR4#2".normalizedBrokerDescription() shouldBe "PETR4"
    }

    "strips every listed marker, including the multi-letter ones" {
        "ALUP11 ED".normalizedBrokerDescription() shouldBe "ALUP11"
        "ALUP11 EDJ".normalizedBrokerDescription() shouldBe "ALUP11"
        "ALUP11 EJ".normalizedBrokerDescription() shouldBe "ALUP11"
        "ALUP11 N2".normalizedBrokerDescription() shouldBe "ALUP11"
    }

    "uppercases and trims the raw description" {
        "  fii   bcff11   ci ".normalizedBrokerDescription() shouldBe "FII BCFF11"
    }

    "keeps a description that carries no marker" {
        "ITSA4".normalizedBrokerDescription() shouldBe "ITSA4"
        "FII RBR PROP DM 88,27".normalizedBrokerDescription() shouldBe "FII RBR PROP DM 88,27"
    }

    "does not strip a marker that is only a substring of a word" {
        "CIA ENERGETICA ON".normalizedBrokerDescription() shouldBe "CIA ENERGETICA ON"
    }
})
