package dev.agner.portfolio.usecase.configuration

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.agner.chameidor.usecase.configuration.serializers.KotlinxDatePeriodModule
import dev.agner.chameidor.usecase.configuration.serializers.KotlinxLocalDateModule
import dev.agner.chameidor.usecase.configuration.serializers.KotlinxLocalDateTimeModule
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

@Component
object JsonMapper {

    val mapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .registerModule(KotlinxLocalDateTimeModule())
        .registerModule(KotlinxLocalDateModule())
        .registerModule(KotlinxDatePeriodModule())
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

    @Bean
    fun jsonAdapter() = mapper
}
