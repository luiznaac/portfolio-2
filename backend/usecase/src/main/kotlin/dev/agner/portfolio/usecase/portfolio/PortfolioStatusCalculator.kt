package dev.agner.portfolio.usecase.portfolio

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.listedasset.model.heldTickers
import dev.agner.portfolio.usecase.monthlyclose.model.Snapshot
import dev.agner.portfolio.usecase.order.model.SuggestedOrderStatus
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferStatus
import dev.agner.portfolio.usecase.portfolio.model.AttentionItem
import dev.agner.portfolio.usecase.portfolio.model.AttentionKind
import dev.agner.portfolio.usecase.portfolio.model.AttentionObject
import dev.agner.portfolio.usecase.portfolio.model.AttentionSeverity
import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.MonthActionKind
import dev.agner.portfolio.usecase.portfolio.model.MonthBlock
import dev.agner.portfolio.usecase.portfolio.model.MonthNextAction
import dev.agner.portfolio.usecase.portfolio.model.MonthRef
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.portfolio.model.PortfolioDataBlock
import dev.agner.portfolio.usecase.portfolio.model.PortfolioStatus
import dev.agner.portfolio.usecase.portfolio.model.PortfolioStatusInput
import dev.agner.portfolio.usecase.portfolio.model.SnapshotRef
import dev.agner.portfolio.usecase.portfolio.model.StaleQuote
import dev.agner.portfolio.usecase.portfolio.model.StatusRef
import dev.agner.portfolio.usecase.portfolio.model.StatusRefType
import dev.agner.portfolio.usecase.quotes.model.isStale
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// The landing's aggregate arithmetic, pure and side-effect free so every rule is unit-testable
// without a repository or a server. The service gathers the book reads; this class decides what the
// status says — the six plan steps, the next unfinished action, the data-state block and the typed
// attention queue.
object PortfolioStatusCalculator {

    private const val STEPS_TOTAL = 6

    fun compute(input: PortfolioStatusInput): PortfolioStatus {
        val pendingTransfers = input.orders?.transfers.orEmpty()
            .filter { it.status == TransferStatus.PENDING }
        val steps = monthSteps(input, pendingTransfers)
        val data = dataBlock(input)

        return PortfolioStatus(
            asOf = input.asOf,
            openMonth = input.current?.let { MonthRef(it.month.id, it.month.period) },
            lastClose = input.closedMonths.maxByOrNull { it.period }
                ?.let { MonthRef(it.id, it.period) },
            capital = input.allocation.capital,
            invested = input.allocation.actual,
            uninvestedBalance = uninvestedBalance(input),
            deficit = input.allocation.deficit,
            drift = input.allocation.drift,
            month = monthBlock(input, steps, pendingTransfers),
            data = data,
            attention = attention(input, pendingTransfers, data),
        )
    }

    // The Uninvested Balance is Capital minus the priced book. A partial allocation excludes unpriced
    // custody, so the residual is unknown rather than overstated; with no open month there is no
    // Capital to measure against, so it is absent too.
    private fun uninvestedBalance(input: PortfolioStatusInput): BigDecimal? =
        if (input.current == null || input.allocation.partial) {
            null
        } else {
            (input.allocation.capital - input.allocation.actual).defaultScale()
        }

    private fun monthBlock(
        input: PortfolioStatusInput,
        steps: MonthSteps,
        pendingTransfers: List<TransferProposal>,
    ) = MonthBlock(
        open = input.current?.month?.status == MonthStatus.OPEN,
        stepsDone = steps.done,
        stepsTotal = STEPS_TOTAL,
        suggestedOrders = input.orders?.orders?.size ?: 0,
        pendingTransfers = pendingTransfers.size,
        nextAction = nextAction(input.current, steps, pendingTransfers),
    )

