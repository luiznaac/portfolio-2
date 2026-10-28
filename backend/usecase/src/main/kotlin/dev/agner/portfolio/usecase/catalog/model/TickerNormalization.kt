package dev.agner.portfolio.usecase.catalog.model

// A listed asset's canonical key: uppercase, and the fractional-market suffix stripped
// (ALUP11F is the same paper as ALUP11). B3's class code is always numeric, so a trailing `F` is
// unambiguous. Every ticker crossing the domain boundary goes through here first.
fun String.normalizedTicker(): String = trim().uppercase().removeSuffix("F")
