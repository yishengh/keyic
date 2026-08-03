package com.yishenghuang.keyic.core.scan

import java.net.URLDecoder

data class TotpUriParseResult(
    val secret: String,
    val issuer: String? = null,
    val account: String? = null,
)

object TotpUriParser {
    private val BASE32 = Regex("^[A-Z2-7]+=*$")

    fun parse(raw: String): Result<TotpUriParseResult> {
        val value = raw.trim()
        if (value.isEmpty()) {
            return Result.failure(IllegalArgumentException("empty"))
        }
        if (!value.startsWith("otpauth://", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("not_otpauth"))
        }
        val withoutScheme = value.removePrefix("otpauth://").removePrefix("OTPAUTH://")
        val typeAndRest = withoutScheme.split("?", limit = 2)
        val pathPart = typeAndRest[0]
        val query = typeAndRest.getOrNull(1).orEmpty()
        if (!pathPart.startsWith("totp/", ignoreCase = true) &&
            !pathPart.equals("totp", ignoreCase = true)
        ) {
            // Accept hotp labels but we only store secret for TOTP use; reject non-totp types.
            if (!pathPart.startsWith("hotp/", ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("unsupported_type"))
            }
        }
        val labelEncoded = pathPart.substringAfter('/', missingDelimiterValue = "")
        val label = decode(labelEncoded)
        val params = parseQuery(query)
        val secretRaw = params["secret"]?.replace(" ", "")?.uppercase().orEmpty()
        if (secretRaw.isEmpty() || !BASE32.matches(secretRaw)) {
            return Result.failure(IllegalArgumentException("bad_secret"))
        }
        val issuerParam = params["issuer"]?.takeIf { it.isNotBlank() }
        val (issuerFromLabel, accountFromLabel) = splitLabel(label)
        return Result.success(
            TotpUriParseResult(
                secret = secretRaw,
                issuer = issuerParam ?: issuerFromLabel,
                account = accountFromLabel ?: label.takeIf { issuerFromLabel == null && it.isNotBlank() },
            ),
        )
    }

    private fun splitLabel(label: String): Pair<String?, String?> {
        if (label.isBlank()) return null to null
        val idx = label.indexOf(':')
        if (idx <= 0) return null to label
        val issuer = label.substring(0, idx).trim().ifBlank { null }
        val account = label.substring(idx + 1).trim().ifBlank { null }
        return issuer to account
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val key = decode(part.substring(0, i)).lowercase()
            val value = decode(part.substring(i + 1))
            key to value
        }.toMap()
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name()) }
            .getOrDefault(value)
}
