package dev.agner.portfolio.usecase.order.model

data class OrderPlan(
    val orders: List<Order>,
    val transferSuggestions: List<TransferSuggestion>,
    val saleCeiling: SaleCeiling,
)
