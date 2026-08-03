package com.yishenghuang.keyic.data.backup

import com.yishenghuang.keyic.core.model.CustomField
import com.yishenghuang.keyic.core.model.EntryType
import com.yishenghuang.keyic.core.model.VaultEntry
import com.yishenghuang.keyic.core.port.KdbxBinary
import com.yishenghuang.keyic.core.port.KdbxImportResult
import com.yishenghuang.keyic.core.port.KdbxImportedAttachment
import org.linguafranca.pwdb.Entry
import org.linguafranca.pwdb.kdbx.KdbxCreds
import org.linguafranca.pwdb.kdbx.jackson.JacksonDatabase
import org.linguafranca.pwdb.kdbx.jackson.JacksonEntry
import org.linguafranca.pwdb.kdbx.jackson.JacksonGroup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * KeePass .kdbx import/export via KeePassJava2 (Jackson).
 * Maps custom string properties ↔ [CustomField]; binary properties ↔ attachments.
 */
object KdbxAdapter {
    private val reservedProps = Entry.STANDARD_PROPERTY_NAMES.map { it.lowercase() }.toSet() +
        setOf("otp", "totp seed", "totp", "timeotp-secret-base32")

    fun importKdbx(bytes: ByteArray, passphrase: CharArray): KdbxImportResult {
        val creds = KdbxCreds(String(passphrase).toByteArray(StandardCharsets.UTF_8))
        passphrase.fill('\u0000')
        val db = JacksonDatabase.load(creds, ByteArrayInputStream(bytes))
        val now = System.currentTimeMillis()
        val out = mutableListOf<VaultEntry>()
        val attachments = mutableListOf<KdbxImportedAttachment>()
        collectEntries(db.rootGroup, emptyList(), now, out, attachments)
        return KdbxImportResult(entries = out, attachments = attachments)
    }

    fun exportKdbx(
        entries: List<VaultEntry>,
        passphrase: CharArray,
        binariesByEntryId: Map<String, List<KdbxBinary>> = emptyMap(),
    ): ByteArray {
        val creds = KdbxCreds(String(passphrase).toByteArray(StandardCharsets.UTF_8))
        passphrase.fill('\u0000')
        val db = JacksonDatabase()
        val root = db.rootGroup
        entries.forEach { vault ->
            val entry = db.newEntry()
            entry.title = vault.title
            entry.username = vault.username
            entry.password = vault.password
            entry.url = vault.url
            entry.notes = buildString {
                append(vault.notes)
                if (!vault.totpSecret.isNullOrBlank()) {
                    if (isNotEmpty()) append('\n')
                    append("otpauth://totp/")
                    append(vault.title)
                    append("?secret=")
                    append(vault.totpSecret)
                }
            }
            if (!vault.totpSecret.isNullOrBlank()) {
                entry.setProperty("otp", "otpauth://totp/${vault.title}?secret=${vault.totpSecret}")
            }
            vault.customFields.forEach { field ->
                val name = field.name.ifBlank { "Field" }
                if (name.lowercase() !in reservedProps) {
                    entry.setProperty(name, field.value)
                }
            }
            binariesByEntryId[vault.id].orEmpty().forEach { binary ->
                val name = binary.fileName.ifBlank { "attachment.bin" }
                entry.setBinaryProperty(name, binary.data)
            }
            root.addEntry(entry)
        }
        val baos = ByteArrayOutputStream()
        db.save(creds, baos)
        return baos.toByteArray()
    }

    private fun collectEntries(
        group: JacksonGroup,
        pathTags: List<String>,
        now: Long,
        out: MutableList<VaultEntry>,
        attachments: MutableList<KdbxImportedAttachment>,
    ) {
        val tags = if (group.name.isNullOrBlank() || group.parent == null) {
            pathTags
        } else {
            pathTags + group.name
        }
        group.entries.forEach { entry: JacksonEntry ->
            val notes = entry.notes.orEmpty()
            val totp = extractTotp(notes)
                ?: entry.propertyNames
                    .firstOrNull {
                        it.equals("otp", ignoreCase = true) ||
                            it.equals("TOTP Seed", ignoreCase = true) ||
                            it.equals("TimeOtp-Secret-Base32", ignoreCase = true)
                    }
                    ?.let { extractTotp(entry.getProperty(it).orEmpty()) }
            val customFields = entry.propertyNames
                .filter { name ->
                    val lower = name.lowercase()
                    lower !in reservedProps &&
                        !lower.startsWith("timeotp-") &&
                        !lower.startsWith("hmacotp-")
                }
                .map { name ->
                    CustomField(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        value = entry.getProperty(name).orEmpty(),
                        masked = name.contains("secret", ignoreCase = true) ||
                            name.contains("pin", ignoreCase = true) ||
                            name.contains("cvv", ignoreCase = true),
                    )
                }
            val entryId = UUID.randomUUID().toString()
            val type = detectType(entry, customFields)
            out += VaultEntry(
                id = entryId,
                title = entry.title.orEmpty().ifBlank { "Untitled" },
                type = type,
                username = entry.username.orEmpty(),
                password = entry.password.orEmpty(),
                url = entry.url.orEmpty(),
                totpSecret = totp,
                notes = notes,
                tags = tags,
                favorite = false,
                customFields = customFields,
                createdAt = now,
                updatedAt = now,
                passwordChangedAt = now,
            )
            entry.binaryPropertyNames.forEach { binName ->
                val data = entry.getBinaryProperty(binName) ?: return@forEach
                if (data.isEmpty()) return@forEach
                attachments += KdbxImportedAttachment(
                    entryId = entryId,
                    fileName = binName.ifBlank { "attachment.bin" },
                    data = data,
                )
            }
        }
        group.groups.forEach { child ->
            collectEntries(child, tags, now, out, attachments)
        }
    }

    private fun detectType(entry: JacksonEntry, fields: List<CustomField>): EntryType {
        val names = fields.map { it.name.lowercase() } +
            listOfNotNull(entry.title?.lowercase(), entry.notes?.lowercase())
        val joined = names.joinToString(" ")
        if (joined.contains("card") ||
            fields.any {
                it.name.contains("cvv", ignoreCase = true) ||
                    it.name.contains("expiry", ignoreCase = true) ||
                    it.name.contains("card number", ignoreCase = true)
            }
        ) {
            return EntryType.CARD
        }
        if (entry.username.isNullOrBlank() &&
            entry.password.isNullOrBlank() &&
            entry.url.isNullOrBlank() &&
            !entry.notes.isNullOrBlank()
        ) {
            return EntryType.NOTE
        }
        return EntryType.LOGIN
    }

    private fun extractTotp(raw: String): String? {
        if (raw.isBlank()) return null
        val otpauth = Regex("otpauth://[^\\s]+", RegexOption.IGNORE_CASE).find(raw)?.value
        if (otpauth != null) {
            return Regex("[?&]secret=([^&]+)", RegexOption.IGNORE_CASE)
                .find(otpauth)?.groupValues?.getOrNull(1)
                ?.replace(" ", "")
                ?.uppercase()
        }
        val cleaned = raw.replace(" ", "").uppercase()
        return cleaned.takeIf { it.matches(Regex("^[A-Z2-7]+=*$")) && it.length >= 16 }
    }
}
