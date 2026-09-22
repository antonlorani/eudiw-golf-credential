package de.antonlorani.eudi.golfmembership.vci

import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Service
class VciIssuanceService {

    private val maxSessions = 100
    private val sessions = ConcurrentHashMap<String, VciSession>()
    private val authCodeIndex = ConcurrentHashMap<String, String>()
    private val tokenIndex = ConcurrentHashMap<String, String>()
    private val requestUriIndex = ConcurrentHashMap<String, String>()
    private val insertionOrder = ConcurrentLinkedQueue<String>()

    fun createOffer(): VciSession {
        val id = UUID.randomUUID().toString().substring(0, 8)
        val session = VciSession(
            id = id,
            credentialConfigurationIds = listOf(CredentialData.CONFIGURATION_ID),
        )
        sessions[id] = session
        insertionOrder.add(id)
        evict()
        return session
    }

    fun getOffer(id: String): VciSession? = sessions[id]

    fun setAuthorizationParams(
        offerId: String,
        redirectUri: String,
        clientState: String?,
        codeChallenge: String,
        codeChallengeMethod: String,
        clientId: String,
        scope: String,
    ) {
        sessions.computeIfPresent(offerId) { _, session ->
            session.copy(
                redirectUri = redirectUri,
                clientState = clientState,
                codeChallenge = codeChallenge,
                codeChallengeMethod = codeChallengeMethod,
                clientId = clientId,
                scope = scope,
            )
        }
    }

    fun pushAuthorizationRequest(
        offerId: String,
        redirectUri: String,
        clientState: String?,
        codeChallenge: String,
        codeChallengeMethod: String,
        clientId: String,
        scope: String,
    ): String {
        setAuthorizationParams(offerId, redirectUri, clientState, codeChallenge, codeChallengeMethod, clientId, scope)
        val requestUri = "urn:ietf:params:oauth:request_uri:${UUID.randomUUID()}"
        requestUriIndex[requestUri] = offerId
        return requestUri
    }

    fun resolveRequestUri(requestUri: String): String? {
        return requestUriIndex.remove(requestUri)
    }

    fun authorize(offerId: String, selectedCredentialIndex: Int): VciSession? {
        return sessions.computeIfPresent(offerId) { _, session ->
            val code = UUID.randomUUID().toString()
            authCodeIndex[code] = offerId
            session.copy(
                authorizationCode = code,
                selectedCredentialIndex = selectedCredentialIndex,
            )
        }
    }

    fun exchangeCode(code: String, codeVerifier: String): VciSession? {
        val offerId = authCodeIndex.remove(code) ?: return null
        return sessions.computeIfPresent(offerId) { _, session ->
            if (session.authorizationCode != code) return@computeIfPresent session
            if (!verifyPkce(codeVerifier, session.codeChallenge, session.codeChallengeMethod)) return@computeIfPresent session
            val token = UUID.randomUUID().toString()
            val nonce = UUID.randomUUID().toString()
            tokenIndex[token] = offerId
            session.copy(accessToken = token, cNonce = nonce)
        }
    }

    fun consumeAccessToken(token: String): VciSession? {
        val offerId = tokenIndex.remove(token) ?: return null
        return sessions[offerId]
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
            sessions.remove(oldest)
        }
    }
}
