package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.HttpMockService.configureResponses
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.bacenCDIValues
import dev.agner.portfolio.integrationTest.helpers.bondPositions
import dev.agner.portfolio.integrationTest.helpers.consolidateBond
import dev.agner.portfolio.integrationTest.helpers.createBondOrder
import dev.agner.portfolio.integrationTest.helpers.createFloatingBond
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.integrationTest.helpers.hydrateIndexValues
import dev.agner.portfolio.usecase.commons.brazilianLocalDateFormat
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.model.IndexValueCreation
import dev.agner.portfolio.usecase.index.repository.IIndexValueRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import java.math.BigDecimal
import java.time.Instant

@IntegrationTest
class BondTest : StringSpec({

    "single bond with multiple buys and sells" {
        every { ClockMock.clock.instant() } returns Instant.parse("2025-10-01T10:00:00Z")
        // Doing this so index values will be hydrated from this date beyond
        getBean<IIndexValueRepository>().saveAll(
            IndexId.CDI,
            listOf(
                IndexValueCreation(
                    LocalDate.parse("2025-05-29"),
                    BigDecimal.ZERO,
                )
            ),
        )

        configureResponses {
            response {
                // Too lazy to fix this now, but it should return only the values for the date range
                bacenCDIValues("2025-05-30", "2025-09-07", buildBacenValues())
                bacenCDIValues("2025-09-08", "2025-09-30", buildBacenValues())
            }
        }

        hydrateIndexValues("CDI")["count"]!! shouldBe "87"

        val bondId = createFloatingBond("102.00", "CDI")
        createBondOrder(bondId, "BUY", "2025-05-30", "4019.01")
        createBondOrder(bondId, "BUY", "2025-06-02", "28610.17")
        createBondOrder(bondId, "SELL", "2025-06-16", "10785.00")
        createBondOrder(bondId, "BUY", "2025-06-30", "4204.47")
        createBondOrder(bondId, "BUY", "2025-07-07", "1320.00")
        createBondOrder(bondId, "BUY", "2025-07-31", "3000.00")
        createBondOrder(bondId, "SELL", "2025-08-11", "2500.00")
        createBondOrder(bondId, "BUY", "2025-08-29", "2200.00")

        consolidateBond(bondId)
        val positions = bondPositions(bondId)

        positions.size shouldBe 87
        positions.last().also {
            it["date"]!! shouldBe "2025-09-30"
            it["principal"]!! shouldBe 30150.86
            it["yield"]!! shouldBe 1271.92
            it["taxes"]!! shouldBe 279.8224
            it["result"]!! shouldBe 31142.9576
        }
    }
})

private fun buildBacenValues() =
    (LocalDate.parse("2025-05-30")..LocalDate.parse("2025-06-18"))
        .filter { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) }
        .map {
            mapOf(
                "data" to it.format(brazilianLocalDateFormat),
                "valor" to "0.054266"
            )
        } +
        (LocalDate.parse("2025-06-20")..LocalDate.parse("2025-09-30"))
            .filter { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) }
            .map {
                mapOf(
                    "data" to it.format(brazilianLocalDateFormat),
                    "valor" to "0.055131"
                )
            }
