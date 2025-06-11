package dev.agner.portfolio.gateway.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.DefaultUriBuilderFactory
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.time.temporal.ChronoUnit

@Configuration
class HttpClientConfig {

    @Bean
    @StatusInvestClient
    fun webClient(builder: WebClient.Builder, httpClient: HttpClient): WebClient =
        builder
            .uriBuilderFactory(DefaultUriBuilderFactory("http://localhost:8080"))
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()

    @Bean
    fun httpClient(): HttpClient =
        HttpClient.create()
            .compress(true)
            .wiretap(true)
            .responseTimeout(Duration.of(30, ChronoUnit.SECONDS))
}
