package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.bd
import dev.agner.portfolio.usecase.corporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// The replay is the source of truth for a stored Position: a corporate action must be part of it,
// otherwise the next trade insert would silently erase the event's effect.
class PositionCalculatorCorporateActionTest : StringSpec({

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

    "a ticker change leaves the old ticker's replay untouched" {
        val position = PositionCalculator.replay(
            "PETR4",
            listOf(buy(id = 1, date = "2026-10-01", quantity = "100", unitPrice = "10")),
            listOf(cash(id = 1, date = "2026-10-10", kind = CorporateActionKind.TICKER_CHANGE)),
        )

        position.ticker shouldBe "PETR4"
        position.quantity.compareTo(BigDecimal("100")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("10")) shouldBe 0
    }
})

private fun buy(
    id: Int,
    date: String,
    quantity: String,
    unitPrice: String,
) = Trade.Buy(
    id = id,
    ticker = "PETR4",
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
) = corporateAction(
    kind,
    id = id,
    exDate = date,
    ratio = ratio,
    declaredValuePerShare = perShare,
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

private fun bonus(id: Int, date: String, ratio: String, ordinal: Int) =
    action(id, date, CorporateActionKind.BONUS_SHARES, ratio = ratio, ordinal = ordinal)

private fun cash(id: Int, date: String, kind: CorporateActionKind, perShare: String? = null) =
    action(id, date, kind, perShare = perShare)
