package dev.agner.portfolio.application

import dev.agner.portfolio.httpapi.configuration.KtorConfig
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan(basePackages = ["dev.agner.portfolio"])
class Boot

fun main() {
    val springContext = AnnotationConfigApplicationContext(Boot::class.java)
    springContext.getBean(KtorConfig::class.java).start()
}
