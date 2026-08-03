package com.yishenghuang.keyic.core.scan

enum class IdDocumentKind {
    PASSPORT,
    DRIVERS_LICENSE,
}

data class IdDocumentOcrResult(
    val kind: IdDocumentKind? = null,
    val fullName: String? = null,
    val documentNumber: String? = null,
    val expiry: String? = null,
    val details: String? = null,
    val suggestedTitle: String? = null,
) {
    fun merge(other: IdDocumentOcrResult): IdDocumentOcrResult = IdDocumentOcrResult(
        kind = kind ?: other.kind,
        fullName = fullName ?: other.fullName,
        documentNumber = documentNumber ?: other.documentNumber,
        expiry = expiry ?: other.expiry,
        details = listOfNotNull(details, other.details)
            .flatMap { it.lines() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")
            .ifBlank { null },
        suggestedTitle = suggestedTitle ?: other.suggestedTitle,
    )

    val hasIdentity: Boolean
        get() = !documentNumber.isNullOrBlank() && !fullName.isNullOrBlank()

    val isCompleteEnough: Boolean
        get() = hasIdentity
}

object IdDocumentOcrParser {
    private val MRZ_LINE = Regex("""^[A-Z0-9<]{30,44}$""")
    private val EXPIRY = Regex(
        """(?i)(?:exp(?:iry|ires)?|valid\s*(?:thru|until)|有效期[至到]?)\s*[:#\-]?\s*(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})""",
    )
    private val EXPIRY_SIMPLE = Regex("""(?<!\d)(0[1-9]|1[0-2])\s*[/\-.]\s*(\d{2}|\d{4})(?!\d)""")
    private val DOB_LABEL = Regex(
        """(?i)(?:dob|date\s*of\s*birth|birth(?:day)?|出生日期?)\s*[:#\-]?\s*(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})""",
    )
    private val NAME_LABEL = Regex(
        """(?i)(?:full\s*name|name|姓名|持有人)\s*[:#\-]?\s*([A-Z][A-Z .'\-]{1,40}|[\u4e00-\u9fff·]{2,20})""",
    )
    private val PASSPORT_NO = Regex(
        """(?i)(?:passport\s*(?:no|num|number|#)|护照号[码]?)\s*[:#\-]?\s*([A-Z0-9]{6,12})""",
    )
    private val LICENSE_NO = Regex(
        """(?i)(?:(?:dl|licen[cs]e|driver(?:'?s)?\s*licen[cs]e)\s*(?:no|num|number|#)|证号|驾驶证号)\s*[:#\-]?\s*([A-Z0-9\-]{5,20})""",
    )
    private val LICENSE_HINT = Regex("""(?i)driver|licen[cs]e|驾照|驾驶证|\bDL\b""")
    private val PASSPORT_HINT = Regex("""(?i)passport|护照|\bP<\b""")

    fun parse(ocrText: String, preferred: IdDocumentKind? = null): IdDocumentOcrResult {
        val normalized = ocrText
            .replace('\u00A0', ' ')
            .replace('＜', '<')
            .uppercase()
        val mrz = parseMrz(normalized)
        val loose = parseLoose(normalized, preferred)
        return if (mrz != null) mrz.merge(loose) else loose
    }

    private fun parseMrz(text: String): IdDocumentOcrResult? {
        val lines = text.lines()
            .map { it.trim().replace(" ", "").replace("«", "<") }
            .filter { MRZ_LINE.matches(it) }
        if (lines.size < 2) return null
        // Prefer TD3 passport pair (44 chars)
        val pair = lines.windowed(2).firstOrNull { (a, b) ->
            a.length >= 44 && b.length >= 44 && a.startsWith("P")
        } ?: lines.windowed(2).firstOrNull { (a, b) ->
            a.length >= 30 && b.length >= 30
        } ?: return null

        val line1 = pair[0].padEnd(44, '<').take(44)
        val line2 = pair[1].padEnd(44, '<').take(44)
        if (!line1.startsWith("P")) return null

        val namePart = line1.drop(5)
        val nameChunks = namePart.split("<<", limit = 2)
        val surname = nameChunks.getOrNull(0)?.replace("<", " ")?.trim().orEmpty()
        val given = nameChunks.getOrNull(1)?.replace("<", " ")?.trim().orEmpty()
        val fullName = listOf(given, surname).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }

        val documentNumber = line2.take(9).replace("<", "").ifBlank { null }
        val nationality = line2.substring(10, 13).replace("<", "")
        val dob = formatYyMmDd(line2.substring(13, 19))
        val sex = when (line2.getOrNull(20)) {
            'M' -> "M"
            'F' -> "F"
            else -> null
        }
        val expiryRaw = line2.substring(21, 27)
        val expiry = formatYyMmDd(expiryRaw)?.let { toMmYy(it) }

        val details = buildList {
            if (nationality.isNotBlank()) add("Nationality: $nationality")
            if (dob != null) add("DOB: $dob")
            if (sex != null) add("Sex: $sex")
            if (expiry != null) add("Expiry: $expiry")
        }.joinToString("\n").ifBlank { null }

        return IdDocumentOcrResult(
            kind = IdDocumentKind.PASSPORT,
            fullName = fullName,
            documentNumber = documentNumber,
            expiry = expiry,
            details = details,
            suggestedTitle = "Passport",
        )
    }

    private fun parseLoose(text: String, preferred: IdDocumentKind?): IdDocumentOcrResult {
        val kind = preferred ?: when {
            PASSPORT_HINT.containsMatchIn(text) -> IdDocumentKind.PASSPORT
            LICENSE_HINT.containsMatchIn(text) -> IdDocumentKind.DRIVERS_LICENSE
            else -> null
        }
        val documentNumber = when (kind) {
            IdDocumentKind.PASSPORT -> PASSPORT_NO.find(text)?.groupValues?.getOrNull(1)
            IdDocumentKind.DRIVERS_LICENSE -> LICENSE_NO.find(text)?.groupValues?.getOrNull(1)
            null -> PASSPORT_NO.find(text)?.groupValues?.getOrNull(1)
                ?: LICENSE_NO.find(text)?.groupValues?.getOrNull(1)
        }
        val fullName = NAME_LABEL.find(text)?.groupValues?.getOrNull(1)?.trim()
            ?: findLikelyNameLine(text)
        val expiry = EXPIRY.find(text)?.let { match ->
            normalizeDate(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        } ?: EXPIRY_SIMPLE.find(text)?.let {
            val month = it.groupValues[1]
            val year = it.groupValues[2].takeLast(2)
            "$month/$year"
        }
        val dob = DOB_LABEL.find(text)?.let { match ->
            normalizeDate(match.groupValues[1], match.groupValues[2], match.groupValues[3], asIso = true)
        }
        val details = buildList {
            if (dob != null) add("DOB: $dob")
            if (expiry != null) add("Expiry: $expiry")
            if (kind == IdDocumentKind.DRIVERS_LICENSE) {
                Regex("""(?i)class\s*[:#\-]?\s*([A-Z0-9]+)""").find(text)?.groupValues?.getOrNull(1)?.let {
                    add("Class: $it")
                }
            }
        }.joinToString("\n").ifBlank { null }

        return IdDocumentOcrResult(
            kind = kind,
            fullName = fullName,
            documentNumber = documentNumber,
            expiry = expiry,
            details = details,
            suggestedTitle = when (kind) {
                IdDocumentKind.PASSPORT -> "Passport"
                IdDocumentKind.DRIVERS_LICENSE -> "Driver's License"
                null -> null
            },
        )
    }

    private fun findLikelyNameLine(text: String): String? {
        val blocked = setOf(
            "PASSPORT", "LICENSE", "DRIVER", "UNITED", "STATES", "REPUBLIC", "KINGDOM",
            "DATE", "BIRTH", "EXPIRY", "SEX", "CLASS", "ENDORSEMENTS", "PASSPORT",
            "护照", "驾驶证", "姓名",
        )
        return text.lines()
            .map { it.trim() }
            .firstOrNull { line ->
                val letters = line.count { it.isLetter() }
                letters >= 4 &&
                    line.length in 4..40 &&
                    blocked.none { line.contains(it, ignoreCase = true) } &&
                    line.none { it.isDigit() } &&
                    (line.all { it.isUpperCase() || it.isWhitespace() || it == '-' || it == '\'' || it == '·' } ||
                        line.any { it in '\u4e00'..'\u9fff' })
            }
    }

    private fun formatYyMmDd(raw: String): String? {
        if (raw.length != 6 || raw.any { it == '<' || !it.isDigit() }) return null
        val yy = raw.substring(0, 2).toIntOrNull() ?: return null
        val mm = raw.substring(2, 4)
        val dd = raw.substring(4, 6)
        if (mm.toIntOrNull() !in 1..12 || dd.toIntOrNull() !in 1..31) return null
        val year = if (yy <= 40) 2000 + yy else 1900 + yy
        return "%04d-%s-%s".format(year, mm, dd)
    }

    private fun toMmYy(isoDate: String): String? {
        val parts = isoDate.split("-")
        if (parts.size != 3) return null
        return "${parts[1]}/${parts[0].takeLast(2)}"
    }

    private fun normalizeDate(
        a: String,
        b: String,
        c: String,
        asIso: Boolean = false,
    ): String? {
        // Heuristic: if first > 12 treat as DD/MM/YYYY else MM/DD/YYYY when ambiguous;
        // prefer MM/YY style for expiry storage.
        val n1 = a.toIntOrNull() ?: return null
        val n2 = b.toIntOrNull() ?: return null
        val yearRaw = if (c.length == 2) c else c.takeLast(2)
        val (month, day) = when {
            n1 in 1..12 && n2 in 1..31 -> n1 to n2
            n2 in 1..12 && n1 in 1..31 -> n2 to n1
            else -> return null
        }
        val mm = month.toString().padStart(2, '0')
        val dd = day.toString().padStart(2, '0')
        return if (asIso) {
            val year = if (c.length == 4) c else {
                val yy = yearRaw.toIntOrNull() ?: return null
                (if (yy <= 40) 2000 + yy else 1900 + yy).toString()
            }
            "$year-$mm-$dd"
        } else {
            "$mm/$yearRaw"
        }
    }
}
