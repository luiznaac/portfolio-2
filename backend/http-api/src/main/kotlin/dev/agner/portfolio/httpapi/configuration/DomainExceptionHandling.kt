package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.monthlyclose.PendingTransfersException
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond
import java.math.BigDecimal

fun Application.installDomainExceptionHandler(mapper: DomainExceptionStatusMapper) {
    install(StatusPages) {
        exception<DomainException> { call, cause ->
            call.respond(mapper.statusFor(cause), cause.toApiError())
        }
    }
}

// The canonical error body. Only the close's blocked-transfer case carries a payload: the caller
// needs the exact proposals to decide, so they ride beside the usual three fields. NON_NULL on the
// shared mapper keeps every other error body exactly the committed contracts/error-response.json.
private data class ApiError(
    val error: String,
    val message: String,
    val detail: String,
    val pendingTransfers: List<PendingTransfer>? = null,
)

private data class PendingTransfer(
    val id: Int,
    val ticker: String,
    val fromStrategy: String,
    val toStrategy: String,
    val quantity: BigDecimal,
    val amount: BigDecimal?,
)

private fun DomainException.toApiError() = ApiError(
    error = error,
    message = userMessage,
    detail = detail,
    pendingTransfers = (this as? PendingTransfersException)?.pending?.map {
        PendingTransfer(
            id = it.id,
            ticker = it.ticker,
            fromStrategy = it.fromStrategy,
            toStrategy = it.toStrategy,
            quantity = it.quantity,
            amount = it.amount,
        )
    },
)
