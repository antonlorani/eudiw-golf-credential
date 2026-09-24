package de.antonlorani.eudi.golfmembership.vci

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class VciIssuanceServiceTest {
    private val now = Instant.parse("2026-09-24T12:00:00Z")
    private val clock = MutableClock(now)
    private val service = VciIssuanceService(clock)

    @Test
    fun `offer expires after five minutes`() {
        val offer = service.createOffer()

        clock.advance(Duration.ofMinutes(5))

        assertNull(service.getOffer(offer.id))
        assertFalse(setAuthorizationParams(offer.id))
    }

    @Test
    fun `session expires after fifteen minutes`() {
        val offer = service.createOffer()

        clock.advance(Duration.ofMinutes(15))

        assertNull(service.authorize(offer.id, 0))
    }

    @Test
    fun `authorization code expires after one minute`() {
        val authorized = authorizedSession()

        clock.advance(Duration.ofMinutes(1))

        assertNull(service.findByAuthorizationCode(authorized.authorizationCode!!))
        assertNull(service.exchangeCode(authorized.authorizationCode, VERIFIER, REDIRECT_URI))
    }

    @Test
    fun `access token expires after five minutes`() {
        val token = tokenSession().accessToken!!

        clock.advance(Duration.ofMinutes(5))

        assertNull(service.findByAccessToken(token))
        assertNull(service.consumeAccessToken(token))
    }

    @Test
    fun `access token can be consumed exactly once concurrently`() {
        val token = tokenSession().accessToken!!
        val executor = Executors.newFixedThreadPool(8)

        val results = try {
            executor.invokeAll(List(32) { Callable { service.consumeAccessToken(token) } })
                .map { it.get() }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, results.count { it != null })
    }

    private fun authorizedSession(): VciSession {
        val offer = service.createOffer()
        assertEquals(true, setAuthorizationParams(offer.id))
        return requireNotNull(service.authorize(offer.id, 0))
    }

    private fun tokenSession(): VciSession {
        val authorized = authorizedSession()
        return requireNotNull(service.exchangeCode(authorized.authorizationCode!!, VERIFIER, REDIRECT_URI))
    }

    private fun setAuthorizationParams(offerId: String): Boolean {
        return service.setAuthorizationParams(
            offerId = offerId,
            redirectUri = REDIRECT_URI,
            clientState = "state",
            codeChallenge = CHALLENGE,
            codeChallengeMethod = "S256",
            clientId = "wallet-client",
            scope = CredentialData.SCOPE,
            dpopKeyThumbprint = "thumbprint",
        )
    }

    private class MutableClock(
        private var instant: Instant,
        private val zone: ZoneId = ZoneOffset.UTC,
    ) : Clock() {
        override fun instant(): Instant = instant

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }
    }

    private companion object {
        const val VERIFIER = "verifier"
        const val REDIRECT_URI = "eudi-wallet://authorization"
        val CHALLENGE: String = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(VERIFIER.toByteArray(Charsets.US_ASCII))
        )
    }
}
