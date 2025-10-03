package dev.agner.portfolio.usecase.upload

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.BondService
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.upload.model.KinvoOrder
import org.springframework.stereotype.Service

@Service
class UploadService(
    private val bondService: BondService,
    private val bondOrderService: BondOrderService,
) {

    suspend fun createOrders(orders: List<KinvoOrder>): List<BondOrder> {
        data class CreatedData(val bonds: Map<String, Bond> = emptyMap(), val orders: List<BondOrder> = emptyList())

        return orders
            .fold(CreatedData()) { acc, kinvoOrder ->
                val bond = acc.bonds[kinvoOrder.description] ?: bondService.createBond(kinvoOrder.toBondCreation())
                val bondOrder = bondOrderService.create(kinvoOrder.toBondOrderCreation(bond.id))

                acc.copy(
                    bonds = acc.bonds + (kinvoOrder.description to bond),
                    orders = acc.orders + bondOrder,
                )
            }
            .orders
    }
}