    // The six steps run in order, so the first unfinished one names the next action. A rejected
    // transfer is a decision, not a pending one, so it releases the transfer step and the close.
    private fun nextAction(
        current: CurrentMonth?,
        steps: MonthSteps,
        pendingTransfers: List<TransferProposal>,
    ): MonthNextAction? {
        if (current == null) {
            return MonthNextAction(MonthActionKind.OPEN_MONTH, StatusRef(StatusRefType.PORTFOLIO))
        }
        val monthRef = StatusRef(StatusRefType.MONTH, id = current.month.id)
        return when {
            !steps.capital -> MonthNextAction(MonthActionKind.SET_CAPITAL, monthRef)
            !steps.targets -> MonthNextAction(MonthActionKind.SET_TARGETS, monthRef)
            !steps.plan -> MonthNextAction(MonthActionKind.REFRESH_PLAN, monthRef)
            !steps.orders -> MonthNextAction(MonthActionKind.IMPORT_STATEMENT, monthRef)
            pendingTransfers.isNotEmpty() -> MonthNextAction(
                MonthActionKind.DECIDE_TRANSFER,
                StatusRef(
                    type = StatusRefType.TRANSFER,
                    id = pendingTransfers.first().id,
                    ticker = pendingTransfers.first().ticker,
                ),
            )
            !steps.closed -> MonthNextAction(MonthActionKind.CLOSE_MONTH, monthRef)
            else -> null
        }
    }

    private fun monthSteps(
        input: PortfolioStatusInput,
        pendingTransfers: List<TransferProposal>,
    ): MonthSteps {
        val month = input.current?.month
        val orders = input.orders
        val hasTargets = input.current?.targets?.let {
            it.classes.isNotEmpty() || it.strategies.isNotEmpty() ||
                it.subClasses.isNotEmpty() || it.tickers.isNotEmpty()
        } == true
        // A persisted suggestion or transfer is the only evidence the refresh ran: the read carries
        // no separate plan marker. An unplanned trade is not evidence — it is a trade the plan did
        // not ask for, so it can exist before any refresh and never completes the plan step.
        val plan = month != null && orders != null &&
            (orders.orders.isNotEmpty() || orders.transfers.isNotEmpty())

        return MonthSteps(
            capital = month != null && month.capital.signum() > 0,
            targets = month != null && hasTargets,
            plan = plan,
            orders = plan && orders?.orders?.all { it.status != SuggestedOrderStatus.SUGGESTED } == true,
            transfer = plan && pendingTransfers.isEmpty(),
            closed = month?.status == MonthStatus.CLOSED,
        )
    }

    private fun dataBlock(input: PortfolioStatusInput): PortfolioDataBlock {
        val heldQuotes = input.positions.heldTickers().mapNotNull { input.latestQuotes[it] }
        val staleQuotes = heldQuotes
            .filter { it.isStale(input.asOf) }
            .sortedBy { it.ticker }
            .map {
                StaleQuote(
                    ticker = it.ticker,
                    date = it.date,
                    close = it.close,
                    daysOld = daysBetweenInt(it.date, input.asOf),
                )
            }

        return PortfolioDataBlock(
            quotesAsOf = heldQuotes.maxOfOrNull { it.date },
            staleQuotes = staleQuotes,
            openGaps = input.openGaps.size,
            lastSnapshot = input.lastSnapshot?.toRef(),
        )
    }

