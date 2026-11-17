package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.model.Attribution
import dev.agner.portfolio.usecase.listedasset.model.AttributionSliceCreation
import dev.agner.portfolio.usecase.listedasset.model.AttributionSummary
import dev.agner.portfolio.usecase.listedasset.model.AttributionTransferCreation
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.rename
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import dev.agner.portfolio.usecase.tickerAliases
import dev.agner.portfolio.usecase.tickeralias.model.TickerAlias
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class AttributionServiceTest : StringSpec({
    val attributions = mockk<IAttributionRepository>(relaxed = true)
    val positions = mockk<IPositionRepository>(relaxed = true)
    val strategies = mockk<IStrategyRepository>(relaxed = true)
    val catalog = mockk<ICatalogRepository>(relaxed = true)
    val transaction = AttributionTransaction()
    val service = AttributionService(attributions, positions, strategies, catalog, tickerAliases(), transaction)

    beforeTest {
        clearAllMocks()
        coEvery { catalog.fetchBy(any()) } returns catalogEntry(AssetClass.ACOES)
        transaction.count = 0
    }

    fun aliasedService(vararg renames: TickerAlias) = AttributionService(
        attributions,
        positions,
        strategies,
        catalog,
        tickerAliases(*renames),
        transaction,
    )

    "fetchSummary normalizes the ticker and reports the slices with their remainder" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { attributions.fetchBy("PETR4") } returns listOf(
            attribution(strategyId = 1, quantity = "60"),
            attribution(strategyId = 2, quantity = "10"),
        )
        coEvery { strategies.fetchAll() } returns strategyCatalog

        val summary = service.fetchSummary("petr4f")!!

        summary.ticker shouldBe "PETR4"
        summary.custodyQuantity.compareTo(BigDecimal("100")) shouldBe 0
        summary.attributedQuantity.compareTo(BigDecimal("70")) shouldBe 0
        summary.unattributedQuantity.compareTo(BigDecimal("30")) shouldBe 0
        summary.slices.map { it.strategyName } shouldBe listOf("Top Ações", "Top Dividendos")
        summary.strategyQuantity("Top Dividendos").compareTo(BigDecimal("60")) shouldBe 0
        summary.strategyQuantity("Top Ações").compareTo(BigDecimal("10")) shouldBe 0
    }

    "fetchLedger keys the ledger by strategy name and ticker" {
        coEvery { attributions.fetchAll() } returns listOf(
            attribution(strategyId = 1, quantity = "60"),
            Attribution(ticker = "VALE3", strategyId = 2, quantity = BigDecimal("5")),
        )
        coEvery { strategies.fetchAll() } returns strategyCatalog

        service.fetchLedger() shouldBe mapOf(
            StrategyTicker("Top Dividendos", "PETR4") to BigDecimal("60"),
            StrategyTicker("Top Ações", "VALE3") to BigDecimal("5"),
        )
    }

    "fetchSlicesByTicker maps a slice stored under a retired code onto the canonical ticker" {
        val aliased = AttributionService(
            attributions,
            positions,
            strategies,
            catalog,
            tickerAliases(rename("OLD3", "NEW3")),
            transaction,
        )
        coEvery {
            attributions.fetchAll()
        } returns listOf(Attribution(ticker = "OLD3", strategyId = 1, quantity = BigDecimal("10")))
        coEvery { strategies.fetchAll() } returns strategyCatalog

        aliased.fetchSlicesByTicker().keys shouldBe setOf("NEW3")
    }

    // A renamed paper's slices stay stored under whatever code they were written with, so the
    // single-ticker surface must treat every code in the chain as one book — one slice per strategy
    // with the quantities summed, the ceiling counting all of them, and every write landing on the
    // canonical code.

    "fetchSummary addressed by an old code sums the paper's slices stored under every code" {
        val aliased = aliasedService(rename("OLD3", "NEW3"))
        coEvery { positions.fetchBy("NEW3") } returns position(ticker = "NEW3", quantity = "100")
        coEvery { attributions.fetchBy("NEW3") } returns listOf(
            attribution(ticker = "NEW3", strategyId = 1, quantity = "20"),
        )
        coEvery { attributions.fetchBy("OLD3") } returns listOf(
            attribution(ticker = "OLD3", strategyId = 1, quantity = "30"),
            attribution(ticker = "OLD3", strategyId = 2, quantity = "10"),
        )
        coEvery { strategies.fetchAll() } returns strategyCatalog

        val summary = aliased.fetchSummary("OLD3")!!

        summary.ticker shouldBe "NEW3"
        summary.slices.size shouldBe 2
        summary.strategyQuantity("Top Dividendos").compareTo(BigDecimal("50")) shouldBe 0
        summary.strategyQuantity("Top Ações").compareTo(BigDecimal("10")) shouldBe 0
        summary.attributedQuantity.compareTo(BigDecimal("60")) shouldBe 0
        summary.unattributedQuantity.compareTo(BigDecimal("40")) shouldBe 0
    }

    "fetchSummary answers the canonical code and an old code alike" {
        val aliased = aliasedService(rename("OLD3", "NEW3"))
        coEvery { positions.fetchBy("NEW3") } returns position(ticker = "NEW3", quantity = "100")
        coEvery { attributions.fetchBy("OLD3") } returns listOf(
            attribution(ticker = "OLD3", strategyId = 1, quantity = "60"),
        )
        coEvery { strategies.fetchAll() } returns strategyCatalog

        val byOld = aliased.fetchSummary("OLD3")!!
        val byNew = aliased.fetchSummary("NEW3")!!

        byOld shouldBe byNew
        byNew.attributedQuantity.compareTo(BigDecimal("60")) shouldBe 0
    }

    "fetchSummary follows a rename chain and sums every code's slice" {
        val aliased = aliasedService(rename("OLD3", "MID3"), rename("MID3", "NEW3"))
        coEvery { positions.fetchBy("NEW3") } returns position(ticker = "NEW3", quantity = "100")
        coEvery { attributions.fetchBy("OLD3") } returns listOf(
            attribution(ticker = "OLD3", strategyId = 1, quantity = "10"),
        )
        coEvery { attributions.fetchBy("MID3") } returns listOf(
            attribution(ticker = "MID3", strategyId = 1, quantity = "5"),
        )
        coEvery { attributions.fetchBy("NEW3") } returns listOf(
            attribution(ticker = "NEW3", strategyId = 1, quantity = "15"),
        )
        coEvery { strategies.fetchAll() } returns strategyCatalog

        val summary = aliased.fetchSummary("OLD3")!!

        summary.slices.size shouldBe 1
        summary.strategyQuantity("Top Dividendos").compareTo(BigDecimal("30")) shouldBe 0
        summary.unattributedQuantity.compareTo(BigDecimal("70")) shouldBe 0
    }

    "setSlice counts the paper's slices stored under every code against the ceiling" {
        val aliased = aliasedService(rename("OLD3", "NEW3"))
        coEvery { positions.fetchBy("NEW3") } returns position(ticker = "NEW3", quantity = "100")
        coEvery { strategies.fetchById(3) } returns strategyCatalog.single { it.id == 3 }
        coEvery { attributions.fetchBy("OLD3") } returns listOf(
            attribution(ticker = "OLD3", strategyId = 1, quantity = "60"),
        )
        coEvery { attributions.fetchBy("NEW3") } returns listOf(
            attribution(ticker = "NEW3", strategyId = 2, quantity = "20"),
        )

        val error = runCatching {
            aliased.setSlice("OLD3", AttributionSliceCreation(strategyId = 3, quantity = BigDecimal("30")))
        }.exceptionOrNull()

        (error is InvalidAttributionException) shouldBe true
        coVerify(exactly = 0) { attributions.save(any()) }
    }

    "setSlice persists under the canonical code and supersedes the old-code slice" {
        val aliased = aliasedService(rename("OLD3", "NEW3"))
        coEvery { positions.fetchBy("NEW3") } returns position(ticker = "NEW3", quantity = "100")
        coEvery { strategies.fetchById(1) } returns strategyCatalog.single { it.id == 1 }
        coEvery { attributions.fetchBy("OLD3") } returns listOf(
            attribution(ticker = "OLD3", strategyId = 1, quantity = "60"),
        )
        coEvery { strategies.fetchAll() } returns strategyCatalog

        val summary = aliased.setSlice(
            "OLD3",
            AttributionSliceCreation(strategyId = 1, quantity = BigDecimal("70")),
        )!!

        coVerify(exactly = 1) { attributions.save(Attribution("NEW3", 1, BigDecimal("70"))) }
        coVerify(exactly = 1) { attributions.delete("OLD3", 1) }
        summary.slices.single().quantity.compareTo(BigDecimal("70")) shouldBe 0
        summary.attributedQuantity.compareTo(BigDecimal("70")) shouldBe 0
    }

    "setSlice counts a rename chain's slices against the ceiling" {
        val aliased = aliasedService(rename("OLD3", "MID3"), rename("MID3", "NEW3"))
        coEvery { positions.fetchBy("NEW3") } returns position(ticker = "NEW3", quantity = "100")
        coEvery { strategies.fetchById(3) } returns strategyCatalog.single { it.id == 3 }
        coEvery { attributions.fetchBy("OLD3") } returns listOf(
            attribution(ticker = "OLD3", strategyId = 1, quantity = "40"),
        )
        coEvery { attributions.fetchBy("MID3") } returns listOf(
            attribution(ticker = "MID3", strategyId = 2, quantity = "30"),
        )

        val error = runCatching {
            aliased.setSlice("NEW3", AttributionSliceCreation(strategyId = 3, quantity = BigDecimal("40")))
        }.exceptionOrNull()

        (error is InvalidAttributionException) shouldBe true
        coVerify(exactly = 0) { attributions.save(any()) }
    }

    "transfer finds a source slice stored under an old code and leaves one destination slice per strategy" {
        val aliased = aliasedService(rename("OLD3", "NEW3"))
        coEvery { positions.fetchBy("NEW3") } returns position(ticker = "NEW3", quantity = "100")
        coEvery { strategies.fetchById(1) } returns strategyCatalog.single { it.id == 1 }
        coEvery { strategies.fetchById(2) } returns strategyCatalog.single { it.id == 2 }
        coEvery { strategies.fetchAll() } returns strategyCatalog
        coEvery { attributions.fetchBy("OLD3") } returns listOf(
            attribution(ticker = "OLD3", strategyId = 1, quantity = "60"),
        )
        coEvery { attributions.fetchBy("NEW3") } returns listOf(
            attribution(ticker = "NEW3", strategyId = 2, quantity = "10"),
        )

        val summary = aliased.transfer(
            "OLD3",
            AttributionTransferCreation(fromStrategyId = 1, toStrategyId = 2, quantity = BigDecimal("20")),
        )!!

        coVerify(exactly = 1) { attributions.delete("OLD3", 1) }
        coVerify(exactly = 1) { attributions.save(Attribution("NEW3", 1, BigDecimal("40"))) }
        coVerify(exactly = 1) { attributions.save(Attribution("NEW3", 2, BigDecimal("30"))) }
        summary.slices.size shouldBe 2
        summary.attributedQuantity.compareTo(BigDecimal("70")) shouldBe 0
    }

    "transfer follows a rename chain and consolidates the destination under the canonical code" {
        val aliased = aliasedService(rename("OLD3", "MID3"), rename("MID3", "NEW3"))
        coEvery { positions.fetchBy("NEW3") } returns position(ticker = "NEW3", quantity = "100")
        coEvery { strategies.fetchById(1) } returns strategyCatalog.single { it.id == 1 }
        coEvery { strategies.fetchById(2) } returns strategyCatalog.single { it.id == 2 }
        coEvery { strategies.fetchAll() } returns strategyCatalog
        coEvery { attributions.fetchBy("OLD3") } returns listOf(
            attribution(ticker = "OLD3", strategyId = 1, quantity = "10"),
        )
        coEvery { attributions.fetchBy("MID3") } returns listOf(
            attribution(ticker = "MID3", strategyId = 1, quantity = "30"),
        )
        coEvery { attributions.fetchBy("NEW3") } returns listOf(
            attribution(ticker = "NEW3", strategyId = 2, quantity = "5"),
        )

        val summary = aliased.transfer(
            "OLD3",
            AttributionTransferCreation(fromStrategyId = 1, toStrategyId = 2, quantity = BigDecimal("20")),
        )!!

        coVerify(exactly = 1) { attributions.delete("OLD3", 1) }
        coVerify(exactly = 1) { attributions.delete("MID3", 1) }
        coVerify(exactly = 1) { attributions.save(Attribution("NEW3", 1, BigDecimal("20"))) }
        coVerify(exactly = 1) { attributions.save(Attribution("NEW3", 2, BigDecimal("25"))) }
        summary.slices.size shouldBe 2
        summary.attributedQuantity.compareTo(BigDecimal("45")) shouldBe 0
    }

    "removeSlice addressed by an old code removes the slice stored under every code" {
        val aliased = aliasedService(rename("OLD3", "NEW3"))
        coEvery { positions.fetchBy("NEW3") } returns position(ticker = "NEW3", quantity = "100")
        coEvery { attributions.fetchBy("OLD3") } returns listOf(
            attribution(ticker = "OLD3", strategyId = 1, quantity = "60"),
        )
        coEvery { attributions.fetchBy("NEW3") } returns listOf(
            attribution(ticker = "NEW3", strategyId = 2, quantity = "10"),
        )
        coEvery { strategies.fetchAll() } returns strategyCatalog

        val summary = aliased.removeSlice("OLD3", strategyId = 1)!!

        coVerify(exactly = 1) { attributions.delete("OLD3", 1) }
        coVerify(exactly = 1) { attributions.delete("NEW3", 1) }
        summary.attributedQuantity.compareTo(BigDecimal("10")) shouldBe 0
    }

    "removeSlice follows a rename chain and removes the slice stored under any code" {
        val aliased = aliasedService(rename("OLD3", "MID3"), rename("MID3", "NEW3"))
        coEvery { positions.fetchBy("NEW3") } returns position(ticker = "NEW3", quantity = "100")
        coEvery { attributions.fetchBy("MID3") } returns listOf(
            attribution(ticker = "MID3", strategyId = 1, quantity = "30"),
        )
        coEvery { attributions.fetchBy("NEW3") } returns listOf(
            attribution(ticker = "NEW3", strategyId = 2, quantity = "10"),
        )
        coEvery { strategies.fetchAll() } returns strategyCatalog

        val summary = aliased.removeSlice("NEW3", strategyId = 1)!!

        coVerify(exactly = 1) { attributions.delete("OLD3", 1) }
        coVerify(exactly = 1) { attributions.delete("MID3", 1) }
        coVerify(exactly = 1) { attributions.delete("NEW3", 1) }
        summary.attributedQuantity.compareTo(BigDecimal("10")) shouldBe 0
    }

    "fetchSummary is null when the ticker has no position" {
        coEvery { positions.fetchBy("PETR4") } returns null

        service.fetchSummary("PETR4") shouldBe null

        coVerify(exactly = 0) { attributions.fetchBy(any()) }
    }

    "fetchSummary reports the whole custody as unattributed when nothing is attributed" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { attributions.fetchBy("PETR4") } returns emptyList()
        coEvery { strategies.fetchAll() } returns strategyCatalog

        val summary = service.fetchSummary("PETR4")!!

        summary.attributedQuantity.compareTo(BigDecimal.ZERO) shouldBe 0
        summary.unattributedQuantity.compareTo(BigDecimal("100")) shouldBe 0
        summary.slices shouldBe emptyList()
    }

    "setSlice stores the slice and returns the updated remainder" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(2) } returns strategyCatalog.single { it.id == 2 }
        coEvery { attributions.fetchBy("PETR4") } returns listOf(attribution(strategyId = 1, quantity = "60"))
        val saved = slot<Attribution>()
        coEvery { attributions.save(capture(saved)) } returns Unit

        val summary = service.setSlice("petr4", AttributionSliceCreation(strategyId = 2, quantity = BigDecimal("15")))!!

        saved.captured shouldBe Attribution("PETR4", 2, BigDecimal("15"))
        summary.attributedQuantity.compareTo(BigDecimal("75")) shouldBe 0
        summary.unattributedQuantity.compareTo(BigDecimal("25")) shouldBe 0
        transaction.count shouldBe 1
    }

    "setSlice replaces the existing slice instead of counting it twice" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(1) } returns strategyCatalog.single { it.id == 1 }
        coEvery { attributions.fetchBy("PETR4") } returns listOf(
            attribution(strategyId = 1, quantity = "60"),
            attribution(strategyId = 2, quantity = "10"),
        )

        val summary = service.setSlice("PETR4", AttributionSliceCreation(strategyId = 1, quantity = BigDecimal("80")))!!

        summary.attributedQuantity.compareTo(BigDecimal("90")) shouldBe 0
        summary.unattributedQuantity.compareTo(BigDecimal("10")) shouldBe 0
        coVerify(exactly = 1) { attributions.save(Attribution("PETR4", 1, BigDecimal("80"))) }
    }

    "setSlice refuses to attribute more than the position holds" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(2) } returns strategyCatalog.single { it.id == 2 }
        coEvery { attributions.fetchBy("PETR4") } returns listOf(attribution(strategyId = 1, quantity = "60"))

        val error = runCatching {
            service.setSlice("PETR4", AttributionSliceCreation(strategyId = 2, quantity = BigDecimal("50")))
        }.exceptionOrNull()

        (error is InvalidAttributionException) shouldBe true
        coVerify(exactly = 0) { attributions.save(any()) }
    }

    "setSlice refuses a non-positive quantity" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(2) } returns strategyCatalog.single { it.id == 2 }

        for (quantity in listOf("0", "-1")) {
            val error = runCatching {
                service.setSlice("PETR4", AttributionSliceCreation(strategyId = 2, quantity = BigDecimal(quantity)))
            }.exceptionOrNull()

            (error is InvalidAttributionException) shouldBe true
        }

        coVerify(exactly = 0) { attributions.save(any()) }
    }

    "setSlice rejects a ticker whose catalog class differs from the strategy's" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(2) } returns strategyCatalog.single { it.id == 2 }
        coEvery { catalog.fetchBy("PETR4") } returns catalogEntry(AssetClass.FIIS)

        val error = runCatching {
            service.setSlice("PETR4", AttributionSliceCreation(strategyId = 2, quantity = BigDecimal("10")))
        }.exceptionOrNull()

        (error is InvalidAttributionException) shouldBe true
        coVerify(exactly = 0) { attributions.save(any()) }
    }

    "setSlice rejects a ticker whose effective catalog class is null" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(2) } returns strategyCatalog.single { it.id == 2 }
        coEvery { catalog.fetchBy("PETR4") } returns catalogEntry(assetClass = null)

        val error = runCatching {
            service.setSlice("PETR4", AttributionSliceCreation(strategyId = 2, quantity = BigDecimal("10")))
        }.exceptionOrNull()

        (error is InvalidAttributionException) shouldBe true
        coVerify(exactly = 0) { attributions.save(any()) }
    }

    "setSlice accepts a ticker whose manual override outranks its BDI class" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(2) } returns strategyCatalog.single { it.id == 2 }
        coEvery { catalog.fetchBy("PETR4") } returns catalogEntry(AssetClass.FIIS)
            .copy(assetClassOverride = AssetClass.ACOES)

        val summary = service.setSlice(
            "PETR4",
            AttributionSliceCreation(strategyId = 2, quantity = BigDecimal("10")),
        )!!

        summary.attributedQuantity.compareTo(BigDecimal("10")) shouldBe 0
        coVerify(exactly = 1) { attributions.save(Attribution("PETR4", 2, BigDecimal("10"))) }
    }

    "setSlice accepts a Manual Strategy of the ticker's class" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(3) } returns Strategy(
            id = 3,
            name = "Legado",
            assetClass = AssetClass.ACOES,
            kind = StrategyKind.MANUAL,
        )
        coEvery { attributions.fetchBy("PETR4") } returns emptyList()

        val summary = service.setSlice("PETR4", AttributionSliceCreation(strategyId = 3, quantity = BigDecimal("40")))!!

        summary.attributedQuantity.compareTo(BigDecimal("40")) shouldBe 0
        coVerify(exactly = 1) { attributions.save(Attribution("PETR4", 3, BigDecimal("40"))) }
    }

    "setSlice is null when the position does not exist" {
        coEvery { positions.fetchBy("PETR4") } returns null

        service.setSlice("PETR4", AttributionSliceCreation(strategyId = 2, quantity = BigDecimal("10"))) shouldBe null

        coVerify(exactly = 0) { attributions.save(any()) }
    }

    "setSlice is null when the strategy does not exist" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(404) } returns null

        service.setSlice("PETR4", AttributionSliceCreation(strategyId = 404, quantity = BigDecimal("10"))) shouldBe null

        coVerify(exactly = 0) { attributions.save(any()) }
    }

    "removeSlice deletes the slice and returns the remainder" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { attributions.fetchBy("PETR4") } returns listOf(attribution(strategyId = 2, quantity = "10"))
        coEvery { strategies.fetchAll() } returns strategyCatalog

        val summary = service.removeSlice("petr4", strategyId = 1)!!

        coVerify(exactly = 1) { attributions.delete("PETR4", 1) }
        summary.attributedQuantity.compareTo(BigDecimal("10")) shouldBe 0
        summary.unattributedQuantity.compareTo(BigDecimal("90")) shouldBe 0
        transaction.count shouldBe 1
    }

    "removeSlice is null when the position does not exist" {
        coEvery { positions.fetchBy("PETR4") } returns null

        service.removeSlice("PETR4", strategyId = 1) shouldBe null

        coVerify(exactly = 0) { attributions.delete(any(), any()) }
    }

    "transfer moves the units from the source slice to the destination in one transaction" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(1) } returns strategyCatalog.single { it.id == 1 }
        coEvery { strategies.fetchById(2) } returns strategyCatalog.single { it.id == 2 }
        coEvery { strategies.fetchAll() } returns strategyCatalog
        coEvery { attributions.fetchBy("PETR4") } returns listOf(
            attribution(strategyId = 1, quantity = "60"),
            attribution(strategyId = 2, quantity = "10"),
        )

        val summary = service.transfer(
            "petr4",
            AttributionTransferCreation(fromStrategyId = 1, toStrategyId = 2, quantity = BigDecimal("20")),
        )!!

        coVerify(exactly = 1) { attributions.save(Attribution("PETR4", 1, BigDecimal("40"))) }
        coVerify(exactly = 1) { attributions.save(Attribution("PETR4", 2, BigDecimal("30"))) }
        coVerify(exactly = 0) { attributions.delete(any(), any()) }
        summary.strategyQuantity("Top Dividendos").compareTo(BigDecimal("40")) shouldBe 0
        summary.strategyQuantity("Top Ações").compareTo(BigDecimal("30")) shouldBe 0
        summary.attributedQuantity.compareTo(BigDecimal("70")) shouldBe 0
        transaction.count shouldBe 1
    }

    "transfer deletes the source row when it reaches zero" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(1) } returns strategyCatalog.single { it.id == 1 }
        coEvery { strategies.fetchById(2) } returns strategyCatalog.single { it.id == 2 }
        coEvery { attributions.fetchBy("PETR4") } returns listOf(
            attribution(strategyId = 1, quantity = "60"),
            attribution(strategyId = 2, quantity = "10"),
        )

        service.transfer(
            "PETR4",
            AttributionTransferCreation(fromStrategyId = 1, toStrategyId = 2, quantity = BigDecimal("60")),
        )

        coVerify(exactly = 1) { attributions.delete("PETR4", 1) }
        coVerify(exactly = 0) { attributions.save(match { it.strategyId == 1 }) }
        coVerify(exactly = 1) { attributions.save(Attribution("PETR4", 2, BigDecimal("70"))) }
    }

    "transfer refuses to move more than the source slice holds and writes nothing" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(1) } returns strategyCatalog.single { it.id == 1 }
        coEvery { strategies.fetchById(2) } returns strategyCatalog.single { it.id == 2 }
        coEvery { attributions.fetchBy("PETR4") } returns listOf(
            attribution(strategyId = 1, quantity = "10"),
            attribution(strategyId = 2, quantity = "60"),
        )

        val error = runCatching {
            service.transfer(
                "PETR4",
                AttributionTransferCreation(fromStrategyId = 1, toStrategyId = 2, quantity = BigDecimal("20")),
            )
        }.exceptionOrNull()

        (error is InvalidTransferException) shouldBe true
        coVerify(exactly = 0) { attributions.save(any()) }
        coVerify(exactly = 0) { attributions.delete(any(), any()) }
        transaction.count shouldBe 1
    }

    "transfer refuses a non-positive quantity before opening a transaction" {
        val error = runCatching {
            service.transfer(
                "PETR4",
                AttributionTransferCreation(fromStrategyId = 1, toStrategyId = 2, quantity = BigDecimal.ZERO),
            )
        }.exceptionOrNull()

        (error is InvalidTransferException) shouldBe true
        coVerify(exactly = 0) { attributions.save(any()) }
        transaction.count shouldBe 0
    }

    "transfer rejects a class mismatch on either side of the move" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(1) } returns strategyCatalog.single { it.id == 1 }
        coEvery { strategies.fetchById(2) } returns strategyCatalog.single { it.id == 2 }
        coEvery { catalog.fetchBy("PETR4") } returns catalogEntry(AssetClass.FIIS)

        val error = runCatching {
            service.transfer(
                "PETR4",
                AttributionTransferCreation(fromStrategyId = 1, toStrategyId = 2, quantity = BigDecimal("10")),
            )
        }.exceptionOrNull()

        (error is InvalidTransferException) shouldBe true
        coVerify(exactly = 0) { attributions.save(any()) }
        transaction.count shouldBe 1
    }

    "transfer refuses the same strategy as source and destination" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(1) } returns strategyCatalog.single { it.id == 1 }

        val error = runCatching {
            service.transfer(
                "PETR4",
                AttributionTransferCreation(fromStrategyId = 1, toStrategyId = 1, quantity = BigDecimal("10")),
            )
        }.exceptionOrNull()

        (error is InvalidTransferException) shouldBe true
        coVerify(exactly = 0) { attributions.save(any()) }
    }

    "transfer allows the manual bucket as the destination" {
        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(1) } returns strategyCatalog.single { it.id == 1 }
        coEvery { strategies.fetchById(3) } returns strategyCatalog.single { it.id == 3 }
        coEvery { strategies.fetchAll() } returns strategyCatalog
        coEvery { attributions.fetchBy("PETR4") } returns listOf(attribution(strategyId = 1, quantity = "60"))

        val summary = service.transfer(
            "PETR4",
            AttributionTransferCreation(fromStrategyId = 1, toStrategyId = 3, quantity = BigDecimal("15")),
        )!!

        coVerify(exactly = 1) { attributions.save(Attribution("PETR4", 1, BigDecimal("45"))) }
        coVerify(exactly = 1) { attributions.save(Attribution("PETR4", 3, BigDecimal("15"))) }
        summary.strategyQuantity("Legado").compareTo(BigDecimal("15")) shouldBe 0
    }

    "transfer is null when the position or a strategy does not exist" {
        coEvery { positions.fetchBy("PETR4") } returns null
        service.transfer(
            "PETR4",
            AttributionTransferCreation(fromStrategyId = 1, toStrategyId = 2, quantity = BigDecimal("10")),
        ) shouldBe null

        coEvery { positions.fetchBy("PETR4") } returns position(quantity = "100")
        coEvery { strategies.fetchById(1) } returns strategyCatalog.single { it.id == 1 }
        coEvery { strategies.fetchById(2) } returns null
        service.transfer(
            "PETR4",
            AttributionTransferCreation(fromStrategyId = 1, toStrategyId = 2, quantity = BigDecimal("10")),
        ) shouldBe null

        coVerify(exactly = 0) { attributions.save(any()) }
    }
})

