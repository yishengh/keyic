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
}
