package dev.agner.portfolio.httpapi.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.jvm.javaio.toInputStream
import org.apache.poi.ss.usermodel.WorkbookFactory
import kotlin.reflect.KClass

class XlsxConverter(
    private val mapper: ObjectMapper,
) : ContentConverter {

    val defs: MutableMap<KClass<*>, List<DataDef>> = mutableMapOf()

    inline fun <reified T> register(vararg dataDefs: DataDef) {
        defs[T::class] = dataDefs.asList()
    }

    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel,
    ): Any? {
        val sheetos = WorkbookFactory.create(content.toInputStream()).getSheetAt(0)
        val headers = sheetos.getRow(0).toList().map { it.toString() }

        val klass = defs.toMap().firstNotNullOf {
            // Finding a header definition that matches the given one to get the target class
            if (it.value.map { def -> def.headerName }.sorted() == headers.sorted()) it.key else null
        }
        val definitions = defs[klass]!!

        return sheetos.drop(1)
            .mapNotNull { row ->
                // Doing this because it does not return the cells that are empty, so we must fill them manually
                val rowValues = headers.indices.map { row.getCell(it)?.toString() }

                if (rowValues.all { it == null || it.isEmpty() }) return@mapNotNull null

                headers
                    .zip(rowValues)
                    .toMap()
                    .mapKeys { definitions.firstOrNull { def -> def.headerName == it.key }?.targetProperty }
                    .filterKeys { it != null }
                    .mapValues { definitions.first { def -> def.targetProperty == it.key }.transform(it.value) }
            }
            .map { mapper.convertValue(it, klass.java) }
    }

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?,
    ): OutgoingContent? {
        TODO("Won't be used")
    }
}

data class DataDef(
    val headerName: String,
    val targetProperty: String? = null,
    val transform: (String?.() -> Any?) = { this },
)
