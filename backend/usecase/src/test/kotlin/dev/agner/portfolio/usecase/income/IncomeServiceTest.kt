package dev.agner.portfolio.usecase.income

import dev.agner.portfolio.usecase.income.model.Income
import dev.agner.portfolio.usecase.income.model.IncomeCreation
import dev.agner.portfolio.usecase.income.model.IncomeQuery
import dev.agner.portfolio.usecase.income.model.IncomeStatus
import dev.agner.portfolio.usecase.income.model.IncomeType
import dev.agner.portfolio.usecase.income.model.Provenance
import dev.agner.portfolio.usecase.income.model.TaxNature
import dev.agner.portfolio.usecase.income.repository.IIncomeRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val CLOCK = Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC)

class IncomeServiceTest : StringSpec({
    val repository = mockk<IIncomeRepository>()
    val service = IncomeService(repository, CLOCK)

    beforeTest { clearAllMocks() }

    "fetchAll derives the status of each movement from its payable date" {
        coEvery { repository.fetchAll() } returns listOf(
            income(id = 1, date = "2026-11-20", status = IncomeStatus.ANNOUNCED),
            income(id = 2, date = "2026-11-05", status = IncomeStatus.ANNOUNCED),
            income(id = 3, date = "2026-11-01", status = IncomeStatus.ANNOUNCED),
            income(id = 4, date = "2026-11-20", status = IncomeStatus.RECONCILED),
        )

        service.fetchAll().map { it.status } shouldBe listOf(
            IncomeStatus.ANNOUNCED,
            IncomeStatus.PAYABLE,
            IncomeStatus.RECEIVED,
            IncomeStatus.RECONCILED,
        )
    }

    "fetchAll applies an inclusive window boundary on the payable date" {
        coEvery { repository.fetchAll() } returns listOf(
            income(id = 1, date = "2026-10-31", status = IncomeStatus.RECEIVED),
            income(id = 2, date = "2026-11-01", status = IncomeStatus.RECEIVED),
            income(id = 3, date = "2026-11-30", status = IncomeStatus.RECEIVED),
            income(id = 4, date = "2026-12-01", status = IncomeStatus.RECEIVED),
        )

        val window = service.fetchAll(
            IncomeQuery(
                from = LocalDate.parse("2026-11-01"),
                to = LocalDate.parse("2026-11-30"),
            ),
        )

        window.map { it.id } shouldBe listOf(2, 3)
    }

    "fetchAll treats the ticker as a search term, not a canonical identity" {
        coEvery { repository.fetchAll() } returns listOf(
            income(id = 1, date = "2026-11-20", status = IncomeStatus.RECEIVED, ticker = "PETR4"),
            income(id = 2, date = "2026-11-20", status = IncomeStatus.RECEIVED, ticker = "VALE3"),
            income(id = 3, date = "2026-11-20", status = IncomeStatus.RECEIVED, ticker = "FGIN11"),
        )

        // A one-letter search must narrow, not normalize to an empty query that matches everything.
        service.fetchAll(IncomeQuery(ticker = "f")).map { it.id } shouldBe listOf(3)
    }

    "fetchAll narrows the ticker as the filter bar's case-insensitive substring" {
        coEvery { repository.fetchAll() } returns listOf(
            income(id = 1, date = "2026-11-20", status = IncomeStatus.RECEIVED, ticker = "PETR4"),
            income(id = 2, date = "2026-11-20", status = IncomeStatus.RECEIVED, ticker = "PETR3"),
            income(id = 3, date = "2026-11-20", status = IncomeStatus.RECEIVED, ticker = "VALE3"),
        )

        service.fetchAll(IncomeQuery(ticker = "petr")).map { it.id } shouldBe listOf(1, 2)
    }

    "fetchAll filters on the derived lifecycle status, not the stored one" {
        coEvery { repository.fetchAll() } returns listOf(
            income(id = 1, date = "2026-11-20", status = IncomeStatus.ANNOUNCED),
            income(id = 2, date = "2026-11-05", status = IncomeStatus.ANNOUNCED),
            income(id = 3, date = "2026-10-01", status = IncomeStatus.ANNOUNCED),
        )

        service.fetchAll(IncomeQuery(status = IncomeStatus.PAYABLE)).map { it.id } shouldBe listOf(2)
    }

    "fetchUpcoming keeps only the movements not yet received" {
        coEvery { repository.fetchAll() } returns listOf(
            income(id = 1, date = "2026-11-20", status = IncomeStatus.ANNOUNCED),
            income(id = 2, date = "2026-11-05", status = IncomeStatus.ANNOUNCED),
            income(id = 3, date = "2026-11-01", status = IncomeStatus.ANNOUNCED),
            income(id = 4, date = "2026-11-20", status = IncomeStatus.RECONCILED),
        )

        service.fetchUpcoming().map { it.id } shouldBe listOf(1, 2)
    }

    "importEvents stores only the movements the ledger does not already hold" {
        coEvery { repository.fetchAll() } returns listOf(
            income(
                id = 1,
                date = "2026-11-20",
                status = IncomeStatus.ANNOUNCED,
                ticker = "PETR4",
                type = IncomeType.DIVIDEND,
            ),
        )
        val saved = slot<List<IncomeCreation>>()
        coEvery { repository.saveAll(capture(saved)) } returns emptyList()

        service.importEvents(
            listOf(
                creation(ticker = "PETR4", date = "2026-11-20", type = IncomeType.DIVIDEND),
                creation(ticker = "VALE3", date = "2026-11-21", type = IncomeType.JCP),
            ),
        )

        coVerify(exactly = 1) { repository.saveAll(any()) }
        saved.captured.map { it.ticker } shouldBe listOf("VALE3")
    }

    "importEvents normalizes the tickers before storing" {
        coEvery { repository.fetchAll() } returns emptyList()
        val saved = slot<List<IncomeCreation>>()
        coEvery { repository.saveAll(capture(saved)) } returns emptyList()

        service.importEvents(listOf(creation(ticker = "ALUP11F")))

        saved.captured.single().ticker shouldBe "ALUP11"
    }

    "importEvents collapses duplicates within one batch" {
        coEvery { repository.fetchAll() } returns emptyList()
        val saved = slot<List<IncomeCreation>>()
        coEvery { repository.saveAll(capture(saved)) } returns emptyList()

        service.importEvents(
            listOf(
                creation(ticker = "PETR4", date = "2026-11-20", type = IncomeType.DIVIDEND),
                creation(ticker = "PETR4", date = "2026-11-20", type = IncomeType.DIVIDEND),
            ),
        )

        saved.captured.size shouldBe 1
    }

    "importEvents returns the stored movements with their derived status" {
        coEvery { repository.fetchAll() } returns emptyList()
        coEvery { repository.saveAll(any()) } returns listOf(
            income(id = 9, date = "2026-11-20", status = IncomeStatus.ANNOUNCED),
        )

        service.importEvents(listOf(creation())).single().status shouldBe IncomeStatus.ANNOUNCED
    }

    "importEvents does not hit the repository for an empty batch" {
        coEvery { repository.fetchAll() } returns emptyList()

        service.importEvents(emptyList()) shouldBe emptyList()

        coVerify(exactly = 0) { repository.saveAll(any()) }
    }

    "reconcile marks a same-month payment of the same type as reconciled" {
        coEvery { repository.fetchAll() } returns listOf(
            income(id = 1, date = "2026-11-20", status = IncomeStatus.ANNOUNCED),
        )
        val marked = slot<List<Int>>()
        coEvery { repository.markReconciled(capture(marked)) } returns Unit
        coEvery { repository.saveAll(any()) } returns emptyList()

        val result = service.reconcile(listOf(creation(date = "2026-11-25")))

        marked.captured shouldBe listOf(1)
        result.single().status shouldBe IncomeStatus.RECONCILED
        coVerify(exactly = 0) { repository.saveAll(any()) }
    }

    "reconcile imports a movement no announced payment matches" {
        coEvery { repository.fetchAll() } returns listOf(
            income(id = 1, date = "2026-11-20", status = IncomeStatus.ANNOUNCED),
        )
        coEvery { repository.markReconciled(any()) } returns Unit
        val saved = slot<List<IncomeCreation>>()
        coEvery { repository.saveAll(capture(saved)) } returns emptyList()

        service.reconcile(listOf(creation(date = "2026-12-05", type = IncomeType.JCP)))

        saved.captured.single().type shouldBe IncomeType.JCP
        coVerify(exactly = 0) { repository.markReconciled(any()) }
    }

    "reconcile skips a movement already reconciled from another file" {
        coEvery { repository.fetchAll() } returns listOf(
            income(id = 1, date = "2026-11-20", status = IncomeStatus.RECONCILED),
        )
        coEvery { repository.markReconciled(any()) } returns Unit
        coEvery { repository.saveAll(any()) } returns emptyList()

        val result = service.reconcile(listOf(creation(date = "2026-11-25")))

        result shouldBe emptyList()
        coVerify(exactly = 0) { repository.markReconciled(any()) }
        coVerify(exactly = 0) { repository.saveAll(any()) }
    }
})

private fun income(
    id: Int,
    date: String,
    status: IncomeStatus,
    ticker: String = "PETR4",
    type: IncomeType = IncomeType.DIVIDEND,
) = Income(
    id = id,
    ticker = ticker,
    date = LocalDate.parse(date),
    type = type,
    grossAmount = BigDecimal("78.39"),
    retainedAmount = BigDecimal("13.33"),
    netAmount = BigDecimal("65.06"),
    taxNature = TaxNature.INCOME,
    provenance = Provenance.B3,
    status = status,
)

private fun creation(
    ticker: String = "PETR4",
    date: String = "2026-11-20",
    type: IncomeType = IncomeType.DIVIDEND,
) = IncomeCreation(
    ticker = ticker,
    date = LocalDate.parse(date),
    type = type,
    grossAmount = BigDecimal("78.39"),
    netAmount = BigDecimal("65.06"),
    provenance = Provenance.B3,
)
