package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
import dev.agner.portfolio.usecase.import.model.ImportRowPayload

// A broker-file layout parser. Adding a layout is adding a class: `shouldExecute` claims a file,
// `parse` turns it into staged rows. It lives in http-api so the usecase layer never sees bytes,
// a file, or a content type. `destination` keeps the layouts disjoint: the resolver only asks the
// parsers that serve the upload's destination, so a custody export can never be staged as trades.
interface IImportParser {

    val destination: ImportDestinationObject

    fun shouldExecute(fileName: String, content: ByteArray): Boolean

    fun parse(fileName: String, content: ByteArray): List<ImportRowPayload>
}
