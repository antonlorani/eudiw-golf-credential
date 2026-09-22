package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.crypto.ECDHDecrypter
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class NimbusAuthorizationResponseDecodingService(
    private val encryptionKey: VerifierResponseEncryptionKey,
    private val objectMapper: ObjectMapper,
) : AuthorizationResponseDecodingService {
    override fun decode(response: String): AuthorizationResponseDecodingResult {
        val jwe = try {
            JWEObject.parse(response)
        } catch (_: Exception) {
            return AuthorizationResponseDecodingResult.Invalid(
                AuthorizationResponseDecodingError.MALFORMED_RESPONSE,
            )
        }

        if (jwe.header.algorithm != JWEAlgorithm.ECDH_ES ||
            jwe.header.encryptionMethod != EncryptionMethod.A128GCM
        ) {
            return AuthorizationResponseDecodingResult.Invalid(
                AuthorizationResponseDecodingError.UNSUPPORTED_ENCRYPTION,
            )
        }
        if (jwe.header.keyID != encryptionKey.value.keyID) {
            return AuthorizationResponseDecodingResult.Invalid(
                AuthorizationResponseDecodingError.UNKNOWN_ENCRYPTION_KEY,
            )
        }

        try {
            jwe.decrypt(ECDHDecrypter(encryptionKey.value))
        } catch (_: Exception) {
            return AuthorizationResponseDecodingResult.Invalid(
                AuthorizationResponseDecodingError.DECRYPTION_FAILED,
            )
        }

        val payload = try {
            objectMapper.readValue(jwe.payload.toString(), DirectPostPayload::class.java)
        } catch (_: Exception) {
            return AuthorizationResponseDecodingResult.Invalid(
                AuthorizationResponseDecodingError.INVALID_PAYLOAD,
            )
        }
        val state = payload.state
            ?: return AuthorizationResponseDecodingResult.Invalid(
                AuthorizationResponseDecodingError.INVALID_PAYLOAD,
            )
        val tokens = payload.vpToken?.get(AuthorizationRequestService.CREDENTIAL_QUERY_ID)
        if (tokens != null && tokens.size != 1) {
            return AuthorizationResponseDecodingResult.Invalid(
                AuthorizationResponseDecodingError.INVALID_PAYLOAD,
            )
        }
        val vpToken = tokens?.single()

        return AuthorizationResponseDecodingResult.Decoded(
            DecryptedAuthorizationResponse(state, vpToken),
        )
    }

    private data class DirectPostPayload(
        val state: String?,
        @param:JsonProperty("vp_token")
        val vpToken: Map<String, List<String>>?,
    )
}
