package de.antonlorani.eudi.golfmembership.vci

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.SignedJWT
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
                cNonce = session.cNonce!!,
                cNonceExpiresIn = 300,
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
            ?: return ResponseEntity.status(401).body(VciErrorResponse("invalid_token"))

        val pendingSession = vciIssuanceService.findByAccessToken(token)
            ?: return ResponseEntity.status(401).body(VciErrorResponse("invalid_token"))

        val boundKeyThumbprint = pendingSession.dpopKeyThumbprint
            ?: return ResponseEntity.status(401).body(VciErrorResponse("invalid_token"))

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

        val session = vciIssuanceService.consumeAccessToken(token)
            ?: return ResponseEntity.status(401).body(VciErrorResponse("invalid_token"))

        val proofJwt = request.extractProofJwt()
            ?: return ResponseEntity.badRequest().body(VciErrorResponse("invalid_proof"))

        val signedJwt = SignedJWT.parse(proofJwt)
        val holderKey = extractHolderKey(signedJwt)
            ?: return ResponseEntity.badRequest().body(VciErrorResponse("invalid_proof"))

        val credentialData = CredentialData.ALL[session.selectedCredentialIndex!!]
        val sdJwtVc = credentialSigningService.sign(credentialData, holderKey)

        return if (request.isBatch()) {
            ResponseEntity.ok(BatchCredentialResponse(listOf(CredentialResponse(sdJwtVc))))
        } else {
            ResponseEntity.ok(CredentialResponse(sdJwtVc))
        }
    }

    private fun extractHolderKey(signedJwt: SignedJWT): JWK? {
        signedJwt.header.jwk?.let { return it }

        val keyAttestationString = signedJwt.header.toJSONObject()["key_attestation"] as? String
            ?: return null
        val keyAttestation = SignedJWT.parse(keyAttestationString)

        val attestedKeys = keyAttestation.jwtClaimsSet.getClaim("attested_keys") as? List<*>
            ?: return null
        val kidIndex = (signedJwt.header.keyID ?: "0").toIntOrNull() ?: 0
        val keyMap = attestedKeys.getOrNull(kidIndex) as? Map<*, *> ?: return null

        @Suppress("UNCHECKED_CAST")
        return JWK.parse(keyMap as Map<String, Any>)
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
}
