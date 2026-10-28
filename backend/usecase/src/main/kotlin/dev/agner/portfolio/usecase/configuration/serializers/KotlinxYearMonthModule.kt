package dev.agner.portfolio.usecase.configuration.serializers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import kotlinx.datetime.YearMonth

// Serializes a kotlinx YearMonth as its ISO "YYYY-MM" form. Without this the mapper would write its
// component fields (year/month), which is not the shape the edge contract — or the frontend's
// `period` string — speaks.
class KotlinxYearMonthModule : SimpleModule() {
    init {
        addSerializer(
            YearMonth::class.java,
            object : JsonSerializer<YearMonth>() {
                override fun serialize(value: YearMonth, gen: JsonGenerator, serializers: SerializerProvider) {
                    gen.writeString(value.toString())
                }
            },
        )
        addDeserializer(
            YearMonth::class.java,
            object : JsonDeserializer<YearMonth>() {
                override fun deserialize(p: JsonParser, ctxt: DeserializationContext) =
                    YearMonth.parse(p.codec.readTree<JsonNode>(p).textValue())
            },
        )
    }
}
