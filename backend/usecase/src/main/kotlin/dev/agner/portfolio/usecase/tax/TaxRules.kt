package dev.agner.portfolio.usecase.tax

import java.math.BigDecimal

/**
 * The Brazilian tax constants the listed-asset side of the portfolio depends on, in one place so
 * the order engine, the capital-gains ledger and the income projection cannot drift apart on what
 * the same rule says.
 */
object TaxRules {

    /**
     * Monthly stock sales up to this amount are exempt from capital-gains tax. The ceiling is on
     * the amount *sold*, not on the gain, and FIIs never qualify — they are always taxed.
     */
    val MONTHLY_STOCK_SALE_EXEMPTION: BigDecimal = BigDecimal("20000.00")

    /** Capital-gains rate on stocks, ETFs and BDRs outside the exemption. */
    val STOCK_CAPITAL_GAINS_RATE: BigDecimal = BigDecimal("0.15")

    /** Capital-gains rate on FIIs — no exemption applies. */
    val FII_CAPITAL_GAINS_RATE: BigDecimal = BigDecimal("0.20")

    /**
     * IRRF withheld at source on JCP. Dividends and FII income are tax-free for individuals, so
     * they have no equivalent constant.
     */
    val JCP_WITHHOLDING_RATE: BigDecimal = BigDecimal("0.15")
}
