package dev.agner.portfolio.integrationTest.config

import io.kotest.extensions.spring.testContextManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.path

class BaseHttpRequestTemplate {
    lateinit var path: String
    var body: Any = emptyMap<String, String>()
}

suspend fun getClient() = testContextManager().testContext.applicationContext.getBean(HttpClient::class.java)

suspend inline fun <reified T> getRequest(crossinline configure: BaseHttpRequestTemplate.() -> Unit) =
    getClient().get("http://localhost:8080") {
        url {
            path(BaseHttpRequestTemplate().apply(configure).path)
        }
    }.body<T>()

suspend inline fun <reified T> postRequest(crossinline configure: BaseHttpRequestTemplate.() -> Unit) =
    getClient().post("http://localhost:8080") {
        url {
            path(BaseHttpRequestTemplate().apply(configure).path)
        }
    }.body<T>()
