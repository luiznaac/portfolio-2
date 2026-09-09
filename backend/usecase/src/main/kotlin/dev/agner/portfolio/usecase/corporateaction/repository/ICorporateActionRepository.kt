package dev.agner.portfolio.usecase.corporateaction.repository

import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation

interface ICorporateActionRepository {

    suspend fun fetchByAssetId(assetId: Int): List<CorporateAction>

    suspend fun save(creation: CorporateActionCreation): CorporateAction
}
