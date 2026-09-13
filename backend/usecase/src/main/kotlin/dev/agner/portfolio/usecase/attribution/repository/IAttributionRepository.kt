package dev.agner.portfolio.usecase.attribution.repository

import dev.agner.portfolio.usecase.attribution.model.AttributionMovement
import dev.agner.portfolio.usecase.attribution.model.AttributionMovementCreation

interface IAttributionRepository {

    suspend fun fetchByAssetId(assetId: Int): List<AttributionMovement>

    suspend fun save(assetId: Int, creation: AttributionMovementCreation): AttributionMovement
}
