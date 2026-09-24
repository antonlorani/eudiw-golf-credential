package de.antonlorani.eudi.golfmembership.vci

import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Service
class VciIssuanceService(
    private val clock: Clock,
) {

    private val maxSessions = 100
    private val sessionLifetime = Duration.ofMinutes(15)
    private val offerLifetime = Duration.ofMinutes(5)
    private val authorizationCodeLifetime = Duration.ofMinutes(1)
    private val accessTokenLifetime = Duration.ofMinutes(5)
    private val sessions = ConcurrentHashMap<String, VciSession>()
    private val authCodeIndex = ConcurrentHashMap<String, AuthorizationCode>()
    private val tokenIndex = ConcurrentHashMap<String, AccessToken>()
    private val insertionOrder = ConcurrentLinkedQueue<String>()

    fun createOffer(): VciSession {
        val now = clock.instant()
        val id = UUID.randomUUID().toString().substring(0, 8)
        val session = VciSession(
            id = id,
            credentialConfigurationIds = listOf(CredentialData.CONFIGURATION_ID),
            expiresAt = now.plus(sessionLifetime),
            offerExpiresAt = now.plus(offerLifetime),
        )
        sessions[id] = session
        insertionOrder.add(id)
        evict()

        return session
    }

    fun getOffer(id: String): VciSession? {
        val session = activeSession(id) ?: return null
        return if (clock.instant().isBefore(session.offerExpiresAt)) {
            session
        } else {
            null
        }
    }

    fun setAuthorizationParams(
        offerId: String,
        redirectUri: String,
        clientState: String?,
        codeChallenge: String,
        codeChallengeMethod: String,
        clientId: String,
        scope: String,
        dpopKeyThumbprint: String,
    ): Boolean {
        var updated = false
        sessions.computeIfPresent(offerId) { _, session ->
            if (isExpired(session.expiresAt)) return@computeIfPresent null
            if (isExpired(session.offerExpiresAt)) return@computeIfPresent session

            updated = true
            session.copy(
                redirectUri = redirectUri,
                clientState = clientState,
                codeChallenge = codeChallenge,
                codeChallengeMethod = codeChallengeMethod,
                clientId = clientId,
                scope = scope,
                dpopKeyThumbprint = dpopKeyThumbprint,
            )
        }

        return updated
    }

    fun authorize(offerId: String, selectedCredentialIndex: Int): VciSession? {
        return sessions.computeIfPresent(offerId) { _, session ->
            if (isExpired(session.expiresAt)) return@computeIfPresent null

            val code = UUID.randomUUID().toString()
            val expiresAt = clock.instant().plus(authorizationCodeLifetime)
            authCodeIndex[code] = AuthorizationCode(offerId, expiresAt)
            session.copy(
                authorizationCode = code,
                authorizationCodeExpiresAt = expiresAt,
                selectedCredentialIndex = selectedCredentialIndex,
            )
        }
    }

    fun exchangeCode(code: String, codeVerifier: String, redirectUri: String): VciSession? {
        val authorizationCode = authCodeIndex.remove(code) ?: return null
        if (isExpired(authorizationCode.expiresAt)) return null

        var exchanged: VciSession? = null
        sessions.computeIfPresent(authorizationCode.sessionId) { _, session ->
            if (isExpired(session.expiresAt)) return@computeIfPresent null
            if (session.authorizationCode != code) return@computeIfPresent session
            if (session.redirectUri != redirectUri) return@computeIfPresent session
            if (!verifyPkce(codeVerifier, session.codeChallenge, session.codeChallengeMethod)) {
                return@computeIfPresent session
            }

            val token = UUID.randomUUID().toString()
            val expiresAt = clock.instant().plus(accessTokenLifetime)
            val updated = session.copy(
                accessToken = token,
                accessTokenExpiresAt = expiresAt,
            )

            tokenIndex[token] = AccessToken(session.id, expiresAt)
            exchanged = updated
            updated
        }

        return exchanged
    }

    fun findByAuthorizationCode(code: String): VciSession? {
        val authorizationCode = authCodeIndex[code] ?: return null
        if (isExpired(authorizationCode.expiresAt)) {
            authCodeIndex.remove(code, authorizationCode)
            return null
        }

        return activeSession(authorizationCode.sessionId)
    }

    fun findByAccessToken(token: String): VciSession? {
        val accessToken = tokenIndex[token] ?: return null
        if (isExpired(accessToken.expiresAt)) {
            tokenIndex.remove(token, accessToken)
            return null
        }

        return activeSession(accessToken.sessionId)
    }

    fun consumeAccessToken(token: String): VciSession? {
        val accessToken = tokenIndex.remove(token) ?: return null
        if (isExpired(accessToken.expiresAt)) return null

        return activeSession(accessToken.sessionId)
    }

    private fun verifyPkce(codeVerifier: String, codeChallenge: String?, codeChallengeMethod: String?): Boolean {
        if (codeChallenge == null || codeChallengeMethod != "S256") return false
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        val computed = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)

        return computed == codeChallenge
    }

    private fun evict() {
        while (sessions.size > maxSessions) {
            val oldest = insertionOrder.poll() ?: break
            removeSession(oldest)
        }
    }

    private fun activeSession(id: String): VciSession? {
        val session = sessions[id] ?: return null
        if (!isExpired(session.expiresAt)) return session

        removeSession(id)
        return null
    }

    private fun removeSession(id: String) {
        sessions.remove(id)
        authCodeIndex.entries.removeIf { it.value.sessionId == id }
        tokenIndex.entries.removeIf { it.value.sessionId == id }
    }

    private fun isExpired(expiration: java.time.Instant): Boolean = !clock.instant().isBefore(expiration)

    private data class AuthorizationCode(val sessionId: String, val expiresAt: java.time.Instant)

    private data class AccessToken(val sessionId: String, val expiresAt: java.time.Instant)
}
