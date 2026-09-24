package de.antonlorani.eudi.golfmembership.vci

import de.antonlorani.eudi.golfmembership.vci.par.PushedAuthorizationRequest
import de.antonlorani.eudi.golfmembership.vci.par.PushedAuthorizationRequestException
import de.antonlorani.eudi.golfmembership.vci.par.PushedAuthorizationRequestService
import de.antonlorani.eudi.golfmembership.vci.dpop.DpopProofException
import de.antonlorani.eudi.golfmembership.vci.dpop.DpopProofService
import org.springframework.http.ResponseEntity
import org.springframework.http.CacheControl
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.net.URLEncoder

@Controller
class VciController(
    private val vciIssuanceService: VciIssuanceService,
    private val credentialSigningService: CredentialSigningService,
    private val vciConfiguration: VciConfiguration,
    private val pushedAuthorizationRequestService: PushedAuthorizationRequestService,
    private val dpopProofService: DpopProofService,
    private val credentialProofService: CredentialProofService,
    private val nonceService: NonceService,
) {

    @PostMapping("/credential-offers")
    fun createOffer(): String {
        val session = vciIssuanceService.createOffer()
        val offerUri = "${vciConfiguration.issuerUrl}/credential-offers/${session.id}"
        val encoded = URLEncoder.encode(offerUri, Charsets.UTF_8)
        return "redirect:openid-credential-offer://?credential_offer_uri=$encoded"
    }

    @GetMapping("/credential-offers/{id}")
    @ResponseBody
    fun getOffer(@PathVariable id: String): ResponseEntity<CredentialOfferResponse> {
        val session = vciIssuanceService.getOffer(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            CredentialOfferResponse(
                credentialIssuer = vciConfiguration.issuerUrl,
                credentialConfigurationIds = session.credentialConfigurationIds,
                grants = CredentialOfferResponse.Grants(
                    authorizationCode = CredentialOfferResponse.AuthorizationCodeGrant(
                        issuerState = session.id,
                    ),
                ),
            )
        )
    }

    @PostMapping("/par")
    @ResponseBody
    fun pushedAuthorizationRequest(
        @RequestParam("response_type", required = false) responseType: String?,
        @RequestParam("client_id", required = false) clientId: String?,
        @RequestParam("redirect_uri", required = false) redirectUri: String?,
        @RequestParam("state", required = false) state: String?,
        @RequestParam("code_challenge", required = false) codeChallenge: String?,
        @RequestParam("code_challenge_method", required = false) codeChallengeMethod: String?,
        @RequestParam("scope", required = false) scope: String?,
        @RequestParam("issuer_state", required = false) issuerState: String?,
        @RequestParam("dpop_jkt", required = false) dpopKeyThumbprint: String?,
        @RequestHeader("DPoP", required = false) dpopProof: String?,
    ): ResponseEntity<Any> {
        val requestUri = try {
            pushedAuthorizationRequestService.push(
                PushedAuthorizationRequest(
                    responseType = responseType,
                    clientId = clientId,
                    redirectUri = redirectUri,
                    state = state,
                    codeChallenge = codeChallenge,
                    codeChallengeMethod = codeChallengeMethod,
                    scope = scope,
                    issuerState = issuerState,
                    dpopKeyThumbprint = dpopKeyThumbprint,
                    dpopProof = dpopProof,
                )
            )
        } catch (error: PushedAuthorizationRequestException) {
            return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(VciErrorResponse(error.error, error.description))
        } catch (error: DpopProofException) {
            return invalidDpopProof(error)
        }

        return ResponseEntity.status(201)
            .cacheControl(CacheControl.noStore())
            .body(
                PushedAuthorizationResponse(
                    requestUri = requestUri,
                    expiresIn = pushedAuthorizationRequestService.expiresInSeconds(),
                )
            )
    }

    @PostMapping("/token")
    @ResponseBody
    fun token(
        @RequestParam("grant_type") grantType: String,
        @RequestParam("code") code: String,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("code_verifier") codeVerifier: String,
        @RequestHeader("DPoP", required = false) dpopProof: String?,
    ): ResponseEntity<Any> {
        if (grantType != "authorization_code") {
            return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(VciErrorResponse("unsupported_grant_type"))
        }

        val pendingSession = vciIssuanceService.findByAuthorizationCode(code)
            ?: return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(VciErrorResponse("invalid_grant"))

        val boundKeyThumbprint = pendingSession.dpopKeyThumbprint
            ?: return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(VciErrorResponse("invalid_grant"))

        try {
            dpopProofService.verify(
                serializedProof = dpopProof,
                httpMethod = "POST",
                targetUri = "${vciConfiguration.issuerUrl}/token",
                expectedKeyThumbprint = boundKeyThumbprint,
            )
        } catch (error: DpopProofException) {
            return invalidDpopProof(error)
        }

        val session = vciIssuanceService.exchangeCode(code, codeVerifier, redirectUri)
            ?: return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(VciErrorResponse("invalid_grant"))

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(TokenResponse(
                accessToken = session.accessToken!!,
                tokenType = "DPoP",
                expiresIn = 300,
            ))
    }

    @PostMapping("/credential")
    @ResponseBody
    suspend fun credential(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("DPoP", required = false) dpopProof: String?,
        @RequestBody request: CredentialRequest,
    ): ResponseEntity<Any> {
        val token = extractDpopAccessToken(authorization)
            ?: return credentialError(401, "invalid_token")

        val pendingSession = vciIssuanceService.findByAccessToken(token)
            ?: return credentialError(401, "invalid_token")

        val boundKeyThumbprint = pendingSession.dpopKeyThumbprint
            ?: return credentialError(401, "invalid_token")

        try {
            dpopProofService.verify(
                serializedProof = dpopProof,
                httpMethod = "POST",
                targetUri = "${vciConfiguration.issuerUrl}/credential",
                accessToken = token,
                expectedKeyThumbprint = boundKeyThumbprint,
            )
        } catch (error: DpopProofException) {
            return invalidResourceDpopProof(error)
        }

        if (request.credentialConfigurationId != CredentialData.CONFIGURATION_ID ||
            request.credentialIdentifier != null
        ) {
            return credentialError(400, "unknown_credential_configuration")
        }

        val proofJwt = request.extractSingleProofJwt()
            ?: return credentialError(400, "invalid_proof")

        val holderKey = try {
            credentialProofService.verify(
                serializedProof = proofJwt,
                expectedClientId = pendingSession.clientId,
            )
        } catch (error: CredentialProofException) {
            return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(VciErrorResponse(error.error, error.description))
        }

        val session = vciIssuanceService.consumeAccessToken(token)
            ?: return credentialError(401, "invalid_token")

        val credentialData = CredentialData.ALL[session.selectedCredentialIndex!!]
        val sdJwtVc = credentialSigningService.sign(credentialData, holderKey)

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(BatchCredentialResponse(listOf(CredentialResponse(sdJwtVc))))
    }

    @PostMapping("/nonce")
    @ResponseBody
    fun nonce(): ResponseEntity<NonceResponse> {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(NonceResponse(nonceService.issue()))
    }

    private fun extractDpopAccessToken(authorization: String): String? {
        if (!authorization.startsWith("DPoP ", ignoreCase = true)) {
            return null
        }

        val token = authorization.substringAfter(' ').trim()
        return if (token.isEmpty()) null else token
    }

    private fun invalidDpopProof(error: DpopProofException): ResponseEntity<Any> {
        return ResponseEntity.badRequest()
            .cacheControl(CacheControl.noStore())
            .body(VciErrorResponse("invalid_dpop_proof", error.description))
    }

    private fun invalidResourceDpopProof(error: DpopProofException): ResponseEntity<Any> {
        return ResponseEntity.status(401)
            .header("WWW-Authenticate", "DPoP error=\"invalid_dpop_proof\", algs=\"ES256\"")
            .cacheControl(CacheControl.noStore())
            .body(VciErrorResponse("invalid_dpop_proof", error.description))
    }

    private fun credentialError(status: Int, error: String): ResponseEntity<Any> {
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore())
            .body(VciErrorResponse(error))
    }
}
