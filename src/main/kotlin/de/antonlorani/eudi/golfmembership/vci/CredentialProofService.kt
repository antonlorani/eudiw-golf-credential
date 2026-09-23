package de.antonlorani.eudi.golfmembership.vci

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.SignedJWT
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

@Service
class CredentialProofService(
    private val configuration: VciConfiguration,
    private val nonceService: NonceService,
    private val clock: Clock,
) {
    private val proofLifetime = Duration.ofMinutes(5)
    private val futureClockSkew = Duration.ofSeconds(60)

    fun verify(serializedProof: String, tokenNonce: String?, expectedClientId: String?): JWK {
        val proof = parse(serializedProof, "The credential proof is not a valid signed JWT")
        validateHeader(proof)
        val holderKey = holderKey(proof)
        validateSignature(proof, holderKey)
        validateClaims(proof, tokenNonce, expectedClientId)
        return holderKey.toPublicJWK()
    }

    private fun validateHeader(proof: SignedJWT) {
        if (proof.header.type != JOSEObjectType("openid4vci-proof+jwt")) {
            invalid("The credential proof typ must be openid4vci-proof+jwt")
        }

        if (proof.header.algorithm != JWSAlgorithm.ES256) {
            invalid("The credential proof algorithm must be ES256")
        }

        if (proof.header.jwk?.isPrivate == true) {
            invalid("The credential proof must not contain private key material")
        }

        if (proof.header.jwk != null && (proof.header.keyID != null || proof.header.x509CertChain != null)) {
            invalid("The credential proof must identify its key in exactly one supported way")
        }
    }

    private fun holderKey(proof: SignedJWT): ECKey {
        proof.header.jwk?.let { key ->
            return try {
                key.toECKey()
            } catch (_: Exception) {
                invalid("The credential proof jwk must be an EC key")
            }
        }

        val serializedAttestation = proof.header.toJSONObject()["key_attestation"] as? String
            ?: invalid("The credential proof must contain a public jwk or key_attestation")
        val attestation = parse(serializedAttestation, "The key attestation is not a valid signed JWT")
        if (attestation.header.type != JOSEObjectType("key-attestation+jwt")) {
            invalid("The key attestation typ must be key-attestation+jwt")
        }

        if (attestation.header.algorithm != JWSAlgorithm.ES256) {
            invalid("The key attestation algorithm must be ES256")
        }

        val keys = try {
            attestation.jwtClaimsSet.getClaim("attested_keys") as? List<*>
        } catch (_: Exception) {
            null
        } ?: invalid("The key attestation must contain attested_keys")

        if (keys.isEmpty()) {
            invalid("The key attestation attested_keys must not be empty")
        }

        val index = proof.header.keyID?.toIntOrNull()
            ?: invalid("A credential proof using key_attestation must identify an attested key with kid")
        val keyMap = keys.getOrNull(index) as? Map<*, *>
            ?: invalid("The credential proof kid does not identify an attested key")

        @Suppress("UNCHECKED_CAST")
        val key = try {
            JWK.parse(keyMap as Map<String, Any>).toECKey()
        } catch (_: Exception) {
            invalid("The selected attested key must be an EC public key")
        }

        if (key.isPrivate) {
            invalid("The key attestation must not contain private key material")
        }

        return key
    }

    private fun validateSignature(proof: SignedJWT, key: ECKey) {
        val valid = try {
            proof.verify(ECDSAVerifier(key))
        } catch (_: Exception) {
            false
        }

        if (!valid) {
            invalid("The credential proof signature is invalid")
        }
    }

    private fun validateClaims(proof: SignedJWT, tokenNonce: String?, expectedClientId: String?) {
        val claims = try {
            proof.jwtClaimsSet
        } catch (_: Exception) {
            invalid("The credential proof claims are invalid")
        }

        if (claims.audience.size != 1 || claims.audience.single() != configuration.issuerUrl) {
            invalid("The credential proof aud must equal the Credential Issuer Identifier")
        }

        val issuedAt = claims.issueTime?.toInstant()
            ?: invalid("The credential proof iat claim is required")
        val now = clock.instant()

        if (issuedAt.isBefore(now.minus(proofLifetime)) || issuedAt.isAfter(now.plus(futureClockSkew))) {
            invalid("The credential proof iat claim is outside the accepted time window")
        }

        if (expectedClientId != null && claims.issuer != null && claims.issuer != expectedClientId) {
            invalid("The credential proof iss must equal the OAuth client_id when present")
        }

        val nonce = try {
            claims.getStringClaim("nonce")
        } catch (_: Exception) {
            invalid("The credential proof nonce claim must be a string")
        } ?: invalid("The credential proof nonce claim is required")

        val validNonce = nonce == tokenNonce || nonceService.consume(nonce)

        if (!validNonce) {
            throw CredentialProofException(
                error = "invalid_nonce",
                description = "The credential proof nonce is invalid, expired, or already used",
            )
        }
    }

    private fun parse(value: String, description: String): SignedJWT = try {
        SignedJWT.parse(value)
    } catch (_: Exception) {
        invalid(description)
    }

    private fun invalid(description: String): Nothing = throw CredentialProofException(description = description)
}
