package dev.agner.portfolio.persistence.strategy

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class StrategyEditionRepositoryTest : StringSpec({
    "should identify the MySQL duplicate-entry violation of the reference date index" {
        isReferenceDateConflict(errorCode = 1062, sqlState = "23000") shouldBe true
    }

    "should identify a duplicate-key violation by SQL state alone" {
        isReferenceDateConflict(errorCode = 0, sqlState = "23000") shouldBe true
    }

    "should not swallow other database failures" {
        isReferenceDateConflict(errorCode = 1213, sqlState = "40001") shouldBe false
    }
})
