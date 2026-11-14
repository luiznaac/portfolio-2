package dev.agner.portfolio.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.env.Environment

class ProdPortContractTest : StringSpec({

    "the prod profile takes the API port and the callback host from API_PORT" {
        withProdEnvironment(apiPort = "9090") { environment ->
            environment.getProperty("ktor.port") shouldBe "9090"
            environment.getProperty("app-own-host") shouldBe "portfolio:9090"
        }
    }

    "the prod profile falls back to the image's 8080 when API_PORT is absent" {
        withProdEnvironment(apiPort = null) { environment ->
            environment.getProperty("ktor.port") shouldBe "8080"
            environment.getProperty("app-own-host") shouldBe "portfolio:8080"
        }
    }
})

private fun withProdEnvironment(apiPort: String?, assertions: (Environment) -> Unit) {
    val properties = buildList {
        add("spring.profiles.active=prod")
        if (apiPort != null) add("API_PORT=$apiPort")
    }

    ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withPropertyValues(*properties.toTypedArray())
        .run { assertions(it.environment) }
}
