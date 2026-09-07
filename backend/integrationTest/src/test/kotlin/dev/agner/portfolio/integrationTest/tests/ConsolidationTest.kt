package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.HttpMockService.configureResponses
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.bacenCDIValues
import dev.agner.portfolio.integrationTest.helpers.bondPositions
import dev.agner.portfolio.integrationTest.helpers.checkingAccountPositions
import dev.agner.portfolio.integrationTest.helpers.createBondOrder
import dev.agner.portfolio.integrationTest.helpers.createCheckingAccount
import dev.agner.portfolio.integrationTest.helpers.createDeposit
import dev.agner.portfolio.integrationTest.helpers.createFloatingBond
import dev.agner.portfolio.integrationTest.helpers.createFullWithdrawal
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.integrationTest.helpers.hydrateIndexValues
import dev.agner.portfolio.integrationTest.helpers.oneTimeTask
import dev.agner.portfolio.integrationTest.helpers.scheduleConsolidations
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
class ConsolidationTest : StringSpec({

    "multiple products with multiple characteristics" {
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

        val checkingAccountId_withDeposit = checkingAccountWithDeposit()
        val checkingAccountId_withMaturity = checkingAccountWithMaturity()
        val checkingAccountId_withFullWithdraw = checkingAccountWithFullWithdraw()

        val bondId_withBuy = bondWithBuy()
        val bondId_withMaturity = bondWithMaturity()
        val bondId_withFullRedemption = bondWithFullRedemption()

        scheduleConsolidations().also {
            it["BOND"]!! shouldBe listOf(
                bondId_withBuy.toInt(),
                bondId_withMaturity.toInt(),
                bondId_withFullRedemption.toInt(),
            )

            it["CHECKING_ACCOUNT"]!! shouldBe listOf(
                checkingAccountId_withDeposit.toInt(),
                checkingAccountId_withMaturity.toInt(),
                checkingAccountId_withFullWithdraw.toInt(),
            )
        }

        bondPositions(bondId_withBuy).also { positions ->
            positions.size shouldBe 87
            positions.last().also {
                it["date"]!! shouldBe "2025-09-30"
                it["principal"]!! shouldBe 4019.01
                it["yield"]!! shouldBe 200.95
                it["taxes"]!! shouldBe 44.21
            }
        }
        bondPositions(bondId_withMaturity).also { positions ->
            positions.size shouldBe 66
            positions.last().also {
                it["date"]!! shouldBe "2025-09-01"
                it["principal"]!! shouldBe 0.0
                it["yield"]!! shouldBe 0.0
                it["taxes"]!! shouldBe 0.0
            }
        }
        bondPositions(bondId_withFullRedemption).also { positions ->
            positions.size shouldBe 3
            positions.last().also {
                it["date"]!! shouldBe "2025-06-03"
                it["principal"]!! shouldBe 0.0
                it["yield"]!! shouldBe 0.0
                it["taxes"]!! shouldBe 0.0
            }
        }

        checkingAccountPositions(checkingAccountId_withDeposit).also { positions ->
            positions.size shouldBe 87
            positions.last().also {
                it["date"]!! shouldBe "2025-09-30"
                it["principal"]!! shouldBe 4019.01
                it["yield"]!! shouldBe 200.95
                it["taxes"]!! shouldBe 44.21
            }
        }
        checkingAccountPositions(checkingAccountId_withMaturity).also { positions ->
            positions.size shouldBe 66
            positions.last().also {
                it["date"]!! shouldBe "2025-09-01"
                it["principal"]!! shouldBe 0.0
                it["yield"]!! shouldBe 0.0
                it["taxes"]!! shouldBe 0.0
            }
        }
        checkingAccountPositions(checkingAccountId_withFullWithdraw).also { positions ->
            positions.size shouldBe 3
            positions.last().also {
                it["date"]!! shouldBe "2025-06-03"
                it["principal"]!! shouldBe 0.0
                it["yield"]!! shouldBe 0.0
                it["taxes"]!! shouldBe 0.0
            }
        }

        scheduleConsolidations().also {
            it["BOND"]!! shouldBe listOf(bondId_withBuy.toInt())
            it["CHECKING_ACCOUNT"]!! shouldBe listOf(checkingAccountId_withDeposit.toInt())
        }
    }
})

private suspend fun checkingAccountWithDeposit(): String {
    val checkingAccountId = createCheckingAccount("102.00", "CDI", "P3Y")
    createDeposit(checkingAccountId, "2025-05-30", "4019.01")
    return checkingAccountId
}

private suspend fun checkingAccountWithMaturity(): String {
    val checkingAccountId = createCheckingAccount("102.00", "CDI", "P3M")
    createDeposit(checkingAccountId, "2025-05-30", "4019.01")
    return checkingAccountId
}

private suspend fun checkingAccountWithFullWithdraw(): String {
    val checkingAccountId = createCheckingAccount("102.00", "CDI", "P3M")
    createDeposit(checkingAccountId, "2025-05-30", "4019.01")
    createFullWithdrawal(checkingAccountId, "2025-06-03")
    return checkingAccountId
}

private suspend fun bondWithBuy(): String {
    val bondId = createFloatingBond("102.00", "CDI")
    createBondOrder(bondId, "BUY", "2025-05-30", "4019.01")
    return bondId
}

private suspend fun bondWithMaturity(): String {
    val bondId = createFloatingBond("102.00", "CDI", "2025-09-01")
    createBondOrder(bondId, "BUY", "2025-05-30", "4019.01")
    return bondId
}

private suspend fun bondWithFullRedemption(): String {
    val bondId = createFloatingBond("102.00", "CDI")
    createBondOrder(bondId, "BUY", "2025-05-30", "4019.01")
    createBondOrder(bondId, "FULL_REDEMPTION", "2025-06-03")
    return bondId
}

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
