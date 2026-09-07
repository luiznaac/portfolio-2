package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.commons.DomainException
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

class DomainExceptionStatusMapperTest : StringSpec({
    val mapper = DefaultDomainExceptionStatusMapper()

    "should map an unmapped domain exception to 400" {
        mapper.statusFor(object : DomainException("domain-error", "Request failed", "detail") {}) shouldBe
            HttpStatusCode.BadRequest
    }
})
