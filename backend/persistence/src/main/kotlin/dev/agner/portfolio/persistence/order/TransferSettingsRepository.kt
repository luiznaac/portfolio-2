package dev.agner.portfolio.persistence.order

import dev.agner.portfolio.usecase.order.model.TransferSettings
import dev.agner.portfolio.usecase.order.repository.ITransferSettingsRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class TransferSettingsRepository : ITransferSettingsRepository {

    override suspend fun fetch(): TransferSettings = transaction {
        val entity = TransferSettingsEntity.findById(SINGLETON_ID)
        TransferSettings(autoApprovalThreshold = entity?.autoApprovalThreshold ?: BigDecimal.ZERO)
    }

    override suspend fun save(autoApprovalThreshold: BigDecimal): TransferSettings = transaction {
        val entity = TransferSettingsEntity.findById(SINGLETON_ID)
            ?: TransferSettingsEntity.new(SINGLETON_ID) { this.autoApprovalThreshold = autoApprovalThreshold }
        entity.autoApprovalThreshold = autoApprovalThreshold
        TransferSettings(autoApprovalThreshold = entity.autoApprovalThreshold)
    }

    private companion object {
        const val SINGLETON_ID = 1
    }
}
