package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.strategy.model.DiffChange
import dev.agner.portfolio.usecase.strategy.model.DiffWeight
import dev.agner.portfolio.usecase.strategy.model.EditionDetail
import dev.agner.portfolio.usecase.strategy.model.EditionDiff
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import dev.agner.portfolio.usecase.strategy.model.EditionImport
import dev.agner.portfolio.usecase.strategy.model.EditionStatus
import dev.agner.portfolio.usecase.strategy.model.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.model.Rating
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import dev.agner.portfolio.usecase.strategy.repository.IEditionRepository
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

class StrategyServiceTest : StringSpec({
    val strategies = mockk<IStrategyRepository>(relaxed = true)
    val editions = mockk<IEditionRepository>(relaxed = true)
    val transaction = CountingTransaction()
    val service = StrategyService(strategies, editions, EditionDiffCalculator(), transaction)

    beforeTest {
        clearAllMocks()
        transaction.count = 0
    }

    "create delegates to the repository" {
        coEvery { strategies.fetchByName("Top Dividendos") } returns null
        coEvery { strategies.save(creation) } returns strategy

        service.create(creation) shouldBe strategy

        coVerify(exactly = 1) { strategies.save(creation) }
    }

    "create refuses a name already in use" {
        coEvery { strategies.fetchByName("Top Dividendos") } returns strategy

        val error = runCatching { service.create(creation) }.exceptionOrNull()

        (error is DuplicateStrategyNameException) shouldBe true
        coVerify(exactly = 0) { strategies.save(any()) }
    }

    "fetchEditions is null when the strategy does not exist" {
        coEvery { strategies.fetchById(404) } returns null

        service.fetchEditions(404) shouldBe null

        coVerify(exactly = 0) { editions.fetchByStrategy(any()) }
    }

    "fetchEditions reads the strategy's editions" {
        coEvery { strategies.fetchById(1) } returns strategy
        coEvery { editions.fetchByStrategy(1) } returns listOf(previousEdition, stagedEdition)

        service.fetchEditions(1) shouldBe listOf(previousEdition, stagedEdition)
    }

    "fetchNeutroSlices reads the current edition's doNotTrade entries" {
        coEvery { strategies.fetchAll() } returns listOf(strategy)
        coEvery { editions.fetchLatestActive(1, null) } returns previousEdition.copy(
            entries = listOf(
                EditionEntry("PETR4", BigDecimal("50.00"), Rating.BUY, doNotTrade = true),
                EditionEntry("VALE3", BigDecimal("50.00"), Rating.NEUTRAL, doNotTrade = false),
            ),
        )

        service.fetchNeutroSlices() shouldBe setOf(StrategyTicker("Top Dividendos", "PETR4"))
    }

    "fetchNeutroSlices contributes nothing for a strategy with no active edition" {
        coEvery { strategies.fetchAll() } returns listOf(strategy)
        coEvery { editions.fetchLatestActive(1, null) } returns null

        service.fetchNeutroSlices() shouldBe emptySet()
    }

    "importEdition is null when the strategy does not exist" {
        coEvery { strategies.fetchById(404) } returns null

        service.importEdition(404, report) shouldBe null

        transaction.count shouldBe 0
        coVerify(exactly = 0) { editions.saveStaged(any(), any(), any(), any()) }
    }

    "importEdition stages the report with the diff against the previous active edition" {
        coEvery { strategies.fetchById(1) } returns strategy
        coEvery { editions.fetchLatestActive(1, SEPTEMBER) } returns previousEdition
        coEvery { editions.saveStaged(any(), any(), any(), any()) } returns stagedEdition

        service.importEdition(1, report) shouldBe EditionImport("XpEquityCarteira", stagedEdition)

        coVerify(exactly = 1) {
            editions.saveStaged(
                1,
                SEPTEMBER,
                match { it == listOf(petr4("60.00"), cxse3("40.00")) },
                match {
                    it == EditionDiff(
                        entered = listOf(DiffWeight("CXSE3", BigDecimal("40.00"))),
                        exited = listOf(DiffWeight("VALE3", BigDecimal("0.00"))),
                        changed = listOf(
                            DiffChange(
                                ticker = "PETR4",
                                weightBefore = BigDecimal("50.00"),
                                weightAfter = BigDecimal("60.00"),
                                ratingBefore = Rating.BUY,
                                ratingAfter = Rating.BUY,
                            ),
                        ),
                    )
                },
            )
        }
        transaction.count shouldBe 1
    }

    "importEdition stages a first edition with no diff" {
        coEvery { strategies.fetchById(1) } returns strategy
        coEvery { editions.fetchLatestActive(1, SEPTEMBER) } returns null
        coEvery { editions.saveStaged(any(), any(), any(), any()) } returns stagedEdition

        service.importEdition(1, report)

        coVerify(exactly = 1) {
            editions.saveStaged(
                1,
                SEPTEMBER,
                match { it == listOf(petr4("60.00"), cxse3("40.00")) },
                null,
            )
        }
    }

    "activateEdition is null when no edition is staged for the month" {
        coEvery { strategies.fetchById(1) } returns strategy
        coEvery { editions.fetch(1, SEPTEMBER) } returns null

        service.activateEdition(1, SEPTEMBER, listOf(petr4("100.00"))) shouldBe null

        coVerify(exactly = 0) { editions.activate(any(), any(), any()) }
    }

    "activateEdition recomputes the diff against the previous active edition and activates" {
        coEvery { strategies.fetchById(1) } returns strategy
        coEvery { editions.fetch(1, SEPTEMBER) } returns stagedEdition
        coEvery { editions.fetchLatestActive(1, SEPTEMBER) } returns previousEdition
        coEvery { editions.activate(9, any(), any()) } returns activatedEdition

        service.activateEdition(1, SEPTEMBER, listOf(petr4("60.00"), cxse3("40.00"))) shouldBe activatedEdition

        coVerify(exactly = 1) {
            editions.activate(
                9,
                match { it == listOf(petr4("60.00"), cxse3("40.00")) },
                match<EditionDiff> { it.entered.single().ticker == "CXSE3" && it.exited.single().ticker == "VALE3" },
            )
        }
        transaction.count shouldBe 1
    }

    "activateEdition refuses entries whose weights do not sum to 100" {
        coEvery { strategies.fetchById(1) } returns strategy
        coEvery { editions.fetch(1, SEPTEMBER) } returns stagedEdition

        val error = runCatching { service.activateEdition(1, SEPTEMBER, listOf(petr4("60.00"), cxse3("30.00"))) }
            .exceptionOrNull()

        (error is InvalidEditionException) shouldBe true
        transaction.count shouldBe 0
        coVerify(exactly = 0) { editions.activate(any(), any(), any()) }
    }

    "activateEdition refuses an edition that is already active" {
        coEvery { strategies.fetchById(1) } returns strategy
        coEvery { editions.fetch(1, SEPTEMBER) } returns stagedEdition.copy(status = EditionStatus.ACTIVE)

        val error = runCatching { service.activateEdition(1, SEPTEMBER, listOf(petr4("100.00"))) }
            .exceptionOrNull()

        (error is EditionAlreadyActiveException) shouldBe true
        coVerify(exactly = 0) { editions.activate(any(), any(), any()) }
    }

    "activateEdition normalises the reviewed tickers" {
        coEvery { strategies.fetchById(1) } returns strategy
        coEvery { editions.fetch(1, SEPTEMBER) } returns stagedEdition
        coEvery { editions.fetchLatestActive(1, SEPTEMBER) } returns null
        coEvery { editions.activate(9, any(), any()) } returns activatedEdition

        service.activateEdition(1, SEPTEMBER, listOf(EditionEntry("PETR4F", BigDecimal("100.00"))))

        coVerify(exactly = 1) {
            editions.activate(9, match { it.single().ticker == "PETR4" }, any())
        }
    }
})

