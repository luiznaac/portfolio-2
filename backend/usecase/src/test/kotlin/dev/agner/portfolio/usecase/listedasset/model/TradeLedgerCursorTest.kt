package dev.agner.portfolio.usecase.listedasset.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

class TradeLedgerCursorTest : StringSpec({

    "a cursor round-trips through its wire form" {
        val cursor = TradeLedgerCursor(date = LocalDate.parse("2026-10-14"), id = 7)

        TradeLedgerCursor.decode(cursor.encode()) shouldBe cursor
    }

    "a malformed cursor decodes to null instead of guessing a position" {
        TradeLedgerCursor.decode("2026-10-14") shouldBe null
        TradeLedgerCursor.decode("no-a-date:7") shouldBe null
        TradeLedgerCursor.decode("2026-10-14:not-an-id") shouldBe null
        TradeLedgerCursor.decode("2026-10-14:0") shouldBe null
    }
})
