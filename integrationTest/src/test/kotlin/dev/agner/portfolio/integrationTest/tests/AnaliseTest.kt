package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.IntegrationTest
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.testContextManager
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

@IntegrationTest
class AnaliseTest : StringSpec({

    "aaa" {
        val client = testContextManager().testContext.applicationContext.getBean(HttpClient::class.java)
        val indx = client
            .get("http://localhost:8080/indexes")
            .body<List<Map<String, String>>>()

        indx.size shouldBe 3
    }

    "bbb" {
        val client = testContextManager().testContext.applicationContext.getBean(HttpClient::class.java)
        val indx = client
            .get("http://localhost:8080/indexes")
            .body<List<Map<String, String>>>()

        indx.size shouldBe 3
    }
})
