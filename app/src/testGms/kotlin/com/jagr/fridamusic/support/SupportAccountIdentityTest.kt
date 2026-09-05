package com.jagr.fridamusic.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SupportAccountIdentityTest {
    @Test
    fun `H active user A produces account hash A`() {
        val hashA = obfuscateSupportAccountId("user-a@example.com")

        assertNotNull(hashA)
        assertEquals(64, hashA?.length)
    }

    @Test
    fun `I switching to user B produces hash B`() {
        val hashA = obfuscateSupportAccountId("user-a@example.com")
        val hashB = obfuscateSupportAccountId("user-b@example.com")

        assertNotEquals(hashA, hashB)
    }

    @Test
    fun `J logout does not reuse a prior hash`() {
        val priorHash = obfuscateSupportAccountId("user-a@example.com")
        val loggedOutHash = obfuscateSupportAccountId(null)

        assertNotNull(priorHash)
        assertNull(loggedOutHash)
    }

    @Test
    fun `K logging in as A again restores the same hash`() {
        val firstLogin = obfuscateSupportAccountId("user-a@example.com")
        val secondLogin = obfuscateSupportAccountId("user-a@example.com")

        assertEquals(firstLogin, secondLogin)
    }

    @Test
    fun `L distinct users have distinct hashes`() {
        assertNotEquals(
            obfuscateSupportAccountId("first@example.com"),
            obfuscateSupportAccountId("second@example.com"),
        )
    }

    @Test
    fun `M normalization and restart produce a stable hash`() {
        val beforeRestart = obfuscateSupportAccountId("  User-A@Example.COM ")
        val afterRestart = obfuscateSupportAccountId("user-a@example.com")

        assertEquals(beforeRestart, afterRestart)
    }

    @Test
    fun `N no login produces no billing account identifier`() {
        assertNull(obfuscateSupportAccountId(null))
        assertNull(obfuscateSupportAccountId("   "))
    }

    @Test
    fun `the literal email is never returned`() {
        val email = "private-user@example.com"
        val hash = obfuscateSupportAccountId(email)

        assertFalse(hash.orEmpty().contains(email, ignoreCase = true))
    }
}
