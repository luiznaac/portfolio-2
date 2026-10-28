package dev.agner.portfolio.usecase.history

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.history.model.BackfillReport
import dev.agner.portfolio.usecase.listedasset.GapService
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.repository.IGapRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.quotes.QuoteService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class BackfillServiceTest : StringSpec({

    "retires an OPENING seed whose quantity the imported history covers" {
        val seed = openingSeed(quantity = "100")
        val imported = listOf(
            buy(id = 2, date = "2026-01-10", quantity = "60"),
            buy(id = 3, date = "2026-02-10", quantity = "40"),
        )
        val repository = repositoryWith(seed = seed, imported = imported)
        val service = service(repository)

        val report = service.backfill(emptyList())

        report.absorbedSeeds shouldBe 1
        coVerify { repository.delete(seed.id) }
        coVerify(exactly = 0) { repository.absorb(any(), any(), any()) }
    }

    "reduces a partially covered OPENING seed to the uncovered quantity" {
        val seed = openingSeed(quantity = "100")
        val imported = listOf(buy(id = 2, date = "2026-01-10", quantity = "40"))
        val repository = repositoryWith(seed = seed, imported = imported)
        val service = service(repository)

        val report = service.backfill(emptyList())

        report.adjustedSeeds shouldBe 1
        coVerify(exactly = 0) { repository.delete(any()) }
        coVerify { repository.absorb(seed.id, BigDecimal("60"), BigDecimal("40")) }
    }

    "does not subtract the same history twice when the backfill reruns" {
        val seed = openingSeed(quantity = "60", absorbedQuantity = "40")
        val imported = listOf(buy(id = 2, date = "2026-01-10", quantity = "40"))
        val repository = repositoryWith(seed = seed, imported = imported)
        val service = service(repository)

        service.backfill(emptyList())

        coVerify(exactly = 0) { repository.absorb(any(), any(), any()) }
        coVerify(exactly = 0) { repository.delete(any()) }
    }

    "absorbs only the newly covered quantity on a later run" {
        val seed = openingSeed(quantity = "60", absorbedQuantity = "40")
        val imported = listOf(buy(id = 2, date = "2026-01-10", quantity = "60"))
        val repository = repositoryWith(seed = seed, imported = imported)
        val service = service(repository)

        service.backfill(emptyList())

        coVerify { repository.absorb(seed.id, BigDecimal("40"), BigDecimal("60")) }
    }

    "keeps an OPENING seed when the history does not reach its date" {
        val seed = openingSeed(quantity = "100", date = "2026-01-01")
        val imported = listOf(buy(id = 2, date = "2026-02-10", quantity = "100"))
        val repository = repositoryWith(seed = seed, imported = imported)
        val service = service(repository)

        val report = service.backfill(emptyList())

        report.absorbedSeeds shouldBe 0
        report.adjustedSeeds shouldBe 0
        coVerify(exactly = 0) { repository.delete(any()) }
        coVerify(exactly = 0) { repository.absorb(any(), any(), any()) }
    }

    "ignores a manual record that was not confirmed by B3" {
        val seed = openingSeed(quantity = "100")
        val imported = listOf(buy(id = 2, date = "2026-01-10", quantity = "100", source = TradeSource.MANUAL))
        val repository = repositoryWith(seed = seed, imported = imported)
        val service = service(repository)

        service.backfill(emptyList())

        coVerify(exactly = 0) { repository.delete(any()) }
    }

    "reports the backfill outcome" {
        val repository = repositoryWith(seed = openingSeed(quantity = "100"), imported = emptyList())
        val service = service(repository)

        service.backfill(emptyList()) shouldBe BackfillReport(
            absorbedSeeds = 0,
            adjustedSeeds = 0,
            gapsCreated = 0,
            gapsReplaced = 0,
            positionsReprojected = 0,
        )
    }
})

private fun service(repository: ITradeRepository): BackfillService {
    val transaction = mockk<ITransactionTemplate>()
    coEvery { transaction.execute<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
    val positions = mockk<PositionService>(relaxed = true)
    val gaps = mockk<IGapRepository>(relaxed = true)
    val quotes = mockk<QuoteService>(relaxed = true)
    return BackfillService(
        trades = repository,
        positions = positions,
        gaps = gaps,
        gapService = mockk<GapService>(relaxed = true),
        corporateActions = mockk<ICorporateActionRepository>(relaxed = true).also {
            coEvery { it.fetchAll() } returns emptyList<CorporateAction>()
        },
        institutions = mockk(relaxed = true),
        quotes = quotes,
        transaction = transaction,
    )
}

private fun repositoryWith(seed: Trade, imported: List<Trade>): ITradeRepository {
    val repository = mockk<ITradeRepository>()
    coEvery { repository.fetchAllBySource(TradeSource.OPENING) } returns listOf(seed)
    coEvery { repository.fetchAllBy(seed.ticker.normalizedTicker()) } returns listOf(seed) + imported
    coEvery { repository.fetchAll() } returns (listOf(seed) + imported).groupBy { it.ticker }.map { it.value.first() }
    coEvery { repository.delete(any()) } returns true
    coEvery { repository.absorb(any(), any(), any()) } returns null
    return repository
}

private fun openingSeed(
    quantity: String,
    date: String = "2026-03-01",
    absorbedQuantity: String = "0",
) = Trade.Buy(
    id = 1,
    ticker = "PETR4",
    date = LocalDate.parse(date),
    venue = "VISTA",
    quantity = BigDecimal(quantity),
    unitPrice = BigDecimal("10"),
    institutionId = 1,
    fee = null,
    source = TradeSource.OPENING,
    confirmedByB3 = false,
    ordinal = 1,
    absorbedQuantity = BigDecimal(absorbedQuantity),
)

private fun buy(
    id: Int,
    date: String,
    quantity: String,
    source: TradeSource = TradeSource.B3_IMPORT,
) = Trade.Buy(
    id = id,
    ticker = "PETR4",
    date = LocalDate.parse(date),
    venue = "VISTA",
    quantity = BigDecimal(quantity),
    unitPrice = BigDecimal("10"),
    institutionId = 1,
    fee = null,
    source = source,
    confirmedByB3 = false,
    ordinal = 1,
)
