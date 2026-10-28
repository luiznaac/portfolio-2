package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.monthlyclose.PendingTransfersException
import dev.agner.portfolio.usecase.transferProposal
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

class DomainExceptionStatusMapperTest : StringSpec({
    val mapper = DefaultDomainExceptionStatusMapper()

    "should map an unmapped domain exception to 400" {
        mapper.statusFor(object : DomainException("domain-error", "Request failed", "detail") {}) shouldBe
            HttpStatusCode.BadRequest
    }

    "should map the pending-transfer block to 409" {
        mapper.statusFor(PendingTransfersException(listOf(transferProposal()))) shouldBe HttpStatusCode.Conflict
    }
})
