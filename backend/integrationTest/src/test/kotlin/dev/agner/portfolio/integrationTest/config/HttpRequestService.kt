package dev.agner.portfolio.integrationTest.config

import dev.agner.portfolio.integrationTest.helpers.getBean
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.path

class BaseHttpRequestTemplate {
    lateinit var path: String
    var body: Any = emptyMap<String, String>()
}

// The app port is the one MySqlContainerExtension published for this run, so a request follows
// the server instead of assuming a dev host port.
fun appBaseUrl(): String {
    val port = checkNotNull(System.getProperty("ktor.port")) {
        "the integration harness has not published ktor.port"
    }
    return "http://localhost:$port"
}

suspend inline fun <reified T> getRequest(crossinline configure: BaseHttpRequestTemplate.() -> Unit) =
    getBean<HttpClient>().get(appBaseUrl()) {
        url {
            path(BaseHttpRequestTemplate().apply(configure).path)
        }
    }.body<T>()

suspend inline fun <reified T> postRequest(crossinline configure: BaseHttpRequestTemplate.() -> Unit) =
    getBean<HttpClient>().post(appBaseUrl()) {
        url {
            path(BaseHttpRequestTemplate().apply(configure).path)
        }
        contentType(ContentType.Application.Json)
        setBody(BaseHttpRequestTemplate().apply(configure).body)
    }.body<T>()
