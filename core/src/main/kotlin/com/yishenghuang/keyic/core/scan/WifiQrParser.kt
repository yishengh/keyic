package com.yishenghuang.keyic.core.scan

data class WifiQrParseResult(
    val ssid: String,
    val password: String = "",
    /** WPA, WEP, nopass, SAE, etc. */
    val security: String = "nopass",
    val hidden: Boolean = false,
)

object WifiQrParser {
    fun parse(raw: String): Result<WifiQrParseResult> {
        val value = raw.trim()
        if (!value.startsWith("WIFI:", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("not_wifi"))
        }
        val body = value.removePrefix("WIFI:").removePrefix("wifi:")
        val fields = parseFields(body)
        val ssid = fields["S"].orEmpty()
        if (ssid.isBlank()) {
            return Result.failure(IllegalArgumentException("missing_ssid"))
        }
        val security = fields["T"]?.ifBlank { null } ?: "nopass"
        val password = fields["P"].orEmpty()
        val hidden = fields["H"].equals("true", ignoreCase = true)
        return Result.success(
            WifiQrParseResult(
                ssid = ssid,
                password = password,
                security = security,
                hidden = hidden,
            ),
        )
    }

    /**
     * WIFI QR fields are semicolon-separated; backslash escapes \; \: \\ \"
     */
    private fun parseFields(body: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        var i = 0
        while (i < body.length) {
            while (i < body.length && (body[i] == ';' || body[i].isWhitespace())) i++
            if (i >= body.length) break
            val keyStart = i
            while (i < body.length && body[i] != ':' && body[i] != ';') i++
            if (i >= body.length || body[i] != ':') break
            val key = body.substring(keyStart, i).trim().uppercase()
            i++ // skip ':'
            val value = StringBuilder()
            while (i < body.length) {
                val ch = body[i]
                when {
                    ch == '\\' && i + 1 < body.length -> {
                        value.append(body[i + 1])
                        i += 2
                    }
                    ch == ';' -> {
                        i++
                        break
                    }
                    else -> {
                        value.append(ch)
                        i++
                    }
                }
            }
            if (key.isNotEmpty()) out[key] = value.toString()
        }
        return out
    }
}
