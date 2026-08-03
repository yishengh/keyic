package com.yishenghuang.keyic.core.scan

data class CardOcrResult(
    val number: String? = null,
    val expiry: String? = null,
    val holder: String? = null,
    val cvv: String? = null,
) {
    /** Prefer non-null fields from [other] when this side is missing. */
    fun merge(other: CardOcrResult): CardOcrResult = CardOcrResult(
        number = number ?: other.number,
        expiry = expiry ?: other.expiry,
        holder = holder ?: other.holder,
        cvv = cvv ?: other.cvv,
    )

    val hasNumber: Boolean get() = !number.isNullOrBlank()
    val hasExpiry: Boolean get() = !expiry.isNullOrBlank()
    val isCompleteEnough: Boolean get() = hasNumber && hasExpiry
}

object CardOcrParser {
    private val EXPIRY_PATTERNS = listOf(
        // 12/30, 12-30, 12.30, 12 30
        Regex("""(?<!\d)(0[1-9]|1[0-2])\s*[/\-.]\s*([0-9]{2})(?!\d)"""),
        Regex("""(?<!\d)(0[1-9]|1[0-2])\s*[/\-.]\s*(20[2-9][0-9])(?!\d)"""),
        // MMYY glued: 1230 (avoid matching inside long card numbers by context)
        Regex("""(?i)(?:exp(?:iry|ires)?|valid\s*(?:thru|through|until)|good\s*thru|有效期[至到]?)[^\d]{0,8}(0[1-9]|1[0-2])\s*[/\-.]?\s*([0-9]{2}|20[2-9][0-9])"""),
        Regex("""(?i)(?:exp(?:iry|ires)?|valid\s*(?:thru|through|until)|good\s*thru|有效期[至到]?)\s*(0[1-9]|1[0-2])([0-9]{2})(?!\d)"""),
        // Month and year on nearby tokens: 12 / 2030
        Regex("""(?<!\d)(0[1-9]|1[0-2])\s*/\s*(20[2-9][0-9])(?!\d)"""),
    )

    private val CVV_PATTERNS = listOf(
        Regex("""(?i)(?:cvv2?|cvc2?|cid|cavv|security\s*code|sec(?:urity)?\s*code|安全码|校验码)\s*[:#\-.]?\s*([0-9]{3,4})"""),
        Regex("""(?i)(?:cvv2?|cvc2?|cid)\s*([0-9]{3,4})"""),
    )

    private val HOLDER_LINE = Regex("""^[A-Z][A-Z .'-]{2,}$""")
    private val BLOCKED = setOf(
        "VISA", "MASTERCARD", "MASTER CARD", "AMEX", "AMERICAN EXPRESS", "DISCOVER",
        "UNIONPAY", "CHINA UNIONPAY", "VALID", "THRU", "GOOD", "FROM", "CREDIT",
        "DEBIT", "CARD", "BANK", "EXPIRES", "EXPIRY", "MONTH", "YEAR", "CVV", "CVC",
        "THROUGH", "UNTIL",
    )

    fun parse(ocrText: String): CardOcrResult {
        val normalized = ocrText
            .replace('\u00A0', ' ')
            .replace('／', '/')
            .replace('－', '-')
            .replace('．', '.')
            .replace('：', ':')
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val joined = lines.joinToString("\n")
        val number = findCardNumber(joined)
        val expiry = findExpiry(joined)
        val holder = findHolder(lines)
        val cvv = findCvv(joined, number)
        return CardOcrResult(number = number, expiry = expiry, holder = holder, cvv = cvv)
    }

    fun luhnValid(digits: String): Boolean {
        if (digits.length !in 13..19) return false
        if (!digits.all { it.isDigit() }) return false
        var sum = 0
        var alt = false
        for (i in digits.indices.reversed()) {
            var n = digits[i] - '0'
            if (alt) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alt = !alt
        }
        return sum % 10 == 0
    }

    private fun findCardNumber(text: String): String? {
        val candidates = Regex("""(?:\d[ \-]*){13,19}""")
            .findAll(text)
            .map { it.value.filter { ch -> ch.isDigit() } }
            .filter { luhnValid(it) }
            .toList()
        return candidates.maxByOrNull { it.length }
    }

    private fun findExpiry(text: String): String? {
        for (pattern in EXPIRY_PATTERNS) {
            val match = pattern.find(text) ?: continue
            val month = match.groupValues[1].padStart(2, '0')
            val yearRaw = match.groupValues[2]
            val year = if (yearRaw.length == 4) yearRaw.takeLast(2) else yearRaw
            val monthInt = month.toIntOrNull() ?: continue
            if (monthInt !in 1..12) continue
            return "$month/$year"
        }
        // Spaced digits near VALID THRU style lines: "1 2 / 3 0"
        val spaced = Regex(
            """(?i)(?:exp|valid|thru|good|有效期)[^\n]{0,20}?([0-1])\s*([0-9])\s*[/\-.]\s*([0-9])\s*([0-9])""",
        ).find(text)
        if (spaced != null) {
            val month = "${spaced.groupValues[1]}${spaced.groupValues[2]}"
            val year = "${spaced.groupValues[3]}${spaced.groupValues[4]}"
            if (month.toIntOrNull() in 1..12) return "$month/$year"
        }
        return null
    }

    private fun findCvv(text: String, cardNumber: String?): String? {
        for (pattern in CVV_PATTERNS) {
            val match = pattern.find(text) ?: continue
            val code = match.groupValues[1]
            if (cardNumber != null && cardNumber.contains(code)) continue
            return code
        }
        return null
    }

    private fun findHolder(lines: List<String>): String? {
        val candidates = lines.mapNotNull { line ->
            val cleaned = line
                .replace(Regex("""\d"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .uppercase()
            if (cleaned.length < 3) return@mapNotNull null
            if (BLOCKED.any { cleaned.contains(it) }) return@mapNotNull null
            if (cleaned.contains('/') || cleaned.contains('-')) return@mapNotNull null
            if (!HOLDER_LINE.matches(cleaned)) return@mapNotNull null
            cleaned
        }
        return candidates.lastOrNull()
    }
}
