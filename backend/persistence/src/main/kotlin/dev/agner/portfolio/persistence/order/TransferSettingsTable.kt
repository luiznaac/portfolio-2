package dev.agner.portfolio.persistence.order

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

// Single-row settings table — there is exactly one TransferSettings for the whole app, at id 1.
object TransferSettingsTable : IntIdTable("transfer_settings") {
    val autoApprovalThreshold = decimal("auto_approval_threshold", 12, 2)
}

class TransferSettingsEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TransferSettingsEntity>(TransferSettingsTable)

    var autoApprovalThreshold by TransferSettingsTable.autoApprovalThreshold
}
