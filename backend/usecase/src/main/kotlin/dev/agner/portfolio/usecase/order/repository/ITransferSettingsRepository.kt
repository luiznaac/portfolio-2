package dev.agner.portfolio.usecase.order.repository

import dev.agner.portfolio.usecase.order.model.TransferSettings
import java.math.BigDecimal

interface ITransferSettingsRepository {

    /** Defaults to a zero threshold (everything goes through the user) when nothing's been saved yet. */
    suspend fun fetch(): TransferSettings

    suspend fun save(autoApprovalThreshold: BigDecimal): TransferSettings
}
