package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.isRightsTicker
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.tickeralias.TickerAliasResolver
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal

// The rights receipt boundary. A rights sale with no receipt in the import would leave the position
// negative, so the engine derives a zero-cost allotment of the deficit on the sale date; a real
// allotment (from a B3 movement row or the owner's resolve) retires the derived guess first, so the
// two can never double the position. The allotment is a corporate action keyed on the rights ticker,
// replayed before a same-date trade, so the position never goes negative.
@Service
class RightsAllotmentService(
    private val trades: ITradeRepository,
    private val corporateActions: ICorporateActionRepository,
    private val catalog: CatalogService,
    private val aliases: TickerAliasResolver,
) {

    // Called before a sale is written. A ticker that is not a right needs nothing; a right with
    // enough received units needs nothing; a right short of units gets a DERIVED allotment of the
    // deficit, dated the sale date. The receipt is written first so the replay sees it before the
    // sale.
    suspend fun ensureReceipt(ticker: String, saleDate: LocalDate, saleQuantity: BigDecimal) {
        val canonical = aliases.canonical(ticker)
        val entry = catalog.fetchBy(canonical)
        // A catalogued right is the normal case; an uncatalogued code shaped like one (the B3
        // `XXXX` + 2 rule) is a rights sale the import has not classified yet.
        val cataloguedRight = entry?.assetKind == AssetKind.RIGHT
        if (!cataloguedRight && !canonical.isRightsTicker()) return
        if (!cataloguedRight) catalog.markRights(canonical)

        val deficit = saleQuantity.subtract(receivedQuantity(canonical, saleDate)).takeIf { it.signum() > 0 }
            ?: return
        corporateActions.save(derivedCreation(canonical, saleDate, deficit), CorporateActionStatus.APPLIED)
    }

    // A real allotment supersedes the derived guesses it can cover: those dated on or after its own
    // ex-date. A derived receipt for an earlier sale is a different issuance and stays. The caller
    // then stores the real grant, so the position becomes the real quantity instead of the derived
    // quantity plus it.
    suspend fun replaceDerived(rightsTicker: String, realExDate: LocalDate) {
        corporateActions.fetchByTicker(rightsTicker)
            .filter {
                it.kind == CorporateActionKind.RIGHTS_ALLOTMENT &&
                    it.source == CorporateActionSource.DERIVED &&
                    it.exDate?.let { date -> date >= realExDate } == true
            }
            .forEach { corporateActions.delete(it.id) }
    }

    // The units the book has already received on or before the sale date, replayed through the
    // rights paper's own ledger and actions. A previous derived receipt of the same batch is
    // included, so a second same-day sale only derives its own deficit; a later-dated buy or
    // allotment never covers it.
    private suspend fun receivedQuantity(rightsTicker: String, saleDate: LocalDate): BigDecimal {
        val aliasSet = aliases.aliasesOf(rightsTicker)
        return PositionCalculator.positionThroughDate(
            rightsTicker,
            aliasSet.flatMap { trades.fetchAllBy(it) },
            aliasSet.flatMap { corporateActions.fetchByTicker(it) },
            saleDate,
        ).quantity.max(BigDecimal.ZERO)
    }

    // The derived allotment for a (paper, sale date). Derived ordinals start above the import's 1..N
    // range, so a derived receipt never occupies the natural key a real allotment on the same date
    // will claim; a second same-day sale gets the next derived ordinal.
    private suspend fun derivedCreation(
        rightsTicker: String,
        saleDate: LocalDate,
        quantity: BigDecimal,
    ): CorporateActionCreation {
        val ordinal = DERIVED_ORDINAL_BASE + corporateActions.fetchByTicker(rightsTicker)
            .count {
                it.kind == CorporateActionKind.RIGHTS_ALLOTMENT &&
                    it.source == CorporateActionSource.DERIVED &&
                    it.exDate == saleDate
            }
        return CorporateActionCreation(
            ticker = rightsTicker,
            kind = CorporateActionKind.RIGHTS_ALLOTMENT,
            exDate = saleDate,
            source = CorporateActionSource.DERIVED,
            ordinal = ordinal,
            grantedQuantity = quantity,
        )
    }

    private companion object {
        const val DERIVED_ORDINAL_BASE = 1000
    }
}
