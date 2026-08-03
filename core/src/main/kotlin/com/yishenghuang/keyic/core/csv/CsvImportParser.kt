package com.yishenghuang.keyic.core.csv

import com.yishenghuang.keyic.core.model.EntryType
import com.yishenghuang.keyic.core.model.VaultEntryDraft

/**
 * Offline CSV importer for Chrome, Bitwarden, Keyic export, and generic password exports.
 * Header names are matched case-insensitively.
 */
object CsvImportParser {
    fun parse(csvText: String): List<VaultEntryDraft> {
        val rows = parseCsv(csvText)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        val data = rows.drop(1)
        val titleIdx = indexOf(header, "name", "title", "account")
        val typeIdx = indexOf(header, "type", "entry_type")
        val userIdx = indexOf(header, "username", "user", "login", "login_username", "email")
        val passIdx = indexOf(header, "password", "login_password", "pass")
        val urlIdx = indexOf(header, "url", "uri", "login_uri", "website", "web site")
        val notesIdx = indexOf(header, "notes", "note", "comments")
        val totpIdx = indexOf(header, "totp", "otpauth", "twofactor_secret", "2fa", "login_totp")
        val folderIdx = indexOf(header, "folder", "group", "collection")
        val expiryIdx = indexOf(header, "card_expiry", "expiry", "exp")
        val cvvIdx = indexOf(header, "card_cvv", "cvv", "cvc")
        val iconIdx = indexOf(header, "icon", "icon_key", "iconkey")
        val favoriteIdx = indexOf(header, "favorite", "fav")

        return data.mapNotNull { cols ->
            fun col(i: Int): String = cols.getOrNull(i)?.trim().orEmpty()
            val title = when {
                titleIdx >= 0 && col(titleIdx).isNotBlank() -> col(titleIdx)
                urlIdx >= 0 && col(urlIdx).isNotBlank() -> col(urlIdx)
                else -> "Imported"
            }
            val password = if (passIdx >= 0) col(passIdx) else ""
            val username = if (userIdx >= 0) col(userIdx) else ""
            val notes = if (notesIdx >= 0) col(notesIdx) else ""
            if (title.isBlank() && password.isBlank() && username.isBlank() && notes.isBlank()) {
                return@mapNotNull null
            }
            val tags = buildList {
                if (folderIdx >= 0 && col(folderIdx).isNotBlank()) {
                    col(folderIdx).split(';', '|').map { it.trim() }.filter { it.isNotEmpty() }
                        .forEach { add(it) }
                }
            }
            val type = parseType(if (typeIdx >= 0) col(typeIdx) else "")
            VaultEntryDraft(
                title = title.ifBlank { "Imported" },
                type = type,
                username = username,
                password = password,
                url = if (urlIdx >= 0) col(urlIdx) else "",
                totpSecret = normalizeTotp(if (totpIdx >= 0) col(totpIdx) else ""),
                notes = notes,
                tags = tags,
                favorite = favoriteIdx >= 0 && col(favoriteIdx).equals("true", ignoreCase = true),
                cardExpiry = if (expiryIdx >= 0) col(expiryIdx) else "",
                cardCvv = if (cvvIdx >= 0) col(cvvIdx) else "",
                iconKey = if (iconIdx >= 0) col(iconIdx).ifBlank { null } else null,
            )
        }
    }

    private fun parseType(raw: String): EntryType {
        if (raw.isBlank()) return EntryType.LOGIN
        val normalized = raw.trim().uppercase().replace(' ', '_')
        return when (normalized) {
            "NOTE", "SECURE_NOTE", "SECURENOTE", "NOTES" -> EntryType.NOTE
            "CARD", "CREDIT_CARD", "CREDITCARD", "PAYMENT" -> EntryType.CARD
            "IDENTITY", "ID", "PERSON" -> EntryType.IDENTITY
            "LOGIN", "PASSWORD", "1" -> EntryType.LOGIN
            else -> runCatching { EntryType.valueOf(normalized) }.getOrDefault(EntryType.LOGIN)
        }
    }

    fun export(entries: List<com.yishenghuang.keyic.core.model.VaultEntry>): String {
        val header = "name,type,url,username,password,totp,notes,folder,card_expiry,card_cvv,icon"
        val lines = entries.map { e ->
            listOf(
                e.title,
                e.type.name,
                e.url,
                e.username,
                e.password,
                e.totpSecret.orEmpty(),
                e.notes,
                e.tags.joinToString(";"),
                e.cardExpiry,
                e.cardCvv,
                e.iconKey.orEmpty(),
            ).joinToString(",") { csvEscape(it) }
        }
        return (listOf(header) + lines).joinToString("\n")
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun normalizeTotp(raw: String): String? {
        if (raw.isBlank()) return null
        val value = raw.trim()
        if (value.startsWith("otpauth://", ignoreCase = true)) {
            val secret = Regex("[?&]secret=([^&]+)", RegexOption.IGNORE_CASE)
                .find(value)?.groupValues?.getOrNull(1)
            return secret?.replace(" ", "")?.uppercase()
        }
        return value.replace(" ", "").uppercase()
    }

    private fun indexOf(header: List<String>, vararg names: String): Int {
        names.forEach { name ->
            val i = header.indexOf(name)
            if (i >= 0) return i
        }
        return -1
    }

    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val field = StringBuilder()
        val row = mutableListOf<String>()
        var i = 0
        var inQuotes = false
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> when (ch) {
                    '"' -> {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    }
                    else -> field.append(ch)
                }
                ch == '"' -> inQuotes = true
                ch == ',' -> {
                    row += field.toString()
                    field.clear()
                }
                ch == '\n' -> {
                    row += field.toString()
                    field.clear()
                    if (row.any { it.isNotBlank() }) rows += row.toList()
                    row.clear()
                }
                ch == '\r' -> Unit
                else -> field.append(ch)
            }
            i++
        }
        row += field.toString()
        if (row.any { it.isNotBlank() }) rows += row
        return rows
    }
}
