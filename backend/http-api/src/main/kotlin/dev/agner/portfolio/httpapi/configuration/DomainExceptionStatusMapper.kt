package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.commons.DomainException
import io.ktor.http.HttpStatusCode
import org.springframework.stereotype.Component

interface DomainExceptionStatusMapper {
    fun statusFor(exception: DomainException): HttpStatusCode
}

@Component
class DefaultDomainExceptionStatusMapper : DomainExceptionStatusMapper {
    override fun statusFor(exception: DomainException) = HttpStatusCode.BadRequest
}
