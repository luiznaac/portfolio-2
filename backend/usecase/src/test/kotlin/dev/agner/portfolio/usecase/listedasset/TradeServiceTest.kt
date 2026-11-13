package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.buy
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerCursor
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerFilter
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.sell
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class TradeServiceTest : StringSpec({
    val repository = mockk<ITradeRepository>(relaxed = true)
    val positionService = mockk<PositionService>(relaxed = true)
    val transaction = CountingTransaction()
    val gaps = mockk<GapService>(relaxed = true)
    val service = TradeService(repository, positionService, gaps, transaction)

    beforeTest {
        clearAllMocks()
        transaction.count = 0
    }

    "createManual normalizes the ticker and saves a manual trade at the next ordinal" {
        coEvery { repository.fetchByKey(any()) } returns emptyList()
        val captured = slot<TradeCreation>()
        coEvery { repository.save(capture(captured), any(), any()) } returns savedManual

        service.createManual(creation(ticker = "petr4f")) shouldBe savedManual

        captured.captured.ticker shouldBe "PETR4"
        coVerify(exactly = 1) { repository.save(any(), TradeSource.MANUAL, 1) }
    }

    "createManual assigns the next ordinal when manual trades already share the tuple" {
        coEvery { repository.fetchByKey(any()) } returns listOf(manualTrade(1), manualTrade(2))
        coEvery { repository.save(any(), any(), any()) } returns savedManual

        service.createManual(creation())

        coVerify(exactly = 1) { repository.save(any(), TradeSource.MANUAL, 3) }
    }

    "createManual refuses a trade that dupes an already-imported B3 row" {
        coEvery { repository.fetchByKey(any()) } returns listOf(importedTrade(1))

        shouldThrow<DuplicateTradeException> { service.createManual(creation()) }

        coVerify(exactly = 0) { repository.save(any(), any(), any()) }
    }

    "createManual reprojects the position inside the transaction" {
        coEvery { repository.fetchByKey(any()) } returns emptyList()
        coEvery { repository.save(any(), any(), any()) } returns savedManual

        service.createManual(creation())

        coVerify(exactly = 1) { positionService.reproject("PETR4") }
        transaction.count shouldBe 1
    }

    "reconcile creates the occurrences past the ledger at the next ordinal, normalizing the ticker" {
        coEvery { repository.fetchByKey(any()) } returns emptyList()
        coEvery { repository.save(any(), any(), any()) } returns savedImported

        service.reconcile(creation(ticker = "alup11f").naturalKey(), listOf(creation(ticker = "alup11f")))

        coVerify(exactly = 1) { repository.save(match { it.ticker == "ALUP11" }, TradeSource.B3_IMPORT, 1) }
    }

    "reconcile skips the occurrences the ledger already holds and creates the ones past it" {
        coEvery { repository.fetchByKey(any()) } returns listOf(importedTrade(1))
        coEvery { repository.save(any(), any(), any()) } returns savedImported

        // Two identical rows: the first is already in the ledger (dedup), the second is a new
        // occurrence and is created at ordinal 2.
        service.reconcile(creation().naturalKey(), listOf(creation(), creation()))

        coVerify(exactly = 1) { repository.save(any(), TradeSource.B3_IMPORT, 2) }
    }

    "reconcile marks an unconfirmed manual trade that an imported row matches as confirmed by B3" {
        val manual = manualTrade(1)
        coEvery { repository.fetchByKey(any()) } returns listOf(manual)

        service.reconcile(creation().naturalKey(), listOf(creation()))

        coVerify(exactly = 1) { repository.confirmByB3(manual.id) }
        coVerify(exactly = 0) { repository.save(any(), any(), any()) }
    }

    "reconcile leaves an already-confirmed manual trade alone" {
        coEvery { repository.fetchByKey(any()) } returns listOf(manualTrade(1).copy(confirmedByB3 = true))

        service.reconcile(creation().naturalKey(), listOf(creation()))

        coVerify(exactly = 0) { repository.confirmByB3(any()) }
        coVerify(exactly = 0) { repository.save(any(), any(), any()) }
    }

    "reconcile reprojects the position only when it wrote a new occurrence" {
        coEvery { repository.fetchByKey(any()) } returns emptyList()
        coEvery { repository.save(any(), any(), any()) } returns savedImported

        service.reconcile(creation().naturalKey(), listOf(creation()))

        coVerify(exactly = 1) { positionService.reproject("PETR4") }
    }

    "reconcile leaves the position untouched when nothing new was written" {
        coEvery { repository.fetchByKey(any()) } returns listOf(importedTrade(1))

        service.reconcile(creation().naturalKey(), listOf(creation()))

        coVerify(exactly = 0) { positionService.reproject(any()) }
    }

    "fetchAllBy looks the ticker up under its normalized key" {
        coEvery { repository.fetchAllBy(any()) } returns emptyList()

        service.fetchAllBy("petr4f")

        coVerify(exactly = 1) { repository.fetchAllBy("PETR4") }
    }

    "fetchLedger normalizes the ticker and exposes the filter's total beside the page" {
        val captured = slot<TradeLedgerFilter>()
        coEvery { repository.fetchLedger(capture(captured)) } returns
            listOf(
                sell(id = 3, date = "2026-10-03"),
                buy(id = 2, date = "2026-10-02"),
                buy(id = 1, date = "2026-10-01"),
            )
        coEvery { repository.countLedger(any()) } returns 7
        coEvery { repository.sumLedgerFinancial(any()) } returns BigDecimal("1234.56")

        val page = service.fetchLedger(TradeLedgerFilter(ticker = "petr4f", limit = 2))

        captured.captured.ticker shouldBe "PETR4"
        page.rows.map { it.id } shouldBe listOf(3, 2)
        page.nextCursor shouldBe "2026-10-02:2"
        page.total shouldBe 7
        page.financialTotal shouldBe BigDecimal("1234.56")
    }

    "fetchLedger leaves the cursor absent on the last page" {
        coEvery { repository.fetchLedger(any()) } returns
            listOf(buy(id = 2, date = "2026-10-02"), buy(id = 1, date = "2026-10-01"))
        coEvery { repository.countLedger(any()) } returns 2
        coEvery { repository.sumLedgerFinancial(any()) } returns BigDecimal("200")

        val page = service.fetchLedger(TradeLedgerFilter(limit = 2))

        page.rows.map { it.id } shouldBe listOf(2, 1)
        page.nextCursor shouldBe null
        page.total shouldBe 2
        page.financialTotal shouldBe BigDecimal("200")
    }

    "fetchLedger sends a blank ticker as no ticker filter" {
        val captured = slot<TradeLedgerFilter>()
        coEvery { repository.fetchLedger(capture(captured)) } returns emptyList()
        coEvery { repository.countLedger(any()) } returns 0
        coEvery { repository.sumLedgerFinancial(any()) } returns BigDecimal.ZERO

        service.fetchLedger(TradeLedgerFilter(ticker = "   "))

        captured.captured.ticker shouldBe null
    }

    "fetchLedger passes the cursor and side through unchanged" {
        val captured = slot<TradeLedgerFilter>()
        coEvery { repository.fetchLedger(capture(captured)) } returns emptyList()
        coEvery { repository.countLedger(any()) } returns 0
        coEvery { repository.sumLedgerFinancial(any()) } returns BigDecimal.ZERO
        val cursor = TradeLedgerCursor(LocalDate.parse("2026-10-02"), 2)

        service.fetchLedger(TradeLedgerFilter(side = TradeSide.SELL, after = cursor))

        captured.captured.side shouldBe TradeSide.SELL
        captured.captured.after shouldBe cursor
    }
})

private class CountingTransaction : ITransactionTemplate {
    var count = 0

    override suspend fun <T> execute(block: suspend () -> T): T {
        count++
        return block()
    }
}

private fun creation(ticker: String = "PETR4") = TradeCreation(
    ticker = ticker,
    side = TradeSide.BUY,
    date = LocalDate.parse("2026-10-14"),
    venue = "VISTA",
    quantity = BigDecimal("100"),
    unitPrice = BigDecimal("48.92"),
    institutionId = 1,
)

private fun manualTrade(ordinal: Int) = Trade.Buy(
    id = 7,
    ticker = "PETR4",
    date = LocalDate.parse("2026-10-14"),
    venue = "VISTA",
    quantity = BigDecimal("100"),
    unitPrice = BigDecimal("48.92"),
    institutionId = 1,
    fee = null,
    source = TradeSource.MANUAL,
    confirmedByB3 = false,
    ordinal = ordinal,
)

private fun importedTrade(ordinal: Int) = manualTrade(ordinal).copy(source = TradeSource.B3_IMPORT)

private val savedManual = manualTrade(1)
private val savedImported = importedTrade(1)
