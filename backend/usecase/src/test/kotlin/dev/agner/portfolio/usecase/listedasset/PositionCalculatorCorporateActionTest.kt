package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.bd
import dev.agner.portfolio.usecase.corporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.sell
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// The replay is the source of truth for a stored Position: a corporate action must be part of it,
// otherwise the next trade insert would silently erase the event's effect.
class PositionCalculatorCorporateActionTest : StringSpec({

    "a derived allotment on the sale date replays before the sale so the position never goes negative" {
        val position = PositionCalculator.replay(
            "RBRP12",
            listOf(sell(id = 2, ticker = "RBRP12", date = "2019-05-06", quantity = "10", unitPrice = "88.27")),
            listOf(
                corporateAction(
                    CorporateActionKind.RIGHTS_ALLOTMENT,
                    ticker = "RBRP12",
                    exDate = "2019-05-06",
                    grantedQuantity = "10",
                    source = CorporateActionSource.DERIVED,
                ),
            ),
        )

        position.quantity.compareTo(BigDecimal("0")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("0")) shouldBe 0
    }

    "a real allotment replaces a derived one instead of doubling the rights position" {
        val sale = sell(id = 2, ticker = "RBRP12", date = "2019-05-06", quantity = "10", unitPrice = "88.27")
        val derived = corporateAction(
            CorporateActionKind.RIGHTS_ALLOTMENT,
            ticker = "RBRP12",
            exDate = "2019-05-06",
            grantedQuantity = "10",
            source = CorporateActionSource.DERIVED,
        )
        val real = corporateAction(
            CorporateActionKind.RIGHTS_ALLOTMENT,
            id = 2,
            ticker = "RBRP12",
            exDate = "2019-05-06",
            grantedQuantity = "10",
        )

        // Both present would double the receipt: 10 + 10 - 10 = 10.
        PositionCalculator.replay("RBRP12", listOf(sale), listOf(derived, real))
            .quantity.compareTo(BigDecimal("10")) shouldBe 0
        // The real allotment retires the derived one: 10 - 10 = 0.
        PositionCalculator.replay("RBRP12", listOf(sale), listOf(real))
            .quantity.compareTo(BigDecimal("0")) shouldBe 0
    }

    "a real allotment's grant becomes the rights position at zero cost" {
        val position = PositionCalculator.replay(
            "RBRP12",
            emptyList(),
            listOf(
                corporateAction(
                    CorporateActionKind.RIGHTS_ALLOTMENT,
                    ticker = "RBRP12",
                    exDate = "2019-05-06",
                    grantedQuantity = "100",
                ),
            ),
        )

        position.quantity.compareTo(BigDecimal("100")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("0")) shouldBe 0
    }

    "a split between two buys is reflected in the replayed position" {
        val position = PositionCalculator.replay(
            "PETR4",
            listOf(
                buy(id = 1, date = "2026-10-10", quantity = "100", unitPrice = "10"),
                buy(id = 2, date = "2026-10-14", quantity = "100", unitPrice = "20"),
            ),
            listOf(split(id = 1, date = "2026-10-12", ratio = "2")),
        )

        position.quantity.compareTo(BigDecimal("300")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("10")) shouldBe 0
    }

    "an event on a trade's own date applies before the trade" {
        val position = PositionCalculator.replay(
            "PETR4",
            listOf(
                buy(id = 1, date = "2026-10-01", quantity = "100", unitPrice = "20"),
                buy(id = 2, date = "2026-10-10", quantity = "100", unitPrice = "10"),
            ),
            listOf(split(id = 1, date = "2026-10-10", ratio = "2")),
        )

        position.quantity.compareTo(BigDecimal("300")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("10")) shouldBe 0
    }

    "same-day events apply in their deterministic ordinal order" {
        val position = PositionCalculator.replay(
            "PETR4",
            listOf(buy(id = 1, date = "2026-10-01", quantity = "100", unitPrice = "10")),
            listOf(
                bonus(id = 2, date = "2026-10-10", ordinal = 2, ratio = "0.5"),
                split(id = 1, date = "2026-10-10", ordinal = 1, ratio = "2"),
            ),
        )

        position.quantity.compareTo(BigDecimal("300")) shouldBe 0
    }

    "a review-queued event has no effect on the replayed position" {
        val position = PositionCalculator.replay(
            "PETR4",
            listOf(buy(id = 1, date = "2026-10-01", quantity = "100", unitPrice = "10")),
            listOf(split(id = 1, date = "2026-10-10", ratio = "2", status = CorporateActionStatus.NEEDS_REVIEW)),
        )

        position.quantity.compareTo(BigDecimal("100")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("10")) shouldBe 0
    }

    "a capital return lowers the replayed cost basis" {
        val position = PositionCalculator.replay(
            "PETR4",
            listOf(buy(id = 1, date = "2026-10-01", quantity = "100", unitPrice = "10")),
            listOf(cash(id = 1, date = "2026-10-10", kind = CorporateActionKind.CAPITAL_RETURN, perShare = "1.5")),
        )

        position.quantity.compareTo(BigDecimal("100")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("8.5")) shouldBe 0
    }

    "a ticker change without a ratio leaves the old ticker's replay untouched" {
        val position = PositionCalculator.replay(
            "PETR4",
            listOf(buy(id = 1, date = "2026-10-01", quantity = "100", unitPrice = "10")),
            listOf(cash(id = 1, date = "2026-10-10", kind = CorporateActionKind.TICKER_CHANGE)),
        )

        position.ticker shouldBe "PETR4"
        position.quantity.compareTo(BigDecimal("100")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("10")) shouldBe 0
    }

    "a rename carrying a conversion ratio scales quantity and average price like a split" {
        val position = PositionCalculator.replay(
            "NEW3",
            listOf(buy(id = 1, ticker = "OLD3", date = "2026-10-01", quantity = "100", unitPrice = "10")),
            listOf(rename(id = 1, date = "2026-10-10", ratio = "2")),
        )

        position.quantity.compareTo(BigDecimal("200")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("5")) shouldBe 0
    }

    "a same-date rename applies before a quantity event keyed to the new code" {
        val position = PositionCalculator.replay(
            "NEW3",
            listOf(buy(id = 1, ticker = "OLD3", date = "2026-10-01", quantity = "100", unitPrice = "10")),
            listOf(
                // The bonus is keyed to the new code and the rename to the old one; both land on the
                // one canonical book, rename first. The rename's higher id proves the kind rank, not
                // the id, decides. Reversed, the declared value would buy fewer units and the
                // average would differ (300 @ 3,666667 instead of 300 @ 4).
                bonus(id = 1, ticker = "NEW3", date = "2026-10-10", ratio = "0.5", perShare = "1"),
                rename(id = 2, date = "2026-10-10", ratio = "2"),
            ),
        )

        position.quantity.compareTo(BigDecimal("300")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("4")) shouldBe 0
    }

    "a same-date reverse split sees the post-rename quantity" {
        val trades = listOf(buy(id = 1, ticker = "OLD3", date = "2026-10-01", quantity = "103", unitPrice = "10"))
        val reverse = reverseSplit(id = 1, ticker = "NEW3", date = "2026-10-10", ratio = "4")
        val actions = listOf(rename(id = 2, date = "2026-10-10", ratio = "2"), reverse)

        val before = PositionCalculator.positionBefore("NEW3", trades, actions, reverse)

        // Rename first: 103 becomes 206, so the grupamento floors to 51 and its fraction is 2 units
        // at the converted average. Reversed, it would see 103, floor to 25 and pay 3 units at the
        // old average — the same position, a different cash entitlement.
        before.quantity.compareTo(BigDecimal("206")) shouldBe 0
    }
})

