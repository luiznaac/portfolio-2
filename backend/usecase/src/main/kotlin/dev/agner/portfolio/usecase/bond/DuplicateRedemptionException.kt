package dev.agner.portfolio.usecase.bond

import dev.agner.portfolio.usecase.commons.DomainException
import kotlinx.datetime.LocalDate

class DuplicateRedemptionException(date: LocalDate, owner: String, ownerId: Int) :
    DomainException(
        error = "duplicate-redemption",
        userMessage = "A redemption order already exists for this date",
        detail = "A redemption order already exists for $owner $ownerId on $date",
    )
