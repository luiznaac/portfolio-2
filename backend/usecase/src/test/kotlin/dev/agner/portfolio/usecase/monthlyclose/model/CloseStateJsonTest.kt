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