private fun buy(
    id: Int,
    date: String,
    quantity: String,
    unitPrice: String,
    ticker: String = "PETR4",
) = Trade.Buy(
    id = id,
    ticker = ticker,
    date = LocalDate.parse(date),
    venue = "VISTA",
    quantity = bd(quantity),
    unitPrice = bd(unitPrice),
    institutionId = 1,
    fee = null,
    source = TradeSource.B3_IMPORT,
    confirmedByB3 = false,
    ordinal = id,
)

private fun action(
    id: Int,
    date: String,
    kind: CorporateActionKind,
    ratio: String? = null,
    perShare: String? = null,
    ordinal: Int = 1,
    status: CorporateActionStatus = CorporateActionStatus.APPLIED,
    ticker: String = "PETR4",
    targetTicker: String? = null,
) = corporateAction(
    kind,
    id = id,
    ticker = ticker,
    exDate = date,
    ratio = ratio,
    declaredValuePerShare = perShare,
    targetTicker = targetTicker,
    ordinal = ordinal,
    status = status,
)

private fun split(
    id: Int,
    date: String,
    ratio: String,
    ordinal: Int = 1,
    status: CorporateActionStatus = CorporateActionStatus.APPLIED,
) = action(id, date, CorporateActionKind.SPLIT, ratio = ratio, ordinal = ordinal, status = status)

private fun bonus(
    id: Int,
    date: String,
    ratio: String,
    ordinal: Int = 1,
    ticker: String = "PETR4",
    perShare: String? = null,
) =
    action(
        id = id,
        date = date,
        kind = CorporateActionKind.BONUS_SHARES,
        ratio = ratio,
        perShare = perShare,
        ordinal = ordinal,
        ticker = ticker,
    )

private fun rename(id: Int, date: String, ratio: String) =
    action(
        id = id,
        date = date,
        kind = CorporateActionKind.TICKER_CHANGE,
        ratio = ratio,
        ticker = "OLD3",
        targetTicker = "NEW3",
    )

private fun reverseSplit(id: Int, date: String, ratio: String, ticker: String = "PETR4") =
    action(
        id = id,
        date = date,
        kind = CorporateActionKind.REVERSE_SPLIT,
        ratio = ratio,
        ticker = ticker,
    )

private fun cash(id: Int, date: String, kind: CorporateActionKind, perShare: String? = null) =
    action(id, date, kind, perShare = perShare)
