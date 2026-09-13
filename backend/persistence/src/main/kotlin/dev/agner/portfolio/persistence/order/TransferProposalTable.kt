package dev.agner.portfolio.persistence.order

import dev.agner.portfolio.persistence.configuration.ColumnSizes
import dev.agner.portfolio.persistence.listedasset.ListedAssetEntity
import dev.agner.portfolio.persistence.listedasset.ListedAssetTable
import dev.agner.portfolio.persistence.strategy.StrategyEntity
import dev.agner.portfolio.persistence.strategy.StrategyTable
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

object TransferProposalTable : IntIdTable("transfer_proposal") {
    val month = date("month")
    val listedAsset = reference("listed_asset_id", ListedAssetTable.id)
    val ticker = varchar("ticker", ColumnSizes.TICKER_LENGTH)
    val fromStrategy = reference("from_strategy_id", StrategyTable.id)
    val toStrategy = reference("to_strategy_id", StrategyTable.id)
    val proposedQuantity = decimal("proposed_quantity", ColumnSizes.QUANTITY_PRECISION, ColumnSizes.QUANTITY_SCALE)
    val appliedQuantity = decimal(
        "applied_quantity",
        ColumnSizes.QUANTITY_PRECISION,
        ColumnSizes.QUANTITY_SCALE,
    ).nullable()
    val status = enumerationByName("status", ColumnSizes.ENUM_NAME_LENGTH, TransferProposalStatus::class)
    val decidedAt = datetime("decided_at").nullable()
    val createdAt = datetime("created_at")

    // UNIQUE, not just an index: the reconcile step looks a pairing up by exactly these four columns
    // and assumes at most one row comes back (see OrderPlanService.reconcileProposal), and the
    // check-then-insert in TransferProposalRepository.save relies on this constraint to close its race.
    init { index("transfer_proposal_pairing", true, month, listedAsset, fromStrategy, toStrategy) }
}

class TransferProposalEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TransferProposalEntity>(TransferProposalTable)

    var month by TransferProposalTable.month
    var listedAsset by ListedAssetEntity referencedOn TransferProposalTable.listedAsset
    var ticker by TransferProposalTable.ticker
    var fromStrategy by StrategyEntity referencedOn TransferProposalTable.fromStrategy
    var toStrategy by StrategyEntity referencedOn TransferProposalTable.toStrategy
    var proposedQuantity by TransferProposalTable.proposedQuantity
    var appliedQuantity by TransferProposalTable.appliedQuantity
    var status by TransferProposalTable.status
    var decidedAt by TransferProposalTable.decidedAt
    var createdAt by TransferProposalTable.createdAt

    fun toModel() = TransferProposal(
        id = id.value,
        month = month,
        listedAssetId = listedAsset.id.value,
        ticker = ticker,
        fromStrategyId = fromStrategy.id.value,
        fromStrategyName = fromStrategy.name,
        toStrategyId = toStrategy.id.value,
        toStrategyName = toStrategy.name,
        proposedQuantity = proposedQuantity,
        appliedQuantity = appliedQuantity,
        status = status,
        decidedAt = decidedAt,
    )
}
