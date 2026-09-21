package de.antonlorani.eudi.golfmembership.vci

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.SignedJWT
import org.springframework.http.ResponseEntity
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

    @PostMapping("/token")
    @ResponseBody
    fun token(
        @RequestParam("grant_type") grantType: String,
        @RequestParam("code") code: String,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("code_verifier") codeVerifier: String,
    ): ResponseEntity<Any> {
        if (grantType != "authorization_code") {
            return ResponseEntity.badRequest().body(VciErrorResponse("unsupported_grant_type"))
        }
        val session = vciIssuanceService.exchangeCode(code, codeVerifier)
            ?: return ResponseEntity.badRequest().body(VciErrorResponse("invalid_grant"))
        return ResponseEntity.ok(
            TokenResponse(
                accessToken = session.accessToken!!,
                tokenType = "bearer",
                expiresIn = 300,
                cNonce = session.cNonce!!,
                cNonceExpiresIn = 300,
            )
        )
    }

    @PostMapping("/credential")
    @ResponseBody
    suspend fun credential(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: CredentialRequest,
    ): ResponseEntity<Any> {
        val token = authorization.removePrefix("Bearer ").trim()
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
}
