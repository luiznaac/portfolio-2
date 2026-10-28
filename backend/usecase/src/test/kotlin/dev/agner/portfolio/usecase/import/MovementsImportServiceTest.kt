package dev.agner.portfolio.usecase.import

import dev.agner.portfolio.usecase.InlineTransaction
import dev.agner.portfolio.usecase.corporateAction
import dev.agner.portfolio.usecase.corporateaction.CorporateActionService
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.import.model.ImportStatus
import dev.agner.portfolio.usecase.import.model.MovementsRouting
import dev.agner.portfolio.usecase.income.IncomeService
import dev.agner.portfolio.usecase.income.model.IncomeCreation
import dev.agner.portfolio.usecase.income.model.IncomeType
import dev.agner.portfolio.usecase.income.model.Provenance
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

class MovementsImportServiceTest : StringSpec({
    val imports = mockk<ImportService>(relaxed = true)
    val incomeService = mockk<IncomeService>(relaxed = true)
    val corporateActions = mockk<CorporateActionService>(relaxed = true)
    val service = MovementsImportService(imports, incomeService, corporateActions, InlineTransaction())

    beforeTest {
        clearAllMocks()
        coEvery { imports.stage(any(), any(), any(), any()) } returns
            import(ImportStatus.STAGED)
        coEvery { imports.review(any()) } returns import(ImportStatus.REVIEWED)
        coEvery { imports.apply(any()) } returns import(ImportStatus.APPLIED)
        coEvery { incomeService.reconcile(any()) } returns emptyList()
        coEvery { corporateActions.importEvents(any()) } returns listOf(action)
    }

    "routes each destination and reports what it absorbed" {
        val result = service.import("movimentacao.xlsx", "hash", routing)

        result.trades shouldBe 1
        result.incomes shouldBe 0
        result.corporateActions shouldBe listOf(action)
        coVerify { imports.stage(ImportSource.B3_MOVEMENTS, "movimentacao.xlsx", "hash", routing.trades) }
        coVerify { imports.review(any()) }
        coVerify { imports.apply(any()) }
        coVerify { incomeService.reconcile(routing.incomes) }
        coVerify { corporateActions.importEvents(match { list -> list.single().fileHash == "hash" }) }
    }

    "a file already applied skips the trade pipeline" {
        coEvery { imports.stage(any(), any(), any(), any()) } returns import(ImportStatus.APPLIED)

        val result = service.import("movimentacao.xlsx", "hash", routing)

        result.trades shouldBe 0
        coVerify(exactly = 0) { imports.review(any()) }
        coVerify(exactly = 0) { imports.apply(any()) }
    }

    "a file with no trade rows never stages an empty import" {
        service.import("movimentacao.xlsx", "hash", routing.copy(trades = emptyList()))

        coVerify(exactly = 0) { imports.stage(any(), any(), any(), any()) }
    }
})

private val routing = MovementsRouting(
    trades = listOf(
        ImportRowPayload(
            date = LocalDate.parse("2026-10-20"),
            side = TradeSide.BUY,
            venue = "Mercado à Vista",
            ticker = "PETR4",
            quantity = BigDecimal("100"),
            unitPrice = BigDecimal("10"),
            institutionName = "XP",
        ),
    ),
    incomes = listOf(
        IncomeCreation(
            ticker = "PETR4",
            date = LocalDate.parse("2026-10-21"),
            type = IncomeType.DIVIDEND,
            grossAmount = BigDecimal("78.39"),
            netAmount = BigDecimal("65.06"),
            provenance = Provenance.B3,
        ),
    ),
    corporateActions = listOf(
        dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation(
            ticker = "PETR4",
            kind = CorporateActionKind.SPLIT,
            exDate = LocalDate.parse("2026-10-20"),
            ratio = BigDecimal("2"),
        ),
    ),
)

private val action = corporateAction(CorporateActionKind.SPLIT, ratio = "2", rawType = "Desdobramento")

private fun import(status: ImportStatus) = Import(
    id = 1,
    source = ImportSource.B3_MOVEMENTS,
    fileName = "movimentacao.xlsx",
    fileHash = "hash",
    status = status,
    createdAt = LocalDateTime.parse("2026-10-20T10:00:00"),
)
