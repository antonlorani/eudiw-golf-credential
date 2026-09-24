package de.antonlorani.eudi.golfmembership.vci

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class NonceServiceTest {
    private val service = NonceService(
        Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"), ZoneOffset.UTC)
    )

    @Test
    fun `bounds the number of outstanding nonces`() {
        val oldest = service.issue()
        repeat(1000) { service.issue() }
        val newest = service.issue()

        assertFalse(service.consume(oldest))
        assertTrue(service.consume(newest))
    }
}
