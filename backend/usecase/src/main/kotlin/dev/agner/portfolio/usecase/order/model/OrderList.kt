package dev.agner.portfolio.usecase.order.model

import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The Orders screen's read: the open month's persisted suggestions, its transfer inbox, and every
// executed trade no suggestion asked for. All three are month-scoped, so the period is the
// envelope — the screen never mixes two months.
data class OrderList(
    val period: YearMonth,
    val orders: List<SuggestedOrder>,
    val transfers: List<TransferProposal>,
    val unplannedTrades: List<UnplannedTrade>,
)

// A trade on the month's ledger that no suggestion planned — the month's extras. It is reported,
// never hidden: the plan's coverage is only honest when what fell outside it is visible.
data class UnplannedTrade(
    val id: Int,
    val ticker: String,
    val side: TradeSide,
    val quantity: BigDecimal,
)

// A suggestion's direction in the trade ledger's vocabulary: opening a position and adding to one
// are both buys, while a funding sell and a full exit are both sells. The single definition every
// match — planned or unplanned — is keyed by.
val OrderSide.tradeSide: TradeSide
    get() = if (isBuy) TradeSide.BUY else TradeSide.SELL

// The (ticker, ledger side) pair a trade and a suggestion are matched by — the key the bulk match,
// the unplanned-trade read and the override validation all share, so they can never disagree.
val OrderCandidate.matchKey: Pair<String, TradeSide> get() = ticker to side.tradeSide
val SuggestedOrder.matchKey: Pair<String, TradeSide> get() = ticker to side.tradeSide
val Trade.matchKey: Pair<String, TradeSide> get() = ticker to side
