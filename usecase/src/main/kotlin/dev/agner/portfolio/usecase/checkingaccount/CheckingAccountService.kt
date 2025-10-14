package dev.agner.portfolio.usecase.checkingaccount

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.BondService
import dev.agner.portfolio.usecase.bond.model.BondCreation.FloatingRateBondCreation
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Deposit
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccount
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccountCreation
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccountMovementCreation
import dev.agner.portfolio.usecase.checkingaccount.repository.ICheckingAccountRepository
import dev.agner.portfolio.usecase.commons.toMondayIfWeekend
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import org.springframework.stereotype.Service

@Service
class CheckingAccountService(
    private val repository: ICheckingAccountRepository,
    private val bondService: BondService,
    private val bondOrderService: BondOrderService,
) {

    suspend fun create(creation: CheckingAccountCreation) = with(creation) {
        repository.save(this)
    }

    suspend fun deposit(creation: CheckingAccountMovementCreation): Deposit {
        val bond = bondService.createForCheckingAccount(
            checkingAccountId = creation.checkingAccountId,
            creation = fetchById(creation.checkingAccountId).toBondCreation(creation.date),
        )

        return bondOrderService.create(
            BondOrderCreation(
                bondId = bond.id,
                type = BondOrderType.DEPOSIT,
                date = creation.date,
                amount = creation.amount!!,
                checkingAccountId = creation.checkingAccountId,
            ),
        ) as Deposit
    }

    suspend fun withdraw(creation: CheckingAccountMovementCreation) =
        bondOrderService.create(
            BondOrderCreation(
                type = BondOrderType.WITHDRAWAL,
                date = creation.date,
                amount = creation.amount!!,
                checkingAccountId = creation.checkingAccountId,
            ),
        )

    suspend fun fullWithdraw(creation: CheckingAccountMovementCreation) =
        bondOrderService.create(
            BondOrderCreation(
                type = BondOrderType.FULL_WITHDRAWAL,
                date = creation.date,
                checkingAccountId = creation.checkingAccountId,
            ),
        )

    suspend fun fetchById(checkingAccountId: Int) = repository.fetchById(checkingAccountId)
        ?: throw IllegalArgumentException("Checking account with ID $checkingAccountId not found")

    suspend fun fetchAll() = repository.fetchAll()
}

private fun CheckingAccount.toBondCreation(date: LocalDate) = FloatingRateBondCreation(
    name = name + " [${date.yearMonth}]",
    value = value,
    maturityDate = date.plus(maturityDuration).toMondayIfWeekend(),
    indexId = indexId,
)
