package dev.agner.portfolio.usecase.strategy.model

// The currency a report quotes an Edition Entry's target price in. The XP reports print "R$" for a
// Brazilian listing and "US$" for one quoted abroad (JBS's BDR, for instance), and the two are not
// interchangeable: a dollar target rendered as reais is a silently wrong number. The keys are
// English and cross the API, like Rating's.
enum class TargetCurrency {
    BRL,
    USD,
}
