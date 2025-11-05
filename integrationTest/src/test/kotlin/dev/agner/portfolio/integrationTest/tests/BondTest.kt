package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.HttpMockService.configureResponses
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.bacenCDIValues
import dev.agner.portfolio.integrationTest.helpers.bondPositions
import dev.agner.portfolio.integrationTest.helpers.createBondOrder
import dev.agner.portfolio.integrationTest.helpers.createFixedBond
import dev.agner.portfolio.integrationTest.helpers.createFloatingBond
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.integrationTest.helpers.hydrateIndexValues
import dev.agner.portfolio.integrationTest.helpers.oneTimeTask
import dev.agner.portfolio.integrationTest.helpers.scheduleConsolidations
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Buy
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullRedemption
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.Maturity
import dev.agner.portfolio.usecase.bond.repository.IBondOrderRepository
import dev.agner.portfolio.usecase.commons.brazilianLocalDateFormat
import dev.agner.portfolio.usecase.commons.firstOfInstance
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
                    BigDecimal("0.00"),
                ),
            ),
        )

        configureResponses {
            response { bacenCDIValues("2025-09-08", "2025-09-30", buildBacenValues()) }
            response { oneTimeTask() }
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

        scheduleConsolidations()
        val positions = bondPositions(bondId)

        positions.size shouldBe 87
        positions.last().also {
            it["date"]!! shouldBe "2025-09-30"
            it["principal"]!! shouldBe 30150.86
            it["yield"]!! shouldBe 1271.92
            it["taxes"]!! shouldBe 279.82
        }
        scheduleConsolidations().also {
            it["BOND"]!! shouldBe listOf(bondId.toInt())
        }
    }

    "single bond with maturity" {
        every { ClockMock.clock.instant() } returns Instant.parse("2025-09-08T10:00:00Z")
        // Doing this so index values will be hydrated from this date beyond
        getBean<IIndexValueRepository>().saveAll(
            IndexId.CDI,
            listOf(
                IndexValueCreation(
                    LocalDate.parse("2025-05-29"),
                    BigDecimal("0.00"),
                ),
            ),
        )

        configureResponses {
            response { bacenCDIValues("2025-05-30", "2025-09-07", buildBacenValues()) }
            response { oneTimeTask() }
        }

        hydrateIndexValues("CDI")["count"]!! shouldBe "87"

        val bondId = createFloatingBond("102.00", "CDI", "2025-09-01")
        createBondOrder(bondId, "BUY", "2025-05-30", "4019.01")

        scheduleConsolidations()
        val positions = bondPositions(bondId)

        positions.size shouldBe 66
        positions.last().also {
            it["date"]!! shouldBe "2025-09-01"
            it["principal"]!! shouldBe 0.00
            it["yield"]!! shouldBe 0.00
            it["taxes"]!! shouldBe 0.00
        }

        val orders = getBean<IBondOrderRepository>().fetchByBondId(bondId.toInt())
        orders.size shouldBe 2
        orders.firstOfInstance<Buy>().also {
            it.amount shouldBe BigDecimal("4019.01")
            it.date shouldBe LocalDate.parse("2025-05-30")
        }
        orders.firstOfInstance<Maturity>().also {
            it.date shouldBe LocalDate.parse("2025-09-01")
        }
        scheduleConsolidations().also {
            it["BOND"]!!.isEmpty() shouldBe true
        }
    }

    "single bond with sell order that turns into full redemption" {
        every { ClockMock.clock.instant() } returns Instant.parse("2025-09-08T10:00:00Z")
        // Doing this so index values will be hydrated from this date beyond
        getBean<IIndexValueRepository>().saveAll(
            IndexId.CDI,
            listOf(
                IndexValueCreation(
                    LocalDate.parse("2025-05-29"),
                    BigDecimal("0.00"),
                ),
            ),
        )

        configureResponses {
            response { bacenCDIValues("2025-05-30", "2025-09-07", buildBacenValues()) }
            response { oneTimeTask() }
        }

        hydrateIndexValues("CDI")["count"]!! shouldBe "87"

        val bondId = createFloatingBond("102.00", "CDI")
        createBondOrder(bondId, "BUY", "2025-05-30", "4019.01")
        createBondOrder(bondId, "SELL", "2025-06-03", "5123.45")

        scheduleConsolidations()
        val positions = bondPositions(bondId)

        positions.size shouldBe 3
        positions.last().also {
            it["date"]!! shouldBe "2025-06-03"
            it["principal"]!! shouldBe 0.00
            it["yield"]!! shouldBe 0.00
            it["taxes"]!! shouldBe 0.00
        }

        val orders = getBean<IBondOrderRepository>().fetchByBondId(bondId.toInt())
        orders.size shouldBe 2
        orders.firstOfInstance<Buy>().also {
            it.amount shouldBe BigDecimal("4019.01")
            it.date shouldBe LocalDate.parse("2025-05-30")
        }
        orders.firstOfInstance<FullRedemption>().also {
            it.date shouldBe LocalDate.parse("2025-06-03")
        }

        scheduleConsolidations().also {
            it["BOND"]!!.isEmpty() shouldBe true
        }
    }

    "single bond with multiple buys - multiple consolidations" {
        every { ClockMock.clock.instant() } returns Instant.parse("2025-10-01T10:00:00Z")
        // Doing this so index values will be hydrated from this date beyond
        getBean<IIndexValueRepository>().saveAll(
            IndexId.CDI,
            listOf(
                IndexValueCreation(
                    LocalDate.parse("2025-05-29"),
                    BigDecimal("0.00"),
                ),
            ),
        )

        configureResponses {
            response { bacenCDIValues("2025-09-08", "2025-09-30", buildBacenValues()) }
            response { oneTimeTask() }
        }

        hydrateIndexValues("CDI")["count"]!! shouldBe "87"

        val bondId = createFloatingBond("102.00", "CDI")
        createBondOrder(bondId, "BUY", "2025-05-29", "1000.12")
        createBondOrder(bondId, "BUY", "2025-05-30", "234.00")

        // Consolidate on 2025-09-26
        every { ClockMock.clock.instant() } returns Instant.parse("2025-09-26T10:00:00Z")
        scheduleConsolidations().also {
            it["BOND"]!! shouldBe listOf(bondId.toInt())
        }
        bondPositions(bondId).also {
            it.size shouldBe 84
            it.last().also {
                it["date"]!! shouldBe "2025-09-25"
                it["principal"]!! shouldBe 1234.12
                it["yield"]!! shouldBe 59.55
                it["taxes"]!! shouldBe 13.1
            }
        }

        // Consolidate a few day later, on 2025-10-01
        every { ClockMock.clock.instant() } returns Instant.parse("2025-10-01T10:00:00Z")
        scheduleConsolidations().also {
            it["BOND"]!! shouldBe listOf(bondId.toInt())
        }
        bondPositions(bondId).also {
            it.size shouldBe 87
            it.last().also {
                it["date"]!! shouldBe "2025-09-30"
                it["principal"]!! shouldBe 1234.12
                it["yield"]!! shouldBe 61.74
                it["taxes"]!! shouldBe 13.59
            }
        }
    }

    "fixed rate bond" {
        every { ClockMock.clock.instant() } returns Instant.parse("2025-05-31T10:00:00Z")

        configureResponses {
            response { oneTimeTask() }
        }

        val bondId = createFixedBond("12.50", "2025-11-11")
        createBondOrder(bondId, "BUY", "2024-05-30", "1000.00")

        scheduleConsolidations()
        val positions = bondPositions(bondId)

        positions.size shouldBe 262
        positions.last().also {
            it["date"]!! shouldBe "2025-05-30"
            it["principal"]!! shouldBe 1000.00
            it["yield"]!! shouldBe 125.21
            it["taxes"]!! shouldBe 22.54
        }
        scheduleConsolidations().also {
            it["BOND"]!! shouldBe listOf(bondId.toInt())
        }
    }
})

private fun buildBacenValues() =
    (LocalDate.parse("2025-05-30")..LocalDate.parse("2025-06-18"))
        .filter { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) }
        .map {
            mapOf(
                "data" to it.format(brazilianLocalDateFormat),
                "valor" to "0.054266",
            )
        } +
        (LocalDate.parse("2025-06-20")..LocalDate.parse("2025-09-30"))
            .filter { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) }
            .map {
                mapOf(
                    "data" to it.format(brazilianLocalDateFormat),
                    "valor" to "0.055131",
                )
            }
