package dev.agner.portfolio.usecase.checkingaccount

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.BondConsolidationService
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Deposit
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullWithdrawal
import dev.agner.portfolio.usecase.bond.model.BondOrder.Redemption.Withdrawal
import dev.agner.portfolio.usecase.bond.position.BondPositionService
import dev.agner.portfolio.usecase.checkingaccount.repository.ICheckingAccountRepository
import dev.agner.portfolio.usecase.floatingRateBond
import io.kotest.core.spec.style.StringSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class CheckingAccountConsolidatorTest : StringSpec({

    val bondOrderService = mockk<BondOrderService>()
    val repository = mockk<ICheckingAccountRepository>()
    val consolidationService = mockk<BondConsolidationService>()
    val positionService = mockk<BondPositionService>(relaxUnitFun = true)

    val consolidator = CheckingAccountConsolidator(
        repository,
        bondOrderService,
        consolidationService,
        positionService,
    )

    beforeEach {
        clearAllMocks()
    }

    "should consolidate checking account with deposits and withdrawals" {
        val checkingAccountId = 1
        val bond = floatingRateBond()
        val depositDate1 = LocalDate.parse("2024-01-01")
        val depositDate2 = LocalDate.parse("2024-01-15")
        val withdrawalDate = LocalDate.parse("2024-02-01")

        val deposit1 = Deposit(
            id = 10,
            bond = bond,
            date = depositDate1,
            amount = BigDecimal("5000.00"),
            checkingAccountId = checkingAccountId,
        )

        val deposit2 = Deposit(
            id = 11,
            bond = bond,
            date = depositDate2,
            amount = BigDecimal("3000.00"),
            checkingAccountId = checkingAccountId,
        )

        val withdrawal = Withdrawal(
            id = 20,
            date = withdrawalDate,
            amount = BigDecimal("1000.00"),
            checkingAccountId = checkingAccountId,
        )

        coEvery { bondOrderService.fetchByCheckingAccountId(checkingAccountId) } returns
            listOf(deposit1, withdrawal, deposit2)
        coEvery { repository.fetchAlreadyConsolidatedWithdrawalsIds(checkingAccountId) } returns emptySet()
        coEvery { repository.fetchAlreadyRedeemedDepositIds(checkingAccountId) } returns emptySet()
        coEvery { consolidationService.consolidate(any(), any(), any()) } returns emptyList()

        consolidator.consolidate(consolidator.buildContext(checkingAccountId))

        coVerify(exactly = 1) { bondOrderService.fetchByCheckingAccountId(checkingAccountId) }
        coVerify(exactly = 1) { repository.fetchAlreadyConsolidatedWithdrawalsIds(checkingAccountId) }
        coVerify(exactly = 1) { repository.fetchAlreadyRedeemedDepositIds(checkingAccountId) }
        coVerify(exactly = 1) {
            consolidationService.consolidate(
                contribution = listOf(deposit1, deposit2),
                redemption = listOf(withdrawal),
                downToZero = null,
            )
        }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(deposit1, deposit2)) }
    }

    "should filter out already consolidated withdrawals" {
        val checkingAccountId = 3

        val withdrawal1 = Withdrawal(
            id = 30,
            date = LocalDate.parse("2024-02-01"),
            amount = BigDecimal("500.00"),
            checkingAccountId = checkingAccountId,
        )

        val withdrawal2 = Withdrawal(
            id = 31,
            date = LocalDate.parse("2024-02-15"),
            amount = BigDecimal("700.00"),
            checkingAccountId = checkingAccountId,
        )

        val alreadyConsolidatedWithdrawals = setOf(30) // First withdrawal already consolidated

        coEvery { bondOrderService.fetchByCheckingAccountId(checkingAccountId) } returns
            listOf(withdrawal1, withdrawal2)
        coEvery { repository.fetchAlreadyConsolidatedWithdrawalsIds(checkingAccountId) } returns
            alreadyConsolidatedWithdrawals
        coEvery { repository.fetchAlreadyRedeemedDepositIds(checkingAccountId) } returns emptySet()
        coEvery { consolidationService.consolidate(any(), any(), any()) } returns emptyList()

        consolidator.consolidate(consolidator.buildContext(checkingAccountId))

        coVerify(exactly = 1) {
            consolidationService.consolidate(
                contribution = emptyList(),
                redemption = listOf(withdrawal2), // Only non-consolidated withdrawal
                downToZero = null,
            )
        }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), emptyList()) }
    }

    "should filter out already redeemed deposits" {
        val checkingAccountId = 4
        val bond = floatingRateBond()

        val deposit1 = Deposit(
            id = 40,
            bond = bond,
            date = LocalDate.parse("2024-01-01"),
            amount = BigDecimal("5000.00"),
            checkingAccountId = checkingAccountId,
        )

        val deposit2 = Deposit(
            id = 41,
            bond = bond,
            date = LocalDate.parse("2024-01-15"),
            amount = BigDecimal("3000.00"),
            checkingAccountId = checkingAccountId,
        )

        val alreadyRedeemedDeposits = setOf(40) // First deposit already redeemed

        coEvery { bondOrderService.fetchByCheckingAccountId(checkingAccountId) } returns listOf(deposit1, deposit2)
        coEvery { repository.fetchAlreadyConsolidatedWithdrawalsIds(checkingAccountId) } returns emptySet()
        coEvery { repository.fetchAlreadyRedeemedDepositIds(checkingAccountId) } returns alreadyRedeemedDeposits
        coEvery { consolidationService.consolidate(any(), any(), any()) } returns emptyList()

        consolidator.consolidate(consolidator.buildContext(checkingAccountId))

        coVerify(exactly = 1) {
            consolidationService.consolidate(
                contribution = listOf(deposit2), // Only non-redeemed deposit
                redemption = emptyList(),
                downToZero = null,
            )
        }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(deposit2)) }
    }

    "should handle full withdrawal order" {
        val checkingAccountId = 5
        val bond = floatingRateBond()
        val fullWithdrawalDate = LocalDate.parse("2024-03-01")

        val deposit = Deposit(
            id = 50,
            bond = bond,
            date = LocalDate.parse("2024-01-01"),
            amount = BigDecimal("10000.00"),
            checkingAccountId = checkingAccountId,
        )

        val fullWithdrawal = FullWithdrawal(
            id = 51,
            date = fullWithdrawalDate,
            checkingAccountId = checkingAccountId,
        )

        coEvery { bondOrderService.fetchByCheckingAccountId(checkingAccountId) } returns listOf(deposit, fullWithdrawal)
        coEvery { repository.fetchAlreadyConsolidatedWithdrawalsIds(checkingAccountId) } returns emptySet()
        coEvery { repository.fetchAlreadyRedeemedDepositIds(checkingAccountId) } returns emptySet()
        coEvery { consolidationService.consolidate(any(), any(), any()) } returns emptyList()

        consolidator.consolidate(consolidator.buildContext(checkingAccountId))

        coVerify(exactly = 1) {
            consolidationService.consolidate(
                contribution = listOf(deposit),
                redemption = emptyList(),
                downToZero = fullWithdrawal,
            )
        }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(deposit)) }
    }

    "should filter both deposits and withdrawals when both have already been processed" {
        val checkingAccountId = 6
        val bond = floatingRateBond()

        val deposit1 = Deposit(
            id = 60,
            bond = bond,
            date = LocalDate.parse("2024-01-01"),
            amount = BigDecimal("5000.00"),
            checkingAccountId = checkingAccountId,
        )

        val deposit2 = Deposit(
            id = 61,
            bond = bond,
            date = LocalDate.parse("2024-01-15"),
            amount = BigDecimal("3000.00"),
            checkingAccountId = checkingAccountId,
        )

        val withdrawal1 = Withdrawal(
            id = 62,
            date = LocalDate.parse("2024-02-01"),
            amount = BigDecimal("1000.00"),
            checkingAccountId = checkingAccountId,
        )

        val withdrawal2 = Withdrawal(
            id = 63,
            date = LocalDate.parse("2024-02-15"),
            amount = BigDecimal("500.00"),
            checkingAccountId = checkingAccountId,
        )

        val alreadyRedeemedDeposits = setOf(60)
        val alreadyConsolidatedWithdrawals = setOf(62)

        coEvery { bondOrderService.fetchByCheckingAccountId(checkingAccountId) } returns
            listOf(deposit1, deposit2, withdrawal1, withdrawal2)
        coEvery { repository.fetchAlreadyConsolidatedWithdrawalsIds(checkingAccountId) } returns
            alreadyConsolidatedWithdrawals
        coEvery { repository.fetchAlreadyRedeemedDepositIds(checkingAccountId) } returns alreadyRedeemedDeposits
        coEvery { consolidationService.consolidate(any(), any(), any()) } returns emptyList()

        consolidator.consolidate(consolidator.buildContext(checkingAccountId))

        coVerify(exactly = 1) {
            consolidationService.consolidate(
                contribution = listOf(deposit2),
                redemption = listOf(withdrawal2),
                downToZero = null,
            )
        }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), listOf(deposit2)) }
    }

    "should handle empty orders list" {
        val checkingAccountId = 7

        coEvery { bondOrderService.fetchByCheckingAccountId(checkingAccountId) } returns emptyList()
        coEvery { repository.fetchAlreadyConsolidatedWithdrawalsIds(checkingAccountId) } returns emptySet()
        coEvery { repository.fetchAlreadyRedeemedDepositIds(checkingAccountId) } returns emptySet()
        coEvery { consolidationService.consolidate(any(), any(), any()) } returns emptyList()

        consolidator.consolidate(consolidator.buildContext(checkingAccountId))

        coVerify(exactly = 1) {
            consolidationService.consolidate(
                contribution = emptyList(),
                redemption = emptyList(),
                downToZero = null,
            )
        }
        coVerify(exactly = 1) { positionService.consolidatePositions(emptyList(), emptyList()) }
    }
})
