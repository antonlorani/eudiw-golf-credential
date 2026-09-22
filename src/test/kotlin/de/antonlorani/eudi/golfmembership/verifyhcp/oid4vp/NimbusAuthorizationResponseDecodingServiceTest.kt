package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDHEncrypter
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class NimbusAuthorizationResponseDecodingServiceTest {
    private val key = ECKeyGenerator(Curve.P_256)
        .keyUse(KeyUse.ENCRYPTION)
        .algorithm(JWEAlgorithm.ECDH_ES)
        .keyID("response-key")
        .generate()
    private val service = NimbusAuthorizationResponseDecodingService(
        VerifierResponseEncryptionKey(key),
        jacksonObjectMapper(),
    )

    @Test
    fun `decrypts the wallet direct post jwt payload`() {
        val response = encrypt(
            """{"state":"expected-state","vp_token":{"golf_membership":["issuer~disclosure~kb"]}}"""
        )

        val result = service.decode(response)
        val decoded = assertInstanceOf(AuthorizationResponseDecodingResult.Decoded::class.java, result).response

        assertEquals("expected-state", decoded.state)
        assertEquals("issuer~disclosure~kb", decoded.vpToken)
    }

    @Test
    fun `rejects an unknown encryption key id`() {
        val response = encrypt(
            payload = """{"state":"state","vp_token":{"golf_membership":["token"]}}""",
            keyId = "another-key",
        )

        val result = service.decode(response)

        assertEquals(
            AuthorizationResponseDecodingResult.Invalid(
                AuthorizationResponseDecodingError.UNKNOWN_ENCRYPTION_KEY,
            ),
            result,
        )
    }

    @Test
    fun `rejects tampered ciphertext`() {
        val response = encrypt(
            """{"state":"state","vp_token":{"golf_membership":["token"]}}"""
        )
        val parts = response.split('.').toMutableList()
        parts[3] = parts[3].replaceRange(0, 1, if (parts[3][0] == 'A') "B" else "A")

        val result = service.decode(parts.joinToString("."))

        assertEquals(
            AuthorizationResponseDecodingResult.Invalid(
                AuthorizationResponseDecodingError.DECRYPTION_FAILED,
            ),
            result,
        )
    }

    private fun encrypt(payload: String, keyId: String = key.keyID): String {
        val jwe = JWEObject(
            JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM)
                .keyID(keyId)
                .build(),
            Payload(payload),
        )
        jwe.encrypt(ECDHEncrypter(key.toPublicJWK()))
        return jwe.serialize()
    }
}
