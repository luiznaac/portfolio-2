package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.bond.repository.IBondOrderRepository
import dev.agner.portfolio.usecase.floatingRateBond
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class BondOrderServiceTest : StringSpec({

    val repository = mockk<IBondOrderRepository>()
    val service = BondOrderService(repository)

    beforeTest {
        clearAllMocks()
    }

    "should reject a second sell for the same bond on the same date" {
        val bond = floatingRateBond()
        val date = LocalDate.parse("2025-03-10")
        coEvery { repository.fetchByBondId(bond.id) } returns listOf(
            BondOrder.Redemption.Sell(
                id = 1,
                date = date,
                amount = BigDecimal("100.00"),
                bond = bond,
            ),
        )

        shouldThrow<DuplicateRedemptionException> {
            service.create(
                BondOrderCreation(
                    bondId = bond.id,
                    type = BondOrderType.SELL,
                    date = date,
                    amount = BigDecimal("200.00"),
                ),
            )
        }

        coVerify(exactly = 0) { repository.save(any()) }
    }

    "should allow a sell for the same bond on a different date" {
        val bond = floatingRateBond()
        val creationDate = LocalDate.parse("2025-03-10")
        val creation = BondOrderCreation(
            bondId = bond.id,
            type = BondOrderType.SELL,
            date = creationDate,
            amount = BigDecimal("200.00"),
        )
        val savedSell = BondOrder.Redemption.Sell(
            id = 2,
            date = creationDate,
            amount = BigDecimal("200.00"),
            bond = bond,
        )
        coEvery { repository.fetchByBondId(bond.id) } returns listOf(
            BondOrder.Redemption.Sell(
                id = 1,
                date = LocalDate.parse("2025-03-09"),
                amount = BigDecimal("100.00"),
                bond = bond,
            ),
        )
        coEvery { repository.save(creation) } returns savedSell

        service.create(creation) shouldBe savedSell

        coVerify(exactly = 1) { repository.save(creation) }
    }

    "should reject a second withdrawal for the same checking account on the same date" {
        val checkingAccountId = 7
        val date = LocalDate.parse("2025-03-10")
        coEvery { repository.fetchByCheckingAccountId(checkingAccountId) } returns listOf(
            BondOrder.Redemption.Withdrawal(
                id = 1,
                date = date,
                amount = BigDecimal("100.00"),
                checkingAccountId = checkingAccountId,
            ),
        )

        shouldThrow<DuplicateRedemptionException> {
            service.create(
                BondOrderCreation(
                    type = BondOrderType.WITHDRAWAL,
                    date = date,
                    amount = BigDecimal("200.00"),
                    checkingAccountId = checkingAccountId,
                ),
            )
        }

        coVerify(exactly = 0) { repository.save(any()) }
    }

    "should allow a withdrawal from a different checking account on the same date" {
        val otherCheckingAccountId = 7
        val targetCheckingAccountId = 8
        val date = LocalDate.parse("2025-03-10")
        val creation = BondOrderCreation(
            type = BondOrderType.WITHDRAWAL,
            date = date,
            amount = BigDecimal("200.00"),
            checkingAccountId = targetCheckingAccountId,
        )
        val savedWithdrawal = BondOrder.Redemption.Withdrawal(
            id = 2,
            date = date,
            amount = BigDecimal("200.00"),
            checkingAccountId = targetCheckingAccountId,
        )
        coEvery { repository.fetchByCheckingAccountId(otherCheckingAccountId) } returns listOf(
            BondOrder.Redemption.Withdrawal(
                id = 1,
                date = date,
                amount = BigDecimal("100.00"),
                checkingAccountId = otherCheckingAccountId,
            ),
        )
        coEvery { repository.fetchByCheckingAccountId(targetCheckingAccountId) } returns emptyList()
        coEvery { repository.save(creation) } returns savedWithdrawal

        service.create(creation) shouldBe savedWithdrawal

        coVerify(exactly = 1) { repository.fetchByCheckingAccountId(targetCheckingAccountId) }
        coVerify(exactly = 1) { repository.save(creation) }
    }

    "should not treat a full redemption as a duplicate redemption" {
        val bond = floatingRateBond()
        val date = LocalDate.parse("2025-03-10")
        val creation = BondOrderCreation(
            bondId = bond.id,
            type = BondOrderType.SELL,
            date = date,
            amount = BigDecimal("200.00"),
        )
        val savedSell = BondOrder.Redemption.Sell(
            id = 2,
            date = date,
            amount = BigDecimal("200.00"),
            bond = bond,
        )
        coEvery { repository.fetchByBondId(bond.id) } returns listOf(
            BondOrder.DownToZero.FullRedemption(
                id = 1,
                date = date,
                bond = bond,
            ),
        )
        coEvery { repository.save(creation) } returns savedSell

        service.create(creation) shouldBe savedSell

        coVerify(exactly = 1) { repository.save(creation) }
    }

    "should keep rejecting a full redemption that carries an amount" {
        val bond = floatingRateBond()

        shouldThrow<IllegalArgumentException> {
            service.create(
                BondOrderCreation(
                    bondId = bond.id,
                    type = BondOrderType.FULL_REDEMPTION,
                    date = LocalDate.parse("2025-03-10"),
                    amount = BigDecimal("100.00"),
                ),
            )
        }

        coVerify(exactly = 0) { repository.fetchByBondId(any()) }
        coVerify(exactly = 0) { repository.save(any()) }
    }

    "should keep rejecting an external maturity" {
        val bond = floatingRateBond()

        shouldThrow<IllegalArgumentException> {
            service.create(
                BondOrderCreation(
                    bondId = bond.id,
                    type = BondOrderType.MATURITY,
                    date = LocalDate.parse("2025-03-10"),
                ),
            )
        }

        coVerify(exactly = 0) { repository.fetchByBondId(any()) }
        coVerify(exactly = 0) { repository.save(any()) }
    }
})
