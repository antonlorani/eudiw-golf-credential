package de.antonlorani.eudi.golfmembership.vci

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date

class CredentialProofServiceTest {
    private val now = Instant.parse("2026-09-24T12:00:00Z")
    private val issuerUrl = "https://issuer.example"
    private val clientId = "wallet-client"
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val nonceService = NonceService(clock)
    private val service = CredentialProofService(VciConfiguration(issuerUrl), nonceService, clock)
    private val holderKey = ECKeyGenerator(Curve.P_256).keyID("holder").generate()

    @Test
    fun `accepts a valid ES256 proof and returns its public key`() {
        val nonce = nonceService.issue()

        val result = service.verify(proof(nonce = nonce), null, clientId)

        assertEquals(holderKey.toPublicJWK(), result)
    }

    @Test
    fun `accepts the nonce issued in the token response`() {
        val nonce = "token-response-nonce"

        val result = service.verify(proof(nonce = nonce), nonce, clientId)

        assertEquals(holderKey.toPublicJWK(), result)
    }

    @Test
    fun `rejects an invalid signature`() {
        val otherKey = ECKeyGenerator(Curve.P_256).generate()
        val serialized = proof(nonce = nonceService.issue(), signingKey = otherKey, headerKey = holderKey)

        assertInvalid(serialized, "signature")
    }

    @Test
    fun `rejects a replayed nonce`() {
        val nonce = nonceService.issue()
        service.verify(proof(nonce = nonce), null, clientId)

        assertInvalid(proof(nonce = nonce), "nonce")
    }

    @Test
    fun `rejects the wrong audience`() {
        val serialized = proof(nonce = nonceService.issue(), audience = "https://other.example")

        assertInvalid(serialized, "aud")
    }

    @Test
    fun `rejects a stale proof`() {
        val serialized = proof(nonce = nonceService.issue(), issuedAt = now.minusSeconds(301))

        assertInvalid(serialized, "iat")
    }

    @Test
    fun `rejects the wrong explicit type`() {
        val serialized = proof(nonce = nonceService.issue(), type = JOSEObjectType.JWT)

        assertInvalid(serialized, "typ")
    }

    @Test
    fun `rejects an issuer that differs from the client id`() {
        val serialized = proof(nonce = nonceService.issue(), issuer = "another-client")

        assertInvalid(serialized, "iss")
    }

    private fun proof(
        nonce: String,
        audience: String = issuerUrl,
        issuer: String = clientId,
        issuedAt: Instant = now,
        type: JOSEObjectType = JOSEObjectType("openid4vci-proof+jwt"),
        signingKey: ECKey = holderKey,
        headerKey: ECKey = holderKey,
    ): String {
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(type)
            .jwk(headerKey.toPublicJWK())
            .build()
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .issueTime(Date.from(issuedAt))
            .claim("nonce", nonce)
            .build()
        return SignedJWT(header, claims).apply {
            sign(ECDSASigner(signingKey))
        }.serialize()
    }

    private fun assertInvalid(serialized: String, expectedDescriptionPart: String) {
        val error = assertThrows(CredentialProofException::class.java) {
            service.verify(serialized, null, clientId)
        }
        check(error.description.contains(expectedDescriptionPart)) {
            "Expected '${error.description}' to contain '$expectedDescriptionPart'"
        }
    }
}
