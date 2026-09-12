package dev.agner.portfolio.usecase.monthlyclose.repository

import dev.agner.portfolio.usecase.monthlyclose.model.MonthlyClose
import kotlinx.datetime.LocalDate

interface IMonthlyCloseRepository {

    suspend fun fetchByMonth(month: LocalDate): MonthlyClose?

    suspend fun fetchAll(): List<MonthlyClose>

    suspend fun open(month: LocalDate): MonthlyClose

    suspend fun close(month: LocalDate): MonthlyClose
}
