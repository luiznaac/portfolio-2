package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.HttpMockService.configureResponses
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.bacenCDIValues
import dev.agner.portfolio.integrationTest.helpers.getIndexValues
import dev.agner.portfolio.integrationTest.helpers.getIndexes
import dev.agner.portfolio.integrationTest.helpers.hydrateIndexValues
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

@IntegrationTest
class AnaliseTest : StringSpec({

    "aaa" {
        val bacenValues = listOf(
            mapOf(
                "data" to "23/04/2024",
                "valor" to "0.123456"
            )
        )

        configureResponses {
            response { bacenCDIValues(bacenValues) }
        }

        val indx = getIndexes()
        hydrateIndexValues("CDI")
        val values = getIndexValues("CDI")

        indx.size shouldBe 3
        values.size shouldBe 1
        values.first() shouldBe mapOf(
            "date" to "2024-04-23",
            "value" to "0.12345600"
        )
    }

    "bbb" {
        val bacenValues = listOf(
            mapOf(
                "data" to "22/04/2025",
                "valor" to "0.987654"
            )
        )

        configureResponses {
            response { bacenCDIValues(bacenValues) }
        }

        val indx = getIndexes()
        hydrateIndexValues("CDI")
        val values = getIndexValues("CDI")

        indx.size shouldBe 3
        values.size shouldBe 1
        values.first() shouldBe mapOf(
            "date" to "2025-04-22",
            "value" to "0.98765400"
        )
    }
})
