package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.corporateaction.CorporateActionNotFoundException
import dev.agner.portfolio.usecase.monthlyclose.PendingTransfersException
import io.ktor.http.HttpStatusCode
import org.springframework.stereotype.Component

interface DomainExceptionStatusMapper {
    fun statusFor(exception: DomainException): HttpStatusCode
}

@Component
class DefaultDomainExceptionStatusMapper : DomainExceptionStatusMapper {
    override fun statusFor(exception: DomainException) = when (exception) {
        is PendingTransfersException -> HttpStatusCode.Conflict
        is CorporateActionNotFoundException -> HttpStatusCode.NotFound
        else -> HttpStatusCode.BadRequest
    }
}
