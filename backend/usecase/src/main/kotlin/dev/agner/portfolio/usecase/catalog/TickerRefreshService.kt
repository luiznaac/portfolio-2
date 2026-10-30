package dev.agner.portfolio.usecase.catalog

import dev.agner.portfolio.usecase.catalog.gateway.CotahistFetchException
import dev.agner.portfolio.usecase.catalog.gateway.ICotahistSource
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.model.CotahistRow
import dev.agner.portfolio.usecase.catalog.model.TickerRefreshFailure
import dev.agner.portfolio.usecase.catalog.model.TickerRefreshResult
import dev.agner.portfolio.usecase.catalog.model.UnclassifiedReason
import dev.agner.portfolio.usecase.catalog.model.UnclassifiedTicker
import dev.agner.portfolio.usecase.catalog.model.effectiveAssetClass
import dev.agner.portfolio.usecase.commons.mapToSet
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.quotes.MonthlyCloses
import dev.agner.portfolio.usecase.quotes.QuoteService
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.time.Clock

// The single combined catalog-and-quotes refresh use case (the UI's "update catalog and quotes"
// action): one daily file feeds both the catalog and the latest closes, then the ledger's completed
// years supply one close per ticker per month for the pre-planning series. Applying an import never
// calls this — the owner runs it from the data panel.
@Service
class TickerRefreshService(
    private val catalogService: CatalogService,
    private val quoteService: QuoteService,
    private val source: ICotahistSource,
    private val positions: IPositionRepository,
    private val trades: ITradeRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend fun refresh(): TickerRefreshResult {
        val heldTickers = positions.fetchAll()
            .filter { it.quantity.signum() > 0 }
            .mapToSet { it.ticker }
        val ledger = trades.fetchAll()
        val ledgerTickers = ledger.mapToSet { it.ticker }

        val daily = fetchDaily()
        val catalogCount = if (daily.rows.isEmpty()) 0 else catalogService.refresh(daily.rows)
        val yearLoads = ledgerYears(ledger).map { loadYear(it, ledgerTickers) }
        val tickerFailures = heldTickers.sorted()
            .filter { quoteService.latestClose(it) == null }
            .map { TickerRefreshFailure.Ticker(it, NO_QUOTE_REASON) }
        val failed = listOfNotNull(daily.failure) + yearLoads.mapNotNull { it.failure } + tickerFailures

        val catalogByTicker = catalogService.fetchAll().associateBy { it.ticker }
        val latestTickers = daily.rows.mapToSet { it.ticker }
        val unclassified = heldTickers.sorted().mapNotNull { ticker ->
            classifyGap(ticker, catalogByTicker[ticker], latestTickers)
        }

        return TickerRefreshResult(
            catalog = catalogCount,
            quotes = quoteService.storeDailyCloses(daily.rows) + yearLoads.sumOf { it.closes.count },
            asOf = daily.rows.maxOfOrNull { it.date } ?: yearLoads.mapNotNull { it.closes.latestDate }.maxOrNull(),
            failed = failed,
            unclassified = unclassified,
        )
    }

    private suspend fun fetchDaily(): DailyFeed = try {
        DailyFeed(rows = source.fetchTradingDay())
    } catch (e: CotahistFetchException) {
        DailyFeed(rows = emptyList(), failure = TickerRefreshFailure.Source(DAILY_SOURCE, e.reason()))
    }

    // One annual file per year, from the ledger's earliest trade through the current year: B3
    // publishes the current year as a year-to-date file, so its completed months are as reachable
    // as an older year's. The daily file above is still the live edge.
    private suspend fun loadYear(year: Int, ledgerTickers: Set<String>): YearLoad = try {
        YearLoad(closes = quoteService.storeMonthlyCloses(source.fetchYear(year), ledgerTickers))
    } catch (e: CotahistFetchException) {
        YearLoad(
            closes = MonthlyCloses(count = 0, latestDate = null),
            failure = TickerRefreshFailure.Source(annualSource(year), e.reason()),
        )
    }

    private fun ledgerYears(ledger: List<Trade>): IntRange {
        val earliest = ledger.minOfOrNull { it.date.year } ?: return IntRange.EMPTY
        return earliest..LocalDate.today(clock).year
    }

    private fun classifyGap(
        ticker: String,
        entry: CatalogEntry?,
        latestTickers: Set<String>,
    ): UnclassifiedTicker? {
        if (entry?.effectiveAssetClass != null) return null

        // A ticker the latest file lists is a mapping gap; one it does not list is absent (delisted,
        // or the daily file could not be read at all).
        val reason = if (ticker in latestTickers) {
            UnclassifiedReason.UNHANDLED_BDI_CODE
        } else {
            UnclassifiedReason.ABSENT_FROM_LATEST_FILE
        }

        return UnclassifiedTicker(ticker, reason)
    }

    private fun Throwable.reason(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private data class DailyFeed(
        val rows: List<CotahistRow>,
        val failure: TickerRefreshFailure.Source? = null,
    )

    private data class YearLoad(
        val closes: MonthlyCloses,
        val failure: TickerRefreshFailure.Source? = null,
    )

    private companion object {
        const val DAILY_SOURCE = "cotahist-daily"
        const val NO_QUOTE_REASON = "no quote available"

        fun annualSource(year: Int) = "cotahist-annual-$year"
    }
}
