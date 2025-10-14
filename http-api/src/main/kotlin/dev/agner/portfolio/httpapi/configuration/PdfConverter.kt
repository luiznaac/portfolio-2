package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.commons.brazilianLocalDateFormat
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.upload.model.UploadOrder
import dev.agner.portfolio.usecase.upload.model.UploadOrder.Action
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.toByteArray
import kotlinx.datetime.LocalDate
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

class PdfConverter : ContentConverter {

    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel,
    ): Any? {
        val doc = Loader.loadPDF(content.toByteArray())

        data class Buff(
            val orphanMovements: List<String> = emptyList(),
            val movements: Map<LocalDate, List<String>> = emptyMap(),
        )

        val txt = PDFTextStripper().getText(doc).lines()

        return txt.fold(Buff()) { acc, line ->
            when {
                line.run { contains("Guardado") || contains("Resgatado") } -> {
                    acc.copy(orphanMovements = acc.orphanMovements + line)
                }

                line.contains("Data:") && acc.orphanMovements.isNotEmpty() -> {
                    val date = LocalDate.parse(
                        line.substringAfter("Data:").trim().replaceMonth(),
                        brazilianLocalDateFormat,
                    )

                    acc.copy(
                        orphanMovements = emptyList(),
                        movements = acc.movements + (date to acc.orphanMovements),
                    )
                }

                else -> acc
            }
        }
            .movements
            .flatMap {
                it.value.map { movement ->
                    UploadOrder(
                        it.key,
                        Action.fromValue(movement.substringBefore(" ")),
                        movement.substringAfter("R$").sanitizeCurrency().toBigDecimal().defaultScale(),
                    )
                }
            }
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

private fun String.sanitizeCurrency() = trim().replace(".", "").replace(",", ".")

private val MONTH_REPLACEMENTS = mapOf(
    "jan" to "01",
    "fev" to "02",
    "mar" to "03",
    "abr" to "04",
    "mai" to "05",
    "jun" to "06",
    "jul" to "07",
    "ago" to "08",
    "set" to "09",
    "out" to "10",
    "nov" to "11",
    "dez" to "12",
)

private fun String.replaceMonth() =
    MONTH_REPLACEMENTS.entries.firstNotNullOf { (month, number) ->
        if (contains(month)) replace(month, number) else null
    }
