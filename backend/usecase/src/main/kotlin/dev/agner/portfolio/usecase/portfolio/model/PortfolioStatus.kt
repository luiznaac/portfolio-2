package dev.agner.portfolio.usecase.portfolio.model

import dev.agner.portfolio.usecase.catalog.model.ClassificationQueueItem
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.import.model.Import
import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.monthlyclose.model.Snapshot
import dev.agner.portfolio.usecase.order.model.OrderList
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.strategy.model.EditionDetail
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The landing's single aggregate read: the portfolio's standing status, not a screen name. It is
// deliberately typeless prose — every sentence is the frontend's — so the wire carries numbers, ids
// and enum kinds only. `asOf` is the server's calendar date, the reference for freshness; the month
// block reports the open month's six-step progress; `data` reports how much to trust the numbers;
// and `attention` is the derived queue of findings needing an action or a watch.
data class PortfolioStatus(
    val asOf: LocalDate,
    val openMonth: MonthRef?,
    val lastClose: MonthRef?,
    val capital: BigDecimal,
    val invested: BigDecimal,
    val uninvestedBalance: BigDecimal?,
    val deficit: BigDecimal,
    val drift: BigDecimal,
    val month: MonthBlock,
    val data: PortfolioDataBlock,
    val attention: List<AttentionItem>,
)

// The identity of a month without its plan payload — enough to link to the month or a closed record.
data class MonthRef(
    val id: Int,
    val period: YearMonth,
)

// The open month's six-step progress. `nextAction` names the first unfinished step and the object it
// acts on; with no open month it points at opening one. `suggestedOrders` and `pendingTransfers` are
// counts, not lists — the landing only states them, the Plano screens own the detail.
data class MonthBlock(
    val open: Boolean,
    val stepsDone: Int,
    val stepsTotal: Int,
    val suggestedOrders: Int,
    val pendingTransfers: Int,
    val nextAction: MonthNextAction?,
)

data class MonthNextAction(
    val kind: MonthActionKind,
    val ref: StatusRef,
)

// The six plan steps, opening at the first unfinished one. The kinds are the action's vocabulary,
// never a sentence: the frontend renders "Definir capital", "Importar extrato", and so on.
enum class MonthActionKind {
    OPEN_MONTH,
    SET_CAPITAL,
    SET_TARGETS,
    REFRESH_PLAN,
    IMPORT_STATEMENT,
    DECIDE_TRANSFER,
    CLOSE_MONTH,
}

// A typed pointer at the object a finding or action belongs to. `type` is the object kind, `id` the
// stored row when one exists, and `ticker` the identity when the object is a listed asset. The
// frontend builds every link and every sentence from these fields, so a ref never carries prose.
data class StatusRef(
    val type: StatusRefType,
    val id: Int? = null,
    val ticker: String? = null,
)

enum class StatusRefType {
    PORTFOLIO,
    MONTH,
    TRANSFER,
    IMPORT,
    EDITION,
    CORPORATE_ACTION,
    GAP,
    QUOTE,
    TICKER,

    // A Proventos ledger row. A receivable finding carries its id so the Proventos deep link lands in
    // the shared window with that row highlighted.
    INCOME,
}

// The landing's data-state panel: how fresh the quotes are, which held tickers are stale, how many
// gaps are open, and the latest frozen month. `lastSnapshot` is absent when no month was ever closed.
data class PortfolioDataBlock(
    val quotesAsOf: LocalDate?,
    val staleQuotes: List<StaleQuote>,
    val openGaps: Int,
    val lastSnapshot: SnapshotRef?,
)

data class StaleQuote(
    val ticker: String,
    val date: LocalDate,
    val close: BigDecimal,
    val daysOld: Int,
)

data class SnapshotRef(
    val monthId: Int,
    val period: YearMonth,
    val createdAt: LocalDateTime,
)

// One finding the owner should act on (ACTION) or keep an eye on (WATCH). `params` carries the
// numbers the frontend needs to write the sentence; the keys are the kind's own contract and are
// never prose. `object` is backticked because the wire field is literally "object".
data class AttentionItem(
    val kind: AttentionKind,
    val `object`: AttentionObject,
    val severity: AttentionSeverity,
    val ref: StatusRef,
    val params: Map<String, BigDecimal>,
)

enum class AttentionKind {
    PENDING_TRANSFER,
    STAGED_IMPORT,
    STAGED_EDITION,
    CORPORATE_ACTION_REVIEW,
    UNCLASSIFIED_TICKER,
    OPEN_GAP,
    STALE_QUOTE,
}

enum class AttentionObject {
    MONTH,
    IMPORT,
    STRATEGY,
    CORPORATE_ACTION,
    ASSET,
}

enum class AttentionSeverity {
    ACTION,
    WATCH,
}

// Everything the calculator needs, gathered by PortfolioStatusService. Keeping the orchestration
// inputs in one shape makes the composition rule testable without a repository or a server.
data class PortfolioStatusInput(
    val asOf: LocalDate,
    val current: CurrentMonth?,
    val allocation: Allocation,
    val orders: OrderList?,
    val closedMonths: List<Month>,
    val lastSnapshot: Snapshot?,
    val positions: List<Position>,
    val latestQuotes: Map<String, Quote>,
    val openGaps: List<Gap>,
    val pendingImports: List<Import>,
    val stagedEditions: List<EditionDetail>,
    val unclassifiedTickers: List<ClassificationQueueItem>,
    val reviewActions: List<CorporateAction>,
)
