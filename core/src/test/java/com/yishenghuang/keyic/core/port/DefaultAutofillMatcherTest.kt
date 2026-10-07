package com.yishenghuang.keyic.core.port

import com.yishenghuang.keyic.core.model.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAutofillMatcherTest {
    @Test
    fun prefersDomainAndRecency() {
        val now = System.currentTimeMillis()
        val older = VaultEntry(
            id = "1",
            title = "Old Bank",
            username = "a",
            url = "https://bank.example",
            createdAt = now - 40L * 86_400_000L,
            updatedAt = now - 40L * 86_400_000L,
            passwordChangedAt = now,
        )
        val newer = VaultEntry(
            id = "2",
            title = "New Bank",
            username = "b",
            url = "https://www.bank.example/login",
            createdAt = now,
            updatedAt = now,
            passwordChangedAt = now,
        )
        val matched = DefaultAutofillMatcher().match(
            packageName = null,
            webDomain = "bank.example",
            entries = listOf(older, newer),
        )
        assertEquals("2", matched.first().id)
        assertTrue(matched.size >= 2)
    }

    @Test
    fun normalizeDomainStripsWww() {
        assertEquals(
            "example.com",
            DefaultAutofillMatcher.normalizeDomain("https://www.example.com/path"),
        )
    }

    private fun entry(url: String = "https://bank.example") = VaultEntry(
        id = "synthetic", title = "Bank", url = url, favorite = true,
        createdAt = 0, updatedAt = System.currentTimeMillis(), passwordChangedAt = 0,
    )

    @Test fun doesNotSuggestSecretsToLookalikeOrUnrelatedTargets() {
        val matcher = DefaultAutofillMatcher()
        for (host in listOf("evil.example", "bank.example.evil.test", "evilbank.example", "https://bank.example@evil.test")) {
            assertTrue(matcher.match("com.evil.bank", host, listOf(entry())).isEmpty())
        }
        assertTrue(matcher.match("com.bank.evil", null,
            listOf(entry().copy(packageHints = listOf("com.bank")))).isEmpty())
        assertTrue(matcher.match("com.bank", null,
            listOf(entry().copy(packageHints = listOf("com.bank")))).isNotEmpty())
    }

    @Test fun doesNotFillNotesOrLoginPasswordsIntoCardFields() {
        val matcher = DefaultAutofillMatcher()
        assertTrue(matcher.match(null, "bank.example", listOf(entry()), preferCards = true).isEmpty())
        assertTrue(matcher.match(null, "bank.example",
            listOf(entry().copy(type = com.yishenghuang.keyic.core.model.EntryType.NOTE))).isEmpty())
        assertTrue(matcher.match(null, "bank.example", listOf(entry().copy(deletedAt = 1))).isEmpty())
    }
}
