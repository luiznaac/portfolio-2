package dev.agner.portfolio.persistence.allocation

import dev.agner.portfolio.usecase.allocation.classification.model.ProductClassification
import dev.agner.portfolio.usecase.allocation.classification.repository.IProductClassificationRepository
import dev.agner.portfolio.usecase.allocation.model.AssetClass
import dev.agner.portfolio.usecase.consolidation.ProductType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Component

@Component
class ProductClassificationRepository : IProductClassificationRepository {

    override suspend fun fetchAll() = transaction {
        ProductClassificationTable.selectAll().map { it.toModel() }
    }

    override suspend fun save(classification: ProductClassification) = transaction {
        val keyMatch = keyMatch(classification.productType, classification.productId)
        val updated = ProductClassificationTable.update({ keyMatch }) {
            it[assetClass] = classification.assetClass.name
        }

        if (updated == 0) {
            ProductClassificationTable.insert {
                it[productType] = classification.productType.name
                it[productId] = classification.productId
                it[assetClass] = classification.assetClass.name
            }
        }

        classification
    }

    override suspend fun delete(productType: ProductType, productId: Int): Unit = transaction {
        ProductClassificationTable.deleteWhere { keyMatch(productType, productId) }
    }

    private fun keyMatch(productType: ProductType, productId: Int) =
        (ProductClassificationTable.productType eq productType.name) and
            (ProductClassificationTable.productId eq productId)
}

private fun ResultRow.toModel() = ProductClassification(
    productType = ProductType.valueOf(this[ProductClassificationTable.productType]),
    productId = this[ProductClassificationTable.productId],
    assetClass = AssetClass.valueOf(this[ProductClassificationTable.assetClass]),
)
