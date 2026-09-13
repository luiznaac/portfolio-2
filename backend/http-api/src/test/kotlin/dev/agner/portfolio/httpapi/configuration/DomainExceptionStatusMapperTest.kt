package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.brokeragenote.parser.BrokerageNoteParseException
import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.monthlyclose.MonthlyCloseAlreadyClosedException
import dev.agner.portfolio.usecase.monthlyclose.PendingTransferProposalsException
import dev.agner.portfolio.usecase.order.InvalidTransferQuantityException
import dev.agner.portfolio.usecase.order.TransferProposalNotFoundException
import dev.agner.portfolio.usecase.order.TransferProposalNotPendingException
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus
import dev.agner.portfolio.usecase.strategy.StrategyEditionAlreadyExistsException
import dev.agner.portfolio.usecase.strategy.StrategyNotFoundException
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class DomainExceptionStatusMapperTest : StringSpec({
    val mapper = DefaultDomainExceptionStatusMapper()

    listOf(
        MappingCase("a missing strategy", StrategyNotFoundException(1), HttpStatusCode.NotFound),
        MappingCase(
            "a missing transfer proposal",
            TransferProposalNotFoundException(7),
            HttpStatusCode.NotFound,
        ),
        MappingCase(
            "a duplicated strategy edition",
            StrategyEditionAlreadyExistsException(1, "2026-09"),
            HttpStatusCode.Conflict,
        ),
        MappingCase(
            "a transfer proposal already decided",
            TransferProposalNotPendingException(7, TransferProposalStatus.APPLIED),
            HttpStatusCode.Conflict,
        ),
        MappingCase(
            "an already closed month",
            MonthlyCloseAlreadyClosedException(LocalDate(2026, 9, 1)),
            HttpStatusCode.Conflict,
        ),
        MappingCase(
            "pending transfer proposals",
            PendingTransferProposalsException(2),
            HttpStatusCode.Conflict,
        ),
        MappingCase(
            "an unmapped domain exception",
            BrokerageNoteParseException("Missing expected columns"),
            HttpStatusCode.BadRequest,
        ),
        MappingCase(
            "an out-of-range transfer quantity",
            InvalidTransferQuantityException(BigDecimal("50"), BigDecimal("9")),
            HttpStatusCode.BadRequest,
        ),
    ).forEach { case ->
        "should map ${case.description} to ${case.expected.value}" {
            mapper.statusFor(case.exception) shouldBe case.expected
        }
    }
})

private data class MappingCase(
    val description: String,
    val exception: DomainException,
    val expected: HttpStatusCode,
)
