package dev.agner.portfolio.persistence.order

import dev.agner.portfolio.persistence.configuration.ColumnSizes
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

// Single-row settings table — there is exactly one TransferSettings for the whole app, at id 1.
object TransferSettingsTable : IntIdTable("transfer_settings") {
    val autoApprovalThreshold = decimal(
        "auto_approval_threshold",
        ColumnSizes.MONEY_PRECISION,
        ColumnSizes.MONEY_SCALE,
    )
}

class TransferSettingsEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TransferSettingsEntity>(TransferSettingsTable)

    var autoApprovalThreshold by TransferSettingsTable.autoApprovalThreshold
}
