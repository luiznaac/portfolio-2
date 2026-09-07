package dev.agner.portfolio.usecase.consolidation

interface ProductConsolidator<T> {

    val type: ProductType

    suspend fun getConsolidatableIds(): List<Int>

    suspend fun buildContext(productId: Int): T

    suspend fun consolidate(ctx: T)
}
