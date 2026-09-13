package dev.agner.portfolio.integrationTest.config

class BaseHttpRequestTemplate {
    lateinit var path: String
    var body: Any = emptyMap<String, String>()
}
