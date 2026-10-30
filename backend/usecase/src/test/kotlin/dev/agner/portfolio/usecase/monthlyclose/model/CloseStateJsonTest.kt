package dev.agner.portfolio.usecase.monthlyclose.model

import dev.agner.portfolio.usecase.configuration.JsonMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

// The snapshot is read back through the same mapper the repository uses. A snapshot written before
// the strategy rung landed has no `strategies` key; the default keeps history readable instead of
// failing the closed-month read.
class CloseStateJsonTest : StringSpec({

    "reads a legacy snapshot that predates the strategy slices" {
        val state = JsonMapper.mapper.readValue(legacySnapshot, CloseState::class.java)

        state.period.toString() shouldBe "2026-01"
        state.strategies shouldBe emptyList()
    }

    "reads a legacy carry bucket written before the availability flag" {
        val state = JsonMapper.mapper.readValue(legacyCarrySnapshot, CloseState::class.java)
        val bucket = state.tax.carry.single()

        bucket.available shouldBe true
        bucket.openingBalance!!.compareTo(java.math.BigDecimal("500")) shouldBe 0
    }
})

private val legacySnapshot = """
    {
      "period": "2026-01",
      "capital": 100000,
      "floor": 0,
      "aportes": 0,
      "caixa": 0,
      "holdings": [],
      "attributions": [],
      "classes": [],
      "income": {"gross": 0, "retained": 0, "net": 0},
      "orders": {"orders": [], "transfers": [], "unplanned_trades": []},
      "tax": {
        "carry": [],
        "meter": {
          "gross_sales": 0,
          "exemption_limit": 20000,
          "exceeded": false,
          "realized_gain": 0,
          "taxable_gain": 0,
          "rate": 15,
          "tax": 0
        }
      }
    }
""".trimIndent()

private val legacyCarrySnapshot = """
    {
      "period": "2026-01",
      "capital": 100000,
      "floor": 0,
      "aportes": 0,
      "caixa": 0,
      "holdings": [],
      "attributions": [],
      "classes": [],
      "income": {"gross": 0, "retained": 0, "net": 0},
      "orders": {"orders": [], "transfers": [], "unplanned_trades": []},
      "tax": {
        "carry": [{
          "kind": "SWING",
          "opening_balance": 500,
          "loss_banked": 0,
          "loss_applied": 0,
          "closing_balance": 500
        }],
        "meter": {
          "gross_sales": 0,
          "exemption_limit": 20000,
          "exceeded": false,
          "realized_gain": 0,
          "taxable_gain": 0,
          "rate": 15,
          "tax": 0
        }
      }
    }
""".trimIndent()