    // The queue is built as ACTION findings first, then the WATCH ones. Each kind's params carry only
    // the numbers its sentence needs; a missing amount (an unpriced transfer) is omitted, never zeroed.
    private fun attention(
        input: PortfolioStatusInput,
        pendingTransfers: List<TransferProposal>,
        data: PortfolioDataBlock,
    ): List<AttentionItem> {
        val transfers = pendingTransfers.map { transfer ->
            AttentionItem(
                kind = AttentionKind.PENDING_TRANSFER,
                `object` = AttentionObject.MONTH,
                severity = AttentionSeverity.ACTION,
                ref = StatusRef(StatusRefType.TRANSFER, id = transfer.id, ticker = transfer.ticker),
                params = buildMap {
                    put("quantity", transfer.quantity)
                    transfer.amount?.let { put("amount", it) }
                },
            )
        }
        val imports = input.pendingImports.map { import ->
            AttentionItem(
                kind = AttentionKind.STAGED_IMPORT,
                `object` = AttentionObject.IMPORT,
                severity = AttentionSeverity.ACTION,
                ref = StatusRef(StatusRefType.IMPORT, id = import.id),
                params = mapOf("age_days" to daysBetween(import.createdAt.date, input.asOf)),
            )
        }
        val editions = input.stagedEditions.map { edition ->
            AttentionItem(
                kind = AttentionKind.STAGED_EDITION,
                `object` = AttentionObject.STRATEGY,
                severity = AttentionSeverity.ACTION,
                ref = StatusRef(StatusRefType.EDITION, id = edition.id),
                params = mapOf("entry_count" to BigDecimal(edition.entries.size)),
            )
        }
        val reviewActions = input.reviewActions.map { action ->
            AttentionItem(
                kind = AttentionKind.CORPORATE_ACTION_REVIEW,
                `object` = AttentionObject.CORPORATE_ACTION,
                severity = AttentionSeverity.ACTION,
                ref = StatusRef(StatusRefType.CORPORATE_ACTION, id = action.id, ticker = action.ticker),
                params = buildMap {
                    put("age_days", daysBetween(action.exDate, input.asOf))
                    action.ratio?.let { put("ratio", it) }
                    action.declaredValuePerShare?.let { put("declared_value_per_share", it) }
                },
            )
        }
        val unclassified = input.unclassifiedTickers.map { ticker ->
            AttentionItem(
                kind = AttentionKind.UNCLASSIFIED_TICKER,
                `object` = AttentionObject.ASSET,
                severity = AttentionSeverity.ACTION,
                ref = StatusRef(StatusRefType.TICKER, ticker = ticker.ticker),
                params = mapOf("quantity" to ticker.quantity),
            )
        }
        val gaps = input.openGaps.map { gap ->
            AttentionItem(
                kind = AttentionKind.OPEN_GAP,
                `object` = AttentionObject.ASSET,
                severity = AttentionSeverity.ACTION,
                ref = StatusRef(StatusRefType.GAP, id = gap.id, ticker = gap.ticker),
                params = mapOf(
                    "quantity" to gap.quantity,
                    "age_days" to daysBetween(gap.date, input.asOf),
                ),
            )
        }
        val staleQuotes = data.staleQuotes.map { quote ->
            AttentionItem(
                kind = AttentionKind.STALE_QUOTE,
                `object` = AttentionObject.ASSET,
                severity = AttentionSeverity.WATCH,
                ref = StatusRef(StatusRefType.QUOTE, ticker = quote.ticker),
                params = mapOf(
                    "days_old" to BigDecimal(quote.daysOld),
                    "close" to quote.close,
                ),
            )
        }

        return transfers + imports + editions + reviewActions + unclassified + gaps + staleQuotes
    }

    private fun Snapshot.toRef() = SnapshotRef(
        monthId = monthId,
        period = state.period,
        createdAt = createdAt,
    )

    private fun daysBetween(from: LocalDate, to: LocalDate): BigDecimal =
        BigDecimal(daysBetweenInt(from, to))

    private fun daysBetweenInt(from: LocalDate, to: LocalDate): Int =
        (to.toEpochDays() - from.toEpochDays()).toInt()
}

// The six independent completion flags. A step is counted only when its artefact exists for the open
// month; a closed month has no open current month, so its block reports zero steps.
private data class MonthSteps(
    val capital: Boolean,
    val targets: Boolean,
    val plan: Boolean,
    val orders: Boolean,
    val transfer: Boolean,
    val closed: Boolean,
) {
    val done: Int
        get() = listOf(capital, targets, plan, orders, transfer, closed).count { it }
}
