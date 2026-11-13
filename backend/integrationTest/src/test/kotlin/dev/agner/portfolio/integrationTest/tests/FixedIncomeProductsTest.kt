package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.createCheckingAccount
import dev.agner.portfolio.integrationTest.helpers.createDeposit
import dev.agner.portfolio.integrationTest.helpers.createFixedBond
import dev.agner.portfolio.integrationTest.helpers.getBonds
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

// GET /fixed-income/bonds is the RF product list: a checking account's deposit-created bonds are the
// account's value, not standalone products, so the read leaves them out.
@IntegrationTest
class FixedIncomeProductsTest : StringSpec({

    "lists a standalone bond but not the bond a deposit created" {
        val standaloneId = createFixedBond("12.00", "2030-01-01")
        val before = getBonds().size

        val checkingAccountId = createCheckingAccount("102.00", "CDI", "P3Y")
        createDeposit(checkingAccountId, "2025-05-30", "1000.00")

        val bonds = getBonds()
        bonds.map { it["id"].toString() } shouldContain standaloneId
        bonds.size shouldBe before
    }
})
