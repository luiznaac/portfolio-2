package dev.agner.portfolio.usecase.corporateaction

import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.commons.EventLevel
import dev.agner.portfolio.usecase.commons.TelemetryAttributes
import dev.agner.portfolio.usecase.commons.event
import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.configuration.JsonMapper
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiFetch
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiIncome
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiRename
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiStockAction
import dev.agner.portfolio.usecase.corporateaction.gateway.BrapiWindow
import dev.agner.portfolio.usecase.corporateaction.gateway.IBrapiCorporateActionSource
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSync
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSyncCreation
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionSyncRepository
import dev.agner.portfolio.usecase.income.IncomeService
import dev.agner.portfolio.usecase.income.model.IncomeCreation
import dev.agner.portfolio.usecase.income.model.IncomeType
import dev.agner.portfolio.usecase.income.model.Provenance
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.tickeralias.TickerAliasResolver
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock

// The explicit, one-off brapi sync behind Importações · Ações corporativas. It asks the provider for
// the renames (free endpoint) and the stock and FII events (Pro) over the ledger's whole window,
// maps them onto the same corporate-action and income shapes an import produces, and persists the
// parsed rows, the raw payload and an audit row. There is no scheduler and no per-read call: the
// owner runs it, and the app keeps working from the database afterwards.
@Service
class CorporateActionSyncService(
    private val source: IBrapiCorporateActionSource,
    private val trades: ITradeRepository,
    private val catalog: CatalogService,
    private val aliases: TickerAliasResolver,
    private val positions: PositionService,
    private val corporateActions: CorporateActionService,
    private val incomes: IncomeService,
    private val syncs: ICorporateActionSyncRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val log = logger()

    // The page reads the last run so the report survives a reload; a run that has never happened
    // answers null and the action panel shows only the button.
    suspend fun latest(): CorporateActionSync? = syncs.fetchLatest()

    suspend fun sync(): CorporateActionSync {
        val startedAt = LocalDateTime.now(clock)
        val asOf = LocalDate.today(clock)
        val ledger = trades.fetchAll()
        val windowFrom = ledger.minOfOrNull { it.date } ?: asOf
        val window = BrapiWindow(from = windowFrom, to = asOf)
        // Every code the paper traded under is queried: a rename means the old code carries the
        // history and the new one the current data, and brapi keeps each class's history apart.
        val tickers = ledger.map { it.ticker }
            .distinct()
            .flatMap { aliases.aliasesOf(it) }
            .distinct()
            .sorted()
        val byEndpoint = splitByKind(tickers)

        val renames = fetchBatches(RENAMES_ENDPOINT, tickers, { source.fetchRenames(it) }) { it }
        val stock = fetchBatches(STOCK_ENDPOINT, byEndpoint.stock, { source.fetchStockEvents(it, window) }) {
            listOf(it)
        }
        val funds = fetchBatches(FUND_ENDPOINT, byEndpoint.fund, { source.fetchFundEvents(it, window) }) { it }

        val cashDividends = stock.items.flatMap { it.cashDividends }
        val stockActions = stock.items.flatMap { it.stockDividends }
        val fundEvents = funds.items

        val actionCreations = renames.items.map { it.toCreation() } +
            stockActions.mapNotNull { it.toCreation() } +
            fundEvents.mapNotNull { it.toCreation() }
        // The events land before the income is mapped: a split synced in this run is part of the
        // book the entitlement quantity replays, so a post-split dividend is not paid on a stale
        // pre-split quantity.
        val actions = corporateActions.applyBrapi(actionCreations)
        val incomeCreations = cashDividends.mapNotNull { it.toIncome() } +
            fundEvents.mapNotNull { it.toIncome() }
        val importedIncome = incomes.importEvents(incomeCreations).size
        val failedTickers = (renames.failed + stock.failed + funds.failed).distinct().sorted()

        val report = syncs.save(
            CorporateActionSyncCreation(
                startedAt = startedAt,
                asOf = asOf,
                windowFrom = windowFrom,
                windowTo = asOf,
                tickerCount = tickers.size,
                fetched = renames.items.size + cashDividends.size + stockActions.size + fundEvents.size,
                applied = actions.applied,
                parked = actions.parked,
                income = importedIncome,
                failedTickers = failedTickers,
                rawPayload = JsonMapper.mapper.writeValueAsString(
                    renames.payloads + stock.payloads + funds.payloads,
                ),
            ),
        )
        log.event(
            "brapi.sync",
            TelemetryAttributes.BRAPI_TICKER_COUNT to report.tickerCount,
            TelemetryAttributes.BRAPI_FETCHED to report.fetched,
            TelemetryAttributes.BRAPI_APPLIED to report.applied,
            TelemetryAttributes.BRAPI_PARKED to report.parked,
            TelemetryAttributes.BRAPI_INCOME to report.income,
            TelemetryAttributes.BRAPI_FAILED_COUNT to failedTickers.size,
            level = if (failedTickers.isEmpty()) EventLevel.INFO else EventLevel.WARN,
        )
        return report
    }

    // The stock endpoint refuses a FII ticker and the FII endpoint refuses a stock one, so the
    // catalog's asset kind picks the batch each code belongs to. An unclassified ticker takes the
    // stock endpoint; a refusal then surfaces as that ticker's failure, never a silent drop.
    private suspend fun splitByKind(tickers: List<String>): TickersByEndpoint {
        val kindByTicker = catalog.fetchAll().associate { it.ticker to it.assetKind }
        val canonicalByTicker = aliases.canonicalAll(tickers)
        val funds = tickers.filter { kindByTicker[canonicalByTicker.getValue(it)] == AssetKind.FII }
        return TickersByEndpoint(stock = tickers - funds.toSet(), fund = funds)
    }

    // One provider failure is that batch's failure, not the run's: the failed tickers are reported
    // and the batches that answered still land. A credential failure (BrapiSyncException) is not
    // caught here — it aborts the run loudly.
    private suspend fun <T, R> fetchBatches(
        endpoint: String,
        tickers: List<String>,
        call: suspend (List<String>) -> BrapiFetch<T>,
        flatten: (T) -> List<R>,
    ): Batches<R> = tickers.chunked(BRAPI_BATCH_SIZE).fold(Batches()) { batches, batch ->
        try {
            val fetch = call(batch)
            Batches(
                payloads = batches.payloads + BrapiPayload(endpoint, fetch.payload),
                items = batches.items + flatten(fetch.items),
                failed = batches.failed,
            )
        } catch (e: BrapiFetchException) {
            log.event(
                "brapi.batch_failed",
                TelemetryAttributes.BRAPI_ENDPOINT to endpoint,
                TelemetryAttributes.BRAPI_TICKERS to batch.joinToString(","),
                TelemetryAttributes.BRAPI_REASON to e.reason,
                level = EventLevel.WARN,
            )
            batches.copy(failed = batches.failed + batch)
        }
    }

    private fun BrapiRename.toCreation() = CorporateActionCreation(
        ticker = oldSymbol,
        kind = CorporateActionKind.TICKER_CHANGE,
        exDate = effectiveDate,
        targetTicker = canonicalSymbol,
        ratio = conversionRatio,
        source = CorporateActionSource.BRAPI,
        rawType = "brapi rename",
    )

    // brapi states a stock event as new shares per old share. A split is that factor; a reverse
    // split is its reciprocal (the engine divides by the ratio); a bonificação is the extra
    // fraction above one. A bonificação carries no capitalised per-share value, so it reaches the
    // engine incomplete and parks for review.
    private fun BrapiStockAction.toCreation(): CorporateActionCreation? {
        val kind = when (label.labelKey()) {
            "DESDOBRAMENTO" -> CorporateActionKind.SPLIT
            "GRUPAMENTO" -> CorporateActionKind.REVERSE_SPLIT
            "BONIFICACAO" -> CorporateActionKind.BONUS_SHARES
            else -> return null
        }
        return CorporateActionCreation(
            ticker = symbol,
            kind = kind,
            exDate = exDate,
            approvalDate = approvedOn,
            ratio = kind.ratioOf(factor),
            source = CorporateActionSource.BRAPI,
            rawType = label,
        )
    }

    private fun BrapiIncome.toCreation(): CorporateActionCreation? {
        if (label.labelKey() != "AMORTIZACAO") return null
        return CorporateActionCreation(
            ticker = symbol,
            kind = CorporateActionKind.AMORTIZATION,
            exDate = exDate,
            declaredValuePerShare = rawRate ?: rate,
            source = CorporateActionSource.BRAPI,
            rawType = label,
        )
    }

    // The amount is per share, so the ledger total is that rate times the quantity the holder had on
    // the data-com (or, absent one, the ex-date). `rawRate` is the amount actually paid; `rate` is
    // on the split-adjusted scale, so a movement built from it is marked estimated.
    private suspend fun BrapiIncome.toIncome(): IncomeCreation? {
        val type = when (label.labelKey()) {
            "JCP" -> IncomeType.JCP
            "DIVIDENDO" -> IncomeType.DIVIDEND
            "RENDIMENTO" -> IncomeType.FUND_INCOME
            else -> return null
        }
        val date = paymentDate ?: exDate ?: return null
        return incomeOf(
            ticker = symbol,
            type = type,
            date = date,
            entitlementDate = lastDatePrior ?: exDate ?: date,
            perShare = rawRate ?: rate,
            estimated = rawRate == null,
        )
    }

    private suspend fun incomeOf(
        ticker: String,
        type: IncomeType,
        date: LocalDate,
        entitlementDate: LocalDate,
        perShare: BigDecimal?,
        estimated: Boolean,
    ): IncomeCreation? {
        val rate = perShare?.takeIf { it.signum() > 0 } ?: return null
        val quantity = positions.positionOn(ticker, entitlementDate).quantity
        if (quantity.signum() <= 0) return null
        val gross = rate.multiply(quantity)
        return IncomeCreation(
            ticker = ticker,
            date = date,
            type = type,
            grossAmount = gross,
            netAmount = gross,
            provenance = Provenance.BRAPI,
            estimated = estimated,
        )
    }

    private data class TickersByEndpoint(
        val stock: List<String>,
        val fund: List<String>,
    )

    private data class Batches<T>(
        val payloads: List<BrapiPayload> = emptyList(),
        val items: List<T> = emptyList(),
        val failed: List<String> = emptyList(),
    )

    private data class BrapiPayload(val endpoint: String, val body: String)

    private companion object {
        const val BRAPI_BATCH_SIZE = 20
        const val RATIO_SCALE = 8
        const val RENAMES_ENDPOINT = "tickers/renames"
        const val STOCK_ENDPOINT = "stocks/dividends"
        const val FUND_ENDPOINT = "fii/dividends"

        fun inverseOf(factor: BigDecimal?): BigDecimal? =
            factor?.takeIf { it.signum() > 0 }?.let { BigDecimal.ONE.divide(it, RATIO_SCALE, RoundingMode.HALF_UP) }

        fun CorporateActionKind.ratioOf(factor: BigDecimal?): BigDecimal? = when (this) {
            CorporateActionKind.SPLIT -> factor
            CorporateActionKind.REVERSE_SPLIT -> inverseOf(factor)
            CorporateActionKind.BONUS_SHARES -> factor?.subtract(BigDecimal.ONE)
            else -> null
        }?.takeIf { it.signum() > 0 }

        // brapi's labels come with or without the Portuguese diacritics depending on the endpoint.
        fun String.labelKey(): String = uppercase()
            .replace("Ç", "C")
            .replace("Ã", "A")
            .trim()
    }
}
