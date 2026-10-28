package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.import.model.ImportRowPayload

// A broker-file layout parser. Adding a layout is adding a class: `shouldExecute` claims a file,
// `parse` turns it into staged rows. It lives in http-api so the usecase layer never sees bytes,
// a file, or a content type.
interface IImportParser {

    fun shouldExecute(fileName: String, content: ByteArray): Boolean

    fun parse(fileName: String, content: ByteArray): List<ImportRowPayload>
}
