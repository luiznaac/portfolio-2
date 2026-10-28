package dev.agner.portfolio.usecase.institution

import dev.agner.portfolio.usecase.institution.model.Institution
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.institution.repository.IInstitutionRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class InstitutionServiceTest : StringSpec({
    val repository = mockk<IInstitutionRepository>(relaxed = true)
    val service = InstitutionService(repository)

    beforeTest { clearAllMocks() }

    "create should normalize the name and save a new institution" {
        coEvery { repository.fetchByName("XP INVESTIMENTOS") } returns null
        coEvery { repository.save(any()) } returns Institution(1, "XP INVESTIMENTOS")

        service.create(InstitutionCreation("  xp   investimentos "))

        coVerify(exactly = 1) { repository.fetchByName("XP INVESTIMENTOS") }
        coVerify(exactly = 1) { repository.save(InstitutionCreation("XP INVESTIMENTOS")) }
    }

    "create should merge into an existing institution whose normalized name matches" {
        val existing = Institution(7, "XP INVESTIMENTOS")
        coEvery { repository.fetchByName("XP INVESTIMENTOS") } returns existing

        service.create(InstitutionCreation("Xp Investimentos")) shouldBe existing

        coVerify(exactly = 0) { repository.save(any()) }
    }

    "rename should merge into another institution holding the target name" {
        val existing = Institution(7, "XP INVESTIMENTOS")
        coEvery { repository.fetchByName("XP INVESTIMENTOS") } returns existing

        service.rename(9, "xp investimentos") shouldBe existing

        coVerify(exactly = 1) { repository.delete(9) }
        coVerify(exactly = 0) { repository.update(any(), any()) }
    }

    "rename to the same normalized name should keep the institution untouched" {
        val existing = Institution(9, "XP INVESTIMENTOS")
        coEvery { repository.fetchByName("XP INVESTIMENTOS") } returns existing

        service.rename(9, "xp investimentos") shouldBe existing

        coVerify(exactly = 0) { repository.delete(any()) }
        coVerify(exactly = 0) { repository.update(any(), any()) }
    }

    "rename should update in place when the target name is free" {
        coEvery { repository.fetchByName("NOVA CORRETORA") } returns null
        coEvery { repository.update(9, "NOVA CORRETORA") } returns Institution(9, "NOVA CORRETORA")

        service.rename(9, "nova corretora") shouldBe Institution(9, "NOVA CORRETORA")

        coVerify(exactly = 1) { repository.update(9, "NOVA CORRETORA") }
        coVerify(exactly = 0) { repository.delete(any()) }
    }

    "delete should delegate to the repository" {
        service.delete(5)

        coVerify(exactly = 1) { repository.delete(5) }
    }
})
