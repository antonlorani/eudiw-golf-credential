package de.antonlorani.eudi.golfmembership.vci

import com.nimbusds.jose.jwk.ECKey
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class VciMetadataController(
    private val vciConfiguration: VciConfiguration,
    private val metadataSigningService: MetadataSigningService,
    private val issuerSigningKey: ECKey,
    private val credentialIssuerMetadata: CredentialIssuerMetadata,
) {

    @GetMapping("/.well-known/openid-credential-issuer")
    fun credentialIssuerMetadata(request: HttpServletRequest): ResponseEntity<*> {
        if (request.getHeader("Accept")?.contains("application/jwt") == true) {
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/jwt"))
                .body(metadataSigningService.sign(credentialIssuerMetadata))
        }
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(credentialIssuerMetadata)
    }

    @GetMapping("/.well-known/jwt-vc-issuer")
    fun jwtVcIssuerMetadata(): JwtVcIssuerMetadata {
        return JwtVcIssuerMetadata(
            issuer = vciConfiguration.issuerUrl,
            jwks = JwtVcIssuerMetadata.Jwks(
                keys = listOf(issuerSigningKey.toPublicJWK().toJSONObject()),
            ),
        )
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    fun authorizationServerMetadata(): AuthorizationServerMetadata {
        val issuerUrl = vciConfiguration.issuerUrl
        return AuthorizationServerMetadata(
            issuer = issuerUrl,
            authorizationEndpoint = "$issuerUrl/authorize",
            tokenEndpoint = "$issuerUrl/token",
            responseTypesSupported = listOf("code"),
            grantTypesSupported = listOf("authorization_code"),
            codeChallengeMethodsSupported = listOf("S256"),
        )
    }
}
