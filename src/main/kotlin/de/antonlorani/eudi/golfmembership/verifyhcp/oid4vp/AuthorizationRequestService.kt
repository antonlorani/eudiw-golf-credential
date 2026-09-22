package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.util.Base64
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import de.antonlorani.eudi.golfmembership.vci.CredentialData
import de.antonlorani.eudi.golfmembership.demo.BookingSelection
import de.antonlorani.eudi.golfmembership.demo.DemoState
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.net.URLEncoder
import java.util.Date

@Component
class AuthorizationRequestService(
    private val configuration: Oid4vpConfiguration,
    private val signingMaterialService: VerifierSigningMaterialService,
    private val responseEncryptionKey: VerifierResponseEncryptionKey,
    private val objectMapper: ObjectMapper,
) {
    fun create(sessionId: String, pending: DemoState.PendingVerification): String {
        val signingMaterial = signingMaterialService.verifierSigningMaterial()
        val requestedClaim = when (val selection = pending.selection) {
            is BookingSelection.GolfCourse -> {
                if (selection.maximumHcp <= 36.0) "is_hcp_below_37" else null
            }
            is BookingSelection.Tournament -> "hcp_index"
        }
        val responseUri = "${configuration.verifierUrl}/oid4vp/responses/$sessionId"
        val requestedClaims = if (requestedClaim == null) {
            null
        } else {
            listOf(ClaimQuery(path = listOf(requestedClaim)))
        }
        val dcql = DcqlQuery(
            credentials = listOf(
                CredentialQuery(
                    id = CREDENTIAL_QUERY_ID,
                    format = "dc+sd-jwt",
                    meta = CredentialMetadata(vctValues = listOf(CredentialData.VCT)),
                    claims = requestedClaims,
                ),
            ),
        )
        val publicJwk = objectMapper.valueToTree<JsonNode>(
            responseEncryptionKey.value.toPublicJWK().toJSONObject(),
        )
        val clientMetadata = ClientMetadata(
            jwks = JsonWebKeySet(keys = listOf(publicJwk)),
            encryptedResponseEncValuesSupported = listOf("A128GCM"),
            vpFormatsSupported = mapOf(
                "dc+sd-jwt" to SupportedSdJwtFormat(
                    sdJwtAlgorithms = listOf("ES256"),
                    keyBindingJwtAlgorithms = listOf("ES256"),
                ),
            ),
        )
        val claims = JWTClaimsSet.Builder()
            .issuer(configuration.clientId)
            .audience(SELF_ISSUED_AUDIENCE)
            .issueTime(Date())
            .expirationTime(Date.from(pending.expiresAt))
            .jwtID(sessionId)
            .claim("client_id", configuration.clientId)
            .claim("response_type", "vp_token")
            .claim("response_mode", "direct_post.jwt")
            .claim("response_uri", responseUri)
            .claim("nonce", pending.nonce)
            .claim("state", pending.responseState)
            .claim("dcql_query", toJsonMap(dcql))
            .claim("client_metadata", toJsonMap(clientMetadata))
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType("oauth-authz-req+jwt"))
            .x509CertChain(listOf(Base64.encode(signingMaterial.certificate.encoded)))
            .build()
        val requestObject = SignedJWT(header, claims).apply {
            sign(ECDSASigner(signingMaterial.signingKey))
        }.serialize()
        return "openid4vp://authorize?client_id=${encode(configuration.clientId)}&request=${encode(requestObject)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private fun toJsonMap(value: Any): Map<String, Any> {
        return objectMapper.convertValue(value, object : TypeReference<Map<String, Any>>() {})
    }

    private data class DcqlQuery(val credentials: List<CredentialQuery>)

    private data class CredentialQuery(
        val id: String,
        val format: String,
        val meta: CredentialMetadata,
        val claims: List<ClaimQuery>?,
    )

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    private data class CredentialMetadata(val vctValues: List<String>)

    private data class ClaimQuery(val path: List<String>)

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    private data class ClientMetadata(
        val jwks: JsonWebKeySet,
        val encryptedResponseEncValuesSupported: List<String>,
        val vpFormatsSupported: Map<String, SupportedSdJwtFormat>,
    )

    private data class JsonWebKeySet(val keys: List<JsonNode>)

    private data class SupportedSdJwtFormat(
        @param:com.fasterxml.jackson.annotation.JsonProperty("sd-jwt_alg_values")
        val sdJwtAlgorithms: List<String>,
        @param:com.fasterxml.jackson.annotation.JsonProperty("kb-jwt_alg_values")
        val keyBindingJwtAlgorithms: List<String>,
    )

    companion object {
        const val CREDENTIAL_QUERY_ID = "golf_membership"
        const val SELF_ISSUED_AUDIENCE = "https://self-issued.me/v2"
    }
}
