package dev.agner.portfolio.httpapi.strategyreport

import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * Layout B — XP "Research FIIs" Carteira Fundamentalista. The weight is the first `%` on a row,
 * before the ticker; the trailing `102% 1,9% …` are performance columns. The report runs 40 pages,
 * so extraction is bounded to the composition table — a fund named in a later deep-dive
 * ("venda de R$ 130 mil em cotas de CYCR11") must not be read as a target.
 */
class XpFiiCarteiraReportParserTest : StringSpec({

    val resolver = StrategyReportParserResolver(listOf(XpFiiCarteiraReportParser(), GenericLineReportParser()))

    val report = arrayOf(
        "1XP RESEARCH",
        "Research FIIs",
        "Setembro 2026",
        "Carteira Fundamentalista",
        "Alterações: Para setembro, reduzimos a alocação em CPTS11 e ampliamos a exposição a XPLG11.",
        "Peso % Fundo Valor de Mercado (VM) Cota Valor Patrimonial (VP) VM/VP Performance Yield",
        "Por Ticker Segmento Ticker Recomendação Nome (R\$ milhões) (R\$) (R\$/Cota) % No mês Em 12m DY 12m (pts.)",
        "40,00% Recebíveis MCCI11 COMPRA Mauá Capital Recebíveis 1.621 96 94 102% 1,9% 25,7% 12,6% 21",
        "35,00% Ativos Logísticos XPLG11 COMPRA XP Logística 4.715 92 105 87% -0,4% 2,0% 10,7% 21",
        "25,00% Híbrido KNRI11 COMPRA Kinea Renda Imobiliária 4.442 158 163 96% 0,5% 21,8% 8,4% 17",
        "90% 12,4% 19",
        "Índice",
        "Teses de investimento",
        "Movimentação recente: houve venda de R\$ 130 mil em cotas de CYCR11 e recompra parcial.",
    )

    "extracts the composition table, weight before the ticker, recommendation always COMPRA" {
        val parsed = resolver.parse(pdfOf(*report))

        parsed.referenceDate shouldBe LocalDate(2026, 9, 1)
        parsed.targets shouldContainExactly listOf(
            StrategyTarget("MCCI11", BigDecimal("0.4000"), "COMPRA"),
            StrategyTarget("XPLG11", BigDecimal("0.3500"), "COMPRA"),
            StrategyTarget("KNRI11", BigDecimal("0.2500"), "COMPRA"),
        )
    }

    "does not read a fund mentioned in a later deep-dive as a target" {
        resolver.parse(pdfOf(*report)).targets.map { it.ticker } shouldBe listOf("MCCI11", "XPLG11", "KNRI11")
    }
})
