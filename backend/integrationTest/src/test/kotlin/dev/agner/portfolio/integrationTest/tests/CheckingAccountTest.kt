package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.HttpMockService.configureResponses
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.bacenCDIValues
import dev.agner.portfolio.integrationTest.helpers.checkingAccountPositions
import dev.agner.portfolio.integrationTest.helpers.createCheckingAccount
import dev.agner.portfolio.integrationTest.helpers.createDeposit
import dev.agner.portfolio.integrationTest.helpers.createWithdrawal
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.integrationTest.helpers.hydrateIndexValues
import dev.agner.portfolio.integrationTest.helpers.oneTimeTask
import dev.agner.portfolio.integrationTest.helpers.scheduleConsolidations
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Deposit
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullWithdrawal
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
class CheckingAccountTest : StringSpec({

    "single checking account with multiple deposits and withdrawals" {
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

        val checkingAccountId = createCheckingAccount("102.00", "CDI", "P3Y")
        createDeposit(checkingAccountId, "2025-05-30", "4019.01")
        createDeposit(checkingAccountId, "2025-06-02", "28610.17")
        createWithdrawal(checkingAccountId, "2025-06-16", "10785.00")
        createDeposit(checkingAccountId, "2025-06-30", "4204.47")
        createDeposit(checkingAccountId, "2025-07-07", "1320.00")
        createDeposit(checkingAccountId, "2025-07-31", "3000.00")
        createWithdrawal(checkingAccountId, "2025-08-11", "2500.00")
        createDeposit(checkingAccountId, "2025-08-29", "2200.00")

        scheduleConsolidations()
        val positions = checkingAccountPositions(checkingAccountId)

        positions.size shouldBe 87
        positions.last().also {
            it["date"]!! shouldBe "2025-09-30"
            it["principal"]!! shouldBe 30150.86
            it["yield"]!! shouldBe 1271.92
            it["taxes"]!! shouldBe 279.82
        }
        scheduleConsolidations().also {
            it["CHECKING_ACCOUNT"]!! shouldBe listOf(checkingAccountId.toInt())
        }
    }

    "single deposit with maturity" {
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

        val checkingAccountId = createCheckingAccount("102.00", "CDI", "P3M")
        createDeposit(checkingAccountId, "2025-05-30", "4019.01")

        scheduleConsolidations()
        val positions = checkingAccountPositions(checkingAccountId)

        positions.size shouldBe 66
        positions.last().also {
            it["date"]!! shouldBe "2025-09-01"
            it["principal"]!! shouldBe 0.00
            it["yield"]!! shouldBe 0.00
            it["taxes"]!! shouldBe 0.00
        }

        val orders = getBean<IBondOrderRepository>().fetchByCheckingAccountId(checkingAccountId.toInt())
        orders.size shouldBe 2
        orders.firstOfInstance<Deposit>().also {
            it.amount shouldBe BigDecimal("4019.01")
            it.date shouldBe LocalDate.parse("2025-05-30")
        }
        orders.firstOfInstance<Maturity>().also {
            it.date shouldBe LocalDate.parse("2025-09-01")
        }
        scheduleConsolidations().also {
            it["CHECKING_ACCOUNT"]!!.isEmpty() shouldBe true
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

        val checkingAccountId = createCheckingAccount("102.00", "CDI", "P3Y")
        createDeposit(checkingAccountId, "2025-05-30", "4019.01")
        createWithdrawal(checkingAccountId, "2025-06-03", "5123.45")

        scheduleConsolidations()
        val positions = checkingAccountPositions(checkingAccountId)

        positions.size shouldBe 3
        positions.last().also {
            it["date"]!! shouldBe "2025-06-03"
            it["principal"]!! shouldBe 0.00
            it["yield"]!! shouldBe 0.00
            it["taxes"]!! shouldBe 0.00
        }

        val orders = getBean<IBondOrderRepository>().fetchByCheckingAccountId(checkingAccountId.toInt())
        orders.size shouldBe 2
        orders.firstOfInstance<Deposit>().also {
            it.amount shouldBe BigDecimal("4019.01")
            it.date shouldBe LocalDate.parse("2025-05-30")
        }
        orders.firstOfInstance<FullWithdrawal>().also {
            it.date shouldBe LocalDate.parse("2025-06-03")
        }
        scheduleConsolidations().also {
            it["CHECKING_ACCOUNT"]!!.isEmpty() shouldBe true
        }
    }

    "single checking account with multiple deposits - multiple consolidations" {
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

        val checkingAccountId = createCheckingAccount("102.00", "CDI", "P3Y")
        createDeposit(checkingAccountId, "2025-05-29", "1000.12")
        createDeposit(checkingAccountId, "2025-05-30", "234.00")

        // Consolidate on 2025-09-26
        every { ClockMock.clock.instant() } returns Instant.parse("2025-09-26T10:00:00Z")
        scheduleConsolidations().also {
            it["CHECKING_ACCOUNT"]!! shouldBe listOf(checkingAccountId.toInt())
        }
        checkingAccountPositions(checkingAccountId).also {
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
            it["CHECKING_ACCOUNT"]!! shouldBe listOf(checkingAccountId.toInt())
        }
        checkingAccountPositions(checkingAccountId).also {
            it.size shouldBe 87
            it.last().also {
                it["date"]!! shouldBe "2025-09-30"
                it["principal"]!! shouldBe 1234.12
                it["yield"]!! shouldBe 61.74
                it["taxes"]!! shouldBe 13.59
            }
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
