package dev.agner.portfolio.usecase.import.model

import java.security.MessageDigest
import java.util.HexFormat

// The file's identity for import idempotency: the SHA-256 of its bytes, lower-case hex.
fun ByteArray.sha256Hex(): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(this))
