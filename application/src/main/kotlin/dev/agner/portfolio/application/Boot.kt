package dev.agner.portfolio.application

import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan(basePackages = ["dev.agner.portfolio"])
class Boot

fun main() {
    runApplication<Boot>()
}
