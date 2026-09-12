package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.monthlyclose.MonthlyCloseAlreadyClosedException
import dev.agner.portfolio.usecase.monthlyclose.PendingTransferProposalsException
import dev.agner.portfolio.usecase.order.TransferProposalNotFoundException
import dev.agner.portfolio.usecase.order.TransferProposalNotPendingException
import dev.agner.portfolio.usecase.strategy.StrategyEditionAlreadyExistsException
import dev.agner.portfolio.usecase.strategy.StrategyNotFoundException
import io.ktor.http.HttpStatusCode
import org.springframework.stereotype.Component

interface DomainExceptionStatusMapper {
    fun statusFor(exception: DomainException): HttpStatusCode
}

@Component
class DefaultDomainExceptionStatusMapper : DomainExceptionStatusMapper {
    override fun statusFor(exception: DomainException) = when (exception) {
        is StrategyNotFoundException -> HttpStatusCode.NotFound
        is TransferProposalNotFoundException -> HttpStatusCode.NotFound
        is StrategyEditionAlreadyExistsException -> HttpStatusCode.Conflict
        is TransferProposalNotPendingException -> HttpStatusCode.Conflict
        is MonthlyCloseAlreadyClosedException -> HttpStatusCode.Conflict
        is PendingTransferProposalsException -> HttpStatusCode.Conflict
        else -> HttpStatusCode.BadRequest
    }
}