private val strategyCatalog = listOf(
    Strategy(id = 1, name = "Top Dividendos", assetClass = AssetClass.ACOES, kind = StrategyKind.REPORT_DRIVEN),
    Strategy(id = 2, name = "Top Ações", assetClass = AssetClass.ACOES, kind = StrategyKind.REPORT_DRIVEN),
    Strategy(id = 3, name = "Legado", assetClass = AssetClass.ACOES, kind = StrategyKind.MANUAL),
)

private fun position(quantity: String, ticker: String = "PETR4") =
    Position(ticker = ticker, quantity = BigDecimal(quantity), averagePrice = BigDecimal("10"))

private fun attribution(ticker: String = "PETR4", strategyId: Int, quantity: String) =
    Attribution(ticker = ticker, strategyId = strategyId, quantity = BigDecimal(quantity))

private class AttributionTransaction : ITransactionTemplate {
    var count = 0

    override suspend fun <T> execute(block: suspend () -> T): T {
        count++
        return block()
    }
}

private fun AttributionSummary.strategyQuantity(strategyName: String): BigDecimal =
    slices.single { it.strategyName == strategyName }.quantity

private fun catalogEntry(assetClass: AssetClass?) = CatalogEntry(
    ticker = "PETR4",
    shortName = "PETR4",
    bdiType = "02",
    isin = null,
    assetKind = AssetKind.STOCK,
    assetClass = assetClass,
    firstSeen = LocalDate.parse("2026-10-01"),
    lastSeen = LocalDate.parse("2026-10-01"),
)
