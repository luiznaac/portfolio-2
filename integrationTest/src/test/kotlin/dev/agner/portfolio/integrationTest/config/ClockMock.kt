package dev.agner.portfolio.integrationTest.config

import io.mockk.mockk
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.time.Clock

@Component
object ClockMock {

    val clock = mockk<Clock>()

    @Bean
    @Primary
    fun clock() = clock
}