private val SEPTEMBER = YearMonth.parse("2026-09")

private fun petr4(weight: String) = EditionEntry(
    ticker = "PETR4",
    weight = BigDecimal(weight),
    rating = Rating.BUY,
)

private fun cxse3(weight: String) = EditionEntry(
    ticker = "CXSE3",
    weight = BigDecimal(weight),
    rating = Rating.BUY,
)

private val strategy = Strategy(
    id = 1,
    name = "Top Dividendos",
    assetClass = AssetClass.ACOES,
    weight = BigDecimal("34.00"),
)

private val creation = StrategyCreation(
    name = "Top Dividendos",
    assetClass = AssetClass.ACOES,
    weight = BigDecimal("34.00"),
)

private val report = ParsedStrategyReport(
    referenceMonth = SEPTEMBER,
    parser = "XpEquityCarteira",
    entries = listOf(
        EditionEntry("PETR4F", BigDecimal("60.00"), Rating.BUY),
        EditionEntry("CXSE3", BigDecimal("40.00"), Rating.BUY),
    ),
)

private val previousEdition = EditionDetail(
    id = 4,
    strategyId = 1,
    referenceMonth = YearMonth.parse("2026-08"),
    status = EditionStatus.ACTIVE,
    diffFromPrevious = null,
    entries = listOf(
        EditionEntry("PETR4", BigDecimal("50.00"), Rating.BUY),
        EditionEntry("VALE3", BigDecimal("50.00"), Rating.NEUTRAL),
    ),
)

private val stagedEdition = EditionDetail(
    id = 9,
    strategyId = 1,
    referenceMonth = SEPTEMBER,
    status = EditionStatus.STAGED,
    diffFromPrevious = null,
    entries = listOf(EditionEntry("PETR4", BigDecimal("60.00"), Rating.BUY)),
)

private val activatedEdition = stagedEdition.copy(status = EditionStatus.ACTIVE)

private class CountingTransaction : ITransactionTemplate {
    var count = 0

    override suspend fun <T> execute(block: suspend () -> T): T {
        count++
        return block()
    }
}
