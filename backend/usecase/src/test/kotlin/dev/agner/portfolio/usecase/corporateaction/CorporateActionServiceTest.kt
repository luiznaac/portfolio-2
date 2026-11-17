package dev.agner.portfolio.usecase.corporateaction

import dev.agner.portfolio.usecase.InlineTransaction
import dev.agner.portfolio.usecase.actionCreation
import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.corporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionResolution
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import dev.agner.portfolio.usecase.corporateaction.model.CorporateCashMovement
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.income.model.TaxNature
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.RightsAllotmentService
import dev.agner.portfolio.usecase.listedasset.model.Attribution
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import dev.agner.portfolio.usecase.rename
import dev.agner.portfolio.usecase.tickerAliases
import dev.agner.portfolio.usecase.tickerAliasesRepository
import dev.agner.portfolio.usecase.tickeralias.TickerAliasResolver
import dev.agner.portfolio.usecase.tickeralias.repository.ITickerAliasRepository
import dev.agner.portfolio.usecase.toModel
import io.kotest.assertions.throwables.shouldThrow
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
    val rightsAllotments = mockk<RightsAllotmentService>(relaxed = true)
    val transaction = InlineTransaction()
    val aliasRepository = tickerAliasesRepository()
    val service = CorporateActionService(
        repository,
        attributions,
        catalog,
        positionService,
        aliasRepository,
        tickerAliases(),
        rightsAllotments,
        transaction,
    )

    // A service over a fresh alias table, so a rename spec does not see the shared repository's
    // edges from an earlier spec.
    fun renamingService(aliases: ITickerAliasRepository) = CorporateActionService(
        repository,
        attributions,
        catalog,
        positionService,
        aliases,
        TickerAliasResolver(aliases),
        rightsAllotments,
        transaction,
    )

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

    "a ticker change remaps the catalog, writes the alias and moves the position" {
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
        aliasRepository.fetchAll().single().oldTicker shouldBe "PETR4"
        aliasRepository.fetchAll().single().canonicalTicker shouldBe "VALE3"
        aliasRepository.fetchAll().single().corporateActionId shouldBe 7
        coVerify(exactly = 1) { positionService.reproject("PETR4") }
    }

    "a rename carrying a conversion ratio scales the attribution slices and stores the ratio on the alias" {
        val freshAliases = tickerAliasesRepository()
        val renaming = renamingService(freshAliases)
        val creation = actionCreation(
            CorporateActionKind.TICKER_CHANGE,
            ticker = "OLD3",
            targetTicker = "NEW3",
            ratio = "2",
        )
        coEvery { repository.save(any(), CorporateActionStatus.APPLIED) } returns action(creation = creation)
        coEvery { attributions.fetchBy("OLD3") } returns
            listOf(Attribution("OLD3", strategyId = 1, quantity = BigDecimal("60")))
        coEvery { attributions.fetchBy("NEW3") } returns emptyList()

        renaming.importEvents(listOf(creation))

        freshAliases.fetchAll().single().ratio?.compareTo(BigDecimal("2")) shouldBe 0
        coVerify { attributions.save(Attribution("OLD3", 1, BigDecimal("120"))) }
        coVerify { positionService.reproject("OLD3") }
    }

    "a same-date quantity event keyed to the new code scales the old code's attribution slices" {
        val freshAliases = tickerAliasesRepository()
        val renaming = renamingService(freshAliases)
        val rename = actionCreation(
            CorporateActionKind.TICKER_CHANGE,
            ticker = "OLD3",
            targetTicker = "NEW3",
        )
        val split = actionCreation(CorporateActionKind.SPLIT, ticker = "NEW3", ratio = "2")
        coEvery { repository.save(any(), CorporateActionStatus.APPLIED) } answers {
            action(creation = firstArg(), status = CorporateActionStatus.APPLIED)
        }
        coEvery { attributions.fetchBy("OLD3") } returns
            listOf(Attribution("OLD3", strategyId = 1, quantity = BigDecimal("60")))
        coEvery { attributions.fetchBy("NEW3") } returns emptyList()

        // The split is listed first to prove the batch order, not the input order, decides: the
        // rename has to land first so the split finds the old code's slices through the alias.
        renaming.importEvents(listOf(split, rename))

        coVerify { attributions.save(Attribution("OLD3", 1, BigDecimal("120"))) }
    }

    "a same-date quantity event keyed to the old code scales the same slices" {
        val freshAliases = tickerAliasesRepository()
        val renaming = renamingService(freshAliases)
        val rename = actionCreation(
            CorporateActionKind.TICKER_CHANGE,
            ticker = "OLD3",
            targetTicker = "NEW3",
        )
        val split = actionCreation(CorporateActionKind.SPLIT, ticker = "OLD3", ratio = "2")
        coEvery { repository.save(any(), CorporateActionStatus.APPLIED) } answers {
            action(creation = firstArg(), status = CorporateActionStatus.APPLIED)
        }
        coEvery { attributions.fetchBy("OLD3") } returns
            listOf(Attribution("OLD3", strategyId = 1, quantity = BigDecimal("60")))
        coEvery { attributions.fetchBy("NEW3") } returns emptyList()

        renaming.importEvents(listOf(split, rename))

        coVerify { attributions.save(Attribution("OLD3", 1, BigDecimal("120"))) }
    }

    "a rename applies even when the target changes the class digit" {
        val freshAliases = tickerAliasesRepository()
        val renaming = renamingService(freshAliases)
        // BIDI11 and INBR32 share no root or class digit; brapi's old-to-canonical result is the
        // authority, so the apply path must not gate a rename on the codes looking alike.
        val creation = actionCreation(
            CorporateActionKind.TICKER_CHANGE,
            ticker = "BIDI11",
            targetTicker = "INBR32",
        )
        coEvery { repository.save(any(), CorporateActionStatus.APPLIED) } returns action(creation = creation)

        renaming.importEvents(listOf(creation))

        coVerify { catalog.remapTicker("BIDI11", "INBR32") }
        freshAliases.fetchAll().single().canonicalTicker shouldBe "INBR32"
        coVerify { positionService.reproject("BIDI11") }
    }

    "fetchAll presents the canonical ticker and target of a rename chain" {
        coEvery { repository.fetchAll() } returns listOf(
            corporateAction(
                CorporateActionKind.TICKER_CHANGE,
                ticker = "OLD3",
                targetTicker = "MID3",
            ),
        )
        val aliased = CorporateActionService(
            repository,
            attributions,
            catalog,
            positionService,
            aliasRepository,
            tickerAliases(rename("OLD3", "MID3"), rename("MID3", "NEW3")),
            rightsAllotments,
            transaction,
        )

        val action = aliased.fetchAll().single()

        action.ticker shouldBe "NEW3"
        action.targetTicker shouldBe "NEW3"
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

    "a rights allotment grants the underlying position's factor and retires the derived receipt" {
        coEvery { positionService.positionBefore("RBRP11", date) } returns
            Position("RBRP11", BigDecimal("100"), BigDecimal("10"))
        val saved = slot<CorporateActionCreation>()
        coEvery { repository.save(capture(saved), CorporateActionStatus.APPLIED) } answers {
            saved.captured.toModel(status = CorporateActionStatus.APPLIED)
        }

        service.importEvents(listOf(allotment(ratio = "0.1")))

        saved.captured.let {
            it.ticker shouldBe "RBRP12"
            it.targetTicker shouldBe "RBRP11"
            it.grantedQuantity!!.compareTo(BigDecimal("10")) shouldBe 0
        }
        coVerify { catalog.markRights("RBRP12") }
        coVerify { rightsAllotments.replaceDerived("RBRP12", date) }
        coVerify(exactly = 1) { positionService.reproject("RBRP12") }
        coVerify(exactly = 0) { repository.saveCashMovement(any()) }
    }

    "a rights allotment with no factor is parked for review" {
        service.importEvents(listOf(allotment(ratio = null)))

        coVerify { repository.save(any(), CorporateActionStatus.NEEDS_REVIEW) }
        coVerify(exactly = 0) { rightsAllotments.replaceDerived(any(), any()) }
        coVerify(exactly = 0) { positionService.reproject(any()) }
    }

    "resolving a parked rights allotment computes the grant from the supplied factor" {
        coEvery { repository.fetchById(7) } returns
            action(creation = allotment(ratio = null), status = CorporateActionStatus.NEEDS_REVIEW)
        coEvery { positionService.positionBefore("RBRP11", date) } returns
            Position("RBRP11", BigDecimal("100"), BigDecimal("10"))
        val saved = slot<CorporateActionCreation>()
        coEvery { repository.update(7, capture(saved), CorporateActionStatus.APPLIED) } answers {
            saved.captured.toModel(status = CorporateActionStatus.APPLIED)
        }

        service.resolve(7, CorporateActionResolution(ratio = BigDecimal("0.1")))

        saved.captured.grantedQuantity!!.compareTo(BigDecimal("10")) shouldBe 0
        coVerify { rightsAllotments.replaceDerived("RBRP12", date) }
        coVerify(exactly = 1) { positionService.reproject("RBRP12") }
    }

    "resolving a parked quantity event applies the supplied factor and its ledger effects" {
        coEvery { repository.fetchById(7) } returns
            action(creation = split(ratio = null), status = CorporateActionStatus.NEEDS_REVIEW)
        coEvery { repository.update(7, any(), CorporateActionStatus.APPLIED) } returns
            action(creation = split(ratio = "2"), status = CorporateActionStatus.APPLIED)
        coEvery { attributions.fetchBy("PETR4") } returns listOf(
            Attribution("PETR4", strategyId = 1, quantity = BigDecimal("60")),
        )
        val saved = slot<CorporateActionCreation>()

        service.resolve(7, CorporateActionResolution(ratio = BigDecimal("2")))

        coVerify { repository.update(7, capture(saved), CorporateActionStatus.APPLIED) }
        saved.captured.ratio?.compareTo(BigDecimal("2")) shouldBe 0
        coVerify { attributions.save(Attribution("PETR4", 1, BigDecimal("120"))) }
        coVerify(exactly = 1) { positionService.reproject("PETR4") }
    }

    "resolving a parked cash event records its per-share movement" {
        coEvery { repository.fetchById(7) } returns
            action(
                creation = cash(CorporateActionKind.CAPITAL_RETURN, perShare = "0"),
                status = CorporateActionStatus.NEEDS_REVIEW,
            )
        coEvery { repository.update(7, any(), CorporateActionStatus.APPLIED) } returns
            action(
                creation = cash(CorporateActionKind.CAPITAL_RETURN, perShare = "1.5"),
                status = CorporateActionStatus.APPLIED,
            )
        val movement = slot<CorporateCashMovement>()

        service.resolve(7, CorporateActionResolution(declaredValuePerShare = BigDecimal("1.5")))

        coVerify { repository.saveCashMovement(capture(movement)) }
        movement.captured.amount.compareTo(BigDecimal("150")) shouldBe 0
        movement.captured.taxNature shouldBe TaxNature.COST_REDUCTION
        coVerify(exactly = 1) { positionService.reproject("PETR4") }
    }

    "resolving a parked ticker change remaps the catalog with the supplied code" {
        coEvery { repository.fetchById(7) } returns
            action(
                creation = actionCreation(CorporateActionKind.TICKER_CHANGE),
                status = CorporateActionStatus.NEEDS_REVIEW,
            )
        coEvery { repository.update(7, any(), CorporateActionStatus.APPLIED) } returns
            action(
                creation = actionCreation(CorporateActionKind.TICKER_CHANGE, targetTicker = "VALE3"),
                status = CorporateActionStatus.APPLIED,
            )

        service.resolve(7, CorporateActionResolution(targetTicker = "VALE3"))

        coVerify { catalog.remapTicker("PETR4", "VALE3") }
    }

    "a ticker change whose source is absent from the catalog is rejected" {
        coEvery { catalog.fetchBy(any()) } returns null
        coEvery { repository.fetchById(7) } returns
            action(
                creation = actionCreation(CorporateActionKind.TICKER_CHANGE, targetTicker = "VALE3"),
                status = CorporateActionStatus.NEEDS_REVIEW,
            )

        shouldThrow<InvalidCorporateActionResolutionException> {
            service.resolve(7, CorporateActionResolution(targetTicker = "VALE3"))
        }

        coVerify(exactly = 0) { repository.update(any(), any(), any()) }
    }

    "resolving a manual review kind records it as handled without ledger effects" {
        coEvery { repository.fetchById(7) } returns
            action(creation = review(CorporateActionKind.RIGHTS_ISSUE), status = CorporateActionStatus.NEEDS_REVIEW)
        coEvery { repository.update(7, any(), CorporateActionStatus.APPLIED) } returns
            action(creation = review(CorporateActionKind.RIGHTS_ISSUE), status = CorporateActionStatus.APPLIED)

        service.resolve(7, CorporateActionResolution())

        coVerify { repository.update(7, any(), CorporateActionStatus.APPLIED) }
        coVerify(exactly = 0) { positionService.reproject(any()) }
        coVerify(exactly = 0) { repository.saveCashMovement(any()) }
        coVerify(exactly = 0) { attributions.save(any()) }
    }

    "resolving without the missing factor is rejected and nothing is written" {
        coEvery { repository.fetchById(7) } returns
            action(creation = split(ratio = null), status = CorporateActionStatus.NEEDS_REVIEW)

        shouldThrow<InvalidCorporateActionResolutionException> {
            service.resolve(7, CorporateActionResolution())
        }

        coVerify(exactly = 0) { repository.update(any(), any(), any()) }
    }

    "resolving an already applied event is rejected" {
        coEvery { repository.fetchById(7) } returns action(creation = split(ratio = "2"))

        shouldThrow<InvalidCorporateActionResolutionException> {
            service.resolve(7, CorporateActionResolution(ratio = BigDecimal("2")))
        }

        coVerify(exactly = 0) { repository.update(any(), any(), any()) }
    }

    "resolving an unknown id is rejected" {
        coEvery { repository.fetchById(7) } returns null

        shouldThrow<CorporateActionNotFoundException> {
            service.resolve(7, CorporateActionResolution())
        }
    }
})

private val date = LocalDate.parse("2026-10-20")

private fun split(ratio: String?, ordinal: Int = 1) =
    actionCreation(CorporateActionKind.SPLIT, ratio = ratio, ordinal = ordinal)

private fun group(ratio: String) = actionCreation(CorporateActionKind.REVERSE_SPLIT, ratio = ratio)

private fun cash(kind: CorporateActionKind, perShare: String) =
    actionCreation(kind, declaredValuePerShare = perShare)

// The real allotment is keyed on the rights ticker it creates; `target_ticker` carries the
// underlying whose position and factor determine the grant.
private fun allotment(ratio: String?) = actionCreation(
    CorporateActionKind.RIGHTS_ALLOTMENT,
    ticker = "RBRP12",
    targetTicker = "RBRP11",
    ratio = ratio,
)

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
