package dev.agner.portfolio.usecase.corporateaction

import dev.agner.portfolio.usecase.InlineTransaction
import dev.agner.portfolio.usecase.actionCreation
import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import dev.agner.portfolio.usecase.corporateaction.model.CorporateCashMovement
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.income.model.TaxNature
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.Attribution
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import dev.agner.portfolio.usecase.toModel
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class CorporateActionServiceTest : StringSpec({
    val repository = mockk<ICorporateActionRepository>(relaxed = true)
    val attributions = mockk<IAttributionRepository>(relaxed = true)
    val catalog = mockk<CatalogService>(relaxed = true)
    val positionService = mockk<PositionService>(relaxed = true)
    val transaction = InlineTransaction()
    val service = CorporateActionService(repository, attributions, catalog, positionService, transaction)

    beforeTest {
        clearAllMocks()
        coEvery { repository.fetchAll() } returns emptyList()
        coEvery { catalog.fetchBy(any()) } returns catalogEntry
        coEvery { positionService.positionBefore(any()) } returns Position("PETR4", BigDecimal("100"), BigDecimal("10"))
        coEvery { positionService.reproject(any()) } returns Position("PETR4", BigDecimal.ZERO, BigDecimal.ZERO)
    }

    "a split applies, scales the attribution slices and reprojects the position" {
        val saved = slot<CorporateActionCreation>()
        coEvery { repository.save(capture(saved), CorporateActionStatus.APPLIED) } returns
            action(creation = split(ratio = "2"), status = CorporateActionStatus.APPLIED)
        coEvery { attributions.fetchBy("PETR4") } returns listOf(
            Attribution("PETR4", strategyId = 1, quantity = BigDecimal("60")),
            Attribution("PETR4", strategyId = 2, quantity = BigDecimal("40")),
        )

        service.importEvents(listOf(split(ratio = "2")))

        saved.captured.kind shouldBe CorporateActionKind.SPLIT
        coVerify { attributions.save(Attribution("PETR4", 1, BigDecimal("120"))) }
        coVerify { attributions.save(Attribution("PETR4", 2, BigDecimal("80"))) }
        coVerify(exactly = 1) { positionService.reproject("PETR4") }
    }

    "a grupamento queues its leftover fraction as an OTHER cash movement" {
        coEvery { positionService.positionBefore(any()) } returns
            Position("PETR4", BigDecimal("105"), BigDecimal("10"))
        coEvery { repository.save(any(), CorporateActionStatus.APPLIED) } returns
            action(creation = group(ratio = "10"), status = CorporateActionStatus.APPLIED)
        val cash = slot<CorporateCashMovement>()

        service.importEvents(listOf(group(ratio = "10")))

        coVerify { repository.saveCashMovement(capture(cash)) }
        cash.captured.ticker shouldBe "PETR4"
        cash.captured.amount.compareTo(BigDecimal("50")) shouldBe 0
        cash.captured.taxNature shouldBe TaxNature.OTHER
        cash.captured.corporateActionId shouldBe 7
    }

    "a capital return is a COST_REDUCTION cash movement and reprojects the position" {
        coEvery { repository.save(any(), CorporateActionStatus.APPLIED) } returns
            action(
                creation = cash(CorporateActionKind.CAPITAL_RETURN, perShare = "1.5"),
                status = CorporateActionStatus.APPLIED,
            )
        val cash = slot<CorporateCashMovement>()

        service.importEvents(listOf(cash(CorporateActionKind.CAPITAL_RETURN, perShare = "1.5")))

        coVerify { repository.saveCashMovement(capture(cash)) }
        cash.captured.amount.compareTo(BigDecimal("150")) shouldBe 0
        cash.captured.taxNature shouldBe TaxNature.COST_REDUCTION
        coVerify(exactly = 1) { positionService.reproject("PETR4") }
    }

    "an amortization is a RETURN_OF_CAPITAL cash movement with no position replay" {
        coEvery { repository.save(any(), CorporateActionStatus.APPLIED) } returns
            action(
                creation = cash(CorporateActionKind.AMORTIZATION, perShare = "0.8"),
                status = CorporateActionStatus.APPLIED,
            )
        val cash = slot<CorporateCashMovement>()

        service.importEvents(listOf(cash(CorporateActionKind.AMORTIZATION, perShare = "0.8")))

        coVerify { repository.saveCashMovement(capture(cash)) }
        cash.captured.taxNature shouldBe TaxNature.RETURN_OF_CAPITAL
        coVerify(exactly = 0) { positionService.reproject(any()) }
    }

    "a ticker change remaps the catalog instead of touching the position" {
        coEvery { repository.save(any(), CorporateActionStatus.APPLIED) } returns
            action(
                creation = CorporateActionCreation(
                    ticker = "PETR4",
                    kind = CorporateActionKind.TICKER_CHANGE,
                    exDate = date,
                    targetTicker = "VALE3",
                ),
                status = CorporateActionStatus.APPLIED,
            )

        service.importEvents(
            listOf(
                CorporateActionCreation(
                    ticker = "PETR4",
                    kind = CorporateActionKind.TICKER_CHANGE,
                    exDate = date,
                    targetTicker = "VALE3",
                ),
            ),
        )

        coVerify { catalog.remapTicker("PETR4", "VALE3") }
        coVerify(exactly = 0) { positionService.reproject(any()) }
    }

    "a ticker change for a ticker absent from the catalog is queued instead of applied" {
        coEvery { catalog.fetchBy("PETR4") } returns null
        coEvery { repository.save(any(), CorporateActionStatus.NEEDS_REVIEW) } returns
            action(
                creation = CorporateActionCreation(
                    ticker = "PETR4",
                    kind = CorporateActionKind.TICKER_CHANGE,
                    exDate = date,
                    targetTicker = "VALE3",
                ),
                status = CorporateActionStatus.NEEDS_REVIEW,
            )

        service.importEvents(
            listOf(
                CorporateActionCreation(
                    ticker = "PETR4",
                    kind = CorporateActionKind.TICKER_CHANGE,
                    exDate = date,
                    targetTicker = "VALE3",
                ),
            ),
        )

        coVerify { repository.save(any(), CorporateActionStatus.NEEDS_REVIEW) }
        coVerify(exactly = 0) { catalog.remapTicker(any(), any()) }
    }

    "a review kind is stored as NEEDS_REVIEW with no ledger effect" {
        coEvery { repository.save(any(), CorporateActionStatus.NEEDS_REVIEW) } returns
            action(creation = review(CorporateActionKind.RIGHTS_ISSUE), status = CorporateActionStatus.NEEDS_REVIEW)

        service.importEvents(listOf(review(CorporateActionKind.RIGHTS_ISSUE)))

        coVerify(exactly = 0) { positionService.reproject(any()) }
        coVerify(exactly = 0) { repository.saveCashMovement(any()) }
        coVerify(exactly = 0) { attributions.save(any()) }
    }

    "a quantity event with no ratio is parked for review" {
        coEvery { repository.save(any(), CorporateActionStatus.NEEDS_REVIEW) } returns
            action(creation = split(ratio = null), status = CorporateActionStatus.NEEDS_REVIEW)

        service.importEvents(listOf(split(ratio = null)))

        coVerify { repository.save(any(), CorporateActionStatus.NEEDS_REVIEW) }
        coVerify(exactly = 0) { positionService.reproject(any()) }
    }

    "a zero factor is parked for review instead of silently doing nothing" {
        coEvery { repository.save(any(), CorporateActionStatus.NEEDS_REVIEW) } returns
            action(creation = split(ratio = "0"), status = CorporateActionStatus.NEEDS_REVIEW)

        service.importEvents(listOf(split(ratio = "0")))

        coVerify { repository.save(any(), CorporateActionStatus.NEEDS_REVIEW) }
        coVerify(exactly = 0) { positionService.reproject(any()) }
    }

    "a zero declared value is parked for review" {
        coEvery { repository.save(any(), CorporateActionStatus.NEEDS_REVIEW) } returns
            action(
                creation = cash(CorporateActionKind.CAPITAL_RETURN, perShare = "0"),
                status = CorporateActionStatus.NEEDS_REVIEW,
            )

        service.importEvents(listOf(cash(CorporateActionKind.CAPITAL_RETURN, perShare = "0")))

        coVerify { repository.save(any(), CorporateActionStatus.NEEDS_REVIEW) }
        coVerify(exactly = 0) { repository.saveCashMovement(any()) }
    }

    "a re-upload of the same file is a no-op even when its rows do not match by natural key" {
        coEvery { repository.fetchAll() } returns listOf(
            action(creation = split(ratio = "2").copy(fileHash = "hash")),
        )

        service.importEvents(listOf(split(ratio = "3").copy(fileHash = "hash"))) shouldBe emptyList()

        coVerify(exactly = 0) { repository.save(any(), any()) }
    }

    "an event already in the log is skipped" {
        coEvery { repository.fetchAll() } returns listOf(action(creation = split(ratio = "2")))

        service.importEvents(listOf(split(ratio = "2")))

        coVerify(exactly = 0) { repository.save(any(), any()) }
    }

    "same-day events are applied in ordinal order rather than input order" {
        val stored = mutableListOf<CorporateActionCreation>()
        coEvery { repository.save(any(), any()) } answers {
            val creation = firstArg<CorporateActionCreation>()
            stored += creation
            action(creation = creation, status = CorporateActionStatus.APPLIED)
        }

        service.importEvents(
            listOf(
                split(ratio = "2", ordinal = 2),
                split(ratio = "3", ordinal = 1),
            ),
        )

        stored.map { it.ordinal } shouldContainExactly listOf(1, 2)
    }

    "the ticker is normalized before the event is stored" {
        val saved = slot<CorporateActionCreation>()
        coEvery { repository.save(capture(saved), any()) } returns action(creation = split(ratio = "2"))

        service.importEvents(listOf(split(ratio = "2").copy(ticker = "petr4f")))

        saved.captured.ticker shouldBe "PETR4"
    }
})

private val date = LocalDate.parse("2026-10-20")

private fun split(ratio: String?, ordinal: Int = 1) =
    actionCreation(CorporateActionKind.SPLIT, ratio = ratio, ordinal = ordinal)

private fun group(ratio: String) = actionCreation(CorporateActionKind.REVERSE_SPLIT, ratio = ratio)

private fun cash(kind: CorporateActionKind, perShare: String) =
    actionCreation(kind, declaredValuePerShare = perShare)

private fun review(kind: CorporateActionKind) = actionCreation(kind)

private fun action(
    creation: CorporateActionCreation,
    status: CorporateActionStatus = CorporateActionStatus.APPLIED,
    id: Int = 7,
) = creation.toModel(status = status, id = id)

private val catalogEntry = CatalogEntry(
    ticker = "PETR4",
    shortName = "PETR4",
    bdiType = "02",
    isin = "BRPETRACNPR6",
    assetKind = AssetKind.STOCK,
    assetClass = AssetClass.ACOES,
    firstSeen = LocalDate.parse("2026-01-02"),
    lastSeen = LocalDate.parse("2026-10-01"),
)
