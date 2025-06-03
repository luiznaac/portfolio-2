package dev.agner.portfolio.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = ["dev.agner.portfolio"])
class Boot

fun main() {
    runApplication<Boot>()
}
