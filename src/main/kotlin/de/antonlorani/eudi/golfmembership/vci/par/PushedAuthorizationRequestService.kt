package de.antonlorani.eudi.golfmembership.vci.par

import de.antonlorani.eudi.golfmembership.vci.CredentialData
import de.antonlorani.eudi.golfmembership.vci.VciIssuanceService
import org.springframework.stereotype.Service
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class PushedAuthorizationRequestService(
    private val issuanceService: VciIssuanceService,
    private val clock: Clock,
) {
    private val lifetime = Duration.ofSeconds(60)
    private val requests = ConcurrentHashMap<String, StoredPushedAuthorizationRequest>()

    fun push(request: PushedAuthorizationRequest): String {
        val responseType = required(request.responseType, "response_type")
        val clientId = required(request.clientId, "client_id")
        val redirectUri = required(request.redirectUri, "redirect_uri")
        val codeChallenge = required(request.codeChallenge, "code_challenge")
        val codeChallengeMethod = required(request.codeChallengeMethod, "code_challenge_method")
        val scope = required(request.scope, "scope")
        val issuerState = required(request.issuerState, "issuer_state")
        validate(responseType, clientId, redirectUri, codeChallenge, codeChallengeMethod, scope)

        val offerExists = issuanceService.setAuthorizationParams(
            offerId = issuerState,
            redirectUri = redirectUri,
            clientState = request.state,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            clientId = clientId,
            scope = scope,
        )
        if (!offerExists) {
            throw PushedAuthorizationRequestException("invalid_request", "Unknown issuer_state")
        }

        val requestUri = "urn:ietf:params:oauth:request_uri:${UUID.randomUUID()}"
        requests[requestUri] = StoredPushedAuthorizationRequest(
            offerId = issuerState,
            clientId = clientId,
            expiresAt = clock.instant().plus(lifetime),
        )
        removeExpiredRequests()
        return requestUri
    }

    fun consume(requestUri: String, clientId: String): String? {
        var offerId: String? = null
        requests.computeIfPresent(requestUri) { _, request ->
            if (!clock.instant().isBefore(request.expiresAt)) {
                return@computeIfPresent null
            }
            if (request.clientId != clientId) {
                return@computeIfPresent request
            }
            offerId = request.offerId
            null
        }
        return offerId
    }

    fun expiresInSeconds(): Int = lifetime.seconds.toInt()

    private fun validate(
        responseType: String,
        clientId: String,
        redirectUri: String,
        codeChallenge: String,
        codeChallengeMethod: String,
        scope: String,
    ) {
        if (responseType != "code") {
            throw PushedAuthorizationRequestException("unsupported_response_type", "response_type must be code")
        }
        if (clientId.isBlank()) {
            throw PushedAuthorizationRequestException("invalid_request", "client_id must not be blank")
        }
        if (!isValidRedirectUri(redirectUri)) {
            throw PushedAuthorizationRequestException("invalid_request", "redirect_uri must be an absolute URI without a fragment")
        }
        if (codeChallenge.isBlank()) {
            throw PushedAuthorizationRequestException("invalid_request", "code_challenge must not be blank")
        }
        if (codeChallengeMethod != "S256") {
            throw PushedAuthorizationRequestException("invalid_request", "code_challenge_method must be S256")
        }
        if (CredentialData.SCOPE !in scope.split(' ').filter { it.isNotBlank() }) {
            throw PushedAuthorizationRequestException("invalid_scope", "scope does not identify a supported credential")
        }
    }

    private fun required(value: String?, parameter: String): String {
        if (value.isNullOrBlank()) {
            throw PushedAuthorizationRequestException("invalid_request", "$parameter is required")
        }
        return value
    }

    private fun isValidRedirectUri(value: String): Boolean {
        return try {
            val uri = URI(value)
            uri.isAbsolute && uri.fragment == null
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun removeExpiredRequests() {
        val now = clock.instant()
        requests.entries.removeIf { !now.isBefore(it.value.expiresAt) }
    }
}
