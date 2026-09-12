package dev.agner.portfolio.usecase.order.model

data class OrderPlan(
    val orders: List<Order>,
    val transferProposals: List<TransferProposal>,
    val saleCeiling: SaleCeiling,
)
