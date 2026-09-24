package de.antonlorani.eudi.golfmembership.vci

import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.LinkedHashMap

@Service
class NonceService(
    private val clock: Clock,
) {
    private val lifetime = Duration.ofMinutes(5)
    private val maxOutstandingNonces = 1000
    private val random = SecureRandom()
    private val expirations = LinkedHashMap<String, Instant>()

    @Synchronized
    fun issue(): String {
        removeExpired()
        val bytes = ByteArray(32).also(random::nextBytes)
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        expirations[nonce] = clock.instant().plus(lifetime)
        evict()

        return nonce
    }

    @Synchronized
    fun consume(nonce: String): Boolean {
        val expiration = expirations.remove(nonce) ?: return false
        return clock.instant().isBefore(expiration)
    }

    private fun removeExpired() {
        val now = clock.instant()
        expirations.entries.removeIf { !now.isBefore(it.value) }
    }

    private fun evict() {
        while (expirations.size > maxOutstandingNonces) {
            val oldest = expirations.keys.first()
            expirations.remove(oldest)
        }
    }
}
