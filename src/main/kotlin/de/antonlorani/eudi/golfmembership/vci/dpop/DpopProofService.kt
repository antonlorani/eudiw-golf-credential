package de.antonlorani.eudi.golfmembership.vci.dpop

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.SignedJWT
import org.springframework.stereotype.Service
import java.net.URI
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Service
class DpopProofService(
    private val clock: Clock,
) {
    private val proofLifetime = Duration.ofMinutes(5)
    private val futureClockSkew = Duration.ofSeconds(60)
    private val replayExpirations = ConcurrentHashMap<String, Instant>()

    fun verify(
        serializedProof: String?,
        httpMethod: String,
        targetUri: String,
        accessToken: String? = null,
        expectedKeyThumbprint: String? = null,
    ): DpopProof {
        val proof = parse(serializedProof)
        val publicKey = publicKey(proof)
        validateSignature(proof, publicKey)

        val claims = claims(proof)
        validateRequestBinding(claims, httpMethod, targetUri)
        validateAccessTokenBinding(claims.accessTokenHash, accessToken)

        val thumbprint = keyThumbprint(publicKey)
        validateKeyBinding(thumbprint, expectedKeyThumbprint)
        preventReplay(thumbprint, claims.id)

        return DpopProof(thumbprint)
    }

    private fun parse(serializedProof: String?): SignedJWT {
        if (serializedProof.isNullOrBlank()) {
            throw DpopProofException("The DPoP HTTP header is required")
        }

        return try {
            SignedJWT.parse(serializedProof)
        } catch (_: Exception) {
            throw DpopProofException("The DPoP proof is not a valid signed JWT")
        }
    }

    private fun publicKey(proof: SignedJWT): JWK {
        val publicKey = proof.header.jwk
            ?: throw DpopProofException("The DPoP proof must contain a public jwk header")

        if (publicKey.isPrivate) {
            throw DpopProofException("The DPoP proof must not contain private key material")
        }

        if (proof.header.type != JOSEObjectType("dpop+jwt")) {
            throw DpopProofException("The DPoP proof typ must be dpop+jwt")
        }

        if (proof.header.algorithm != JWSAlgorithm.ES256) {
            throw DpopProofException("The DPoP proof algorithm must be ES256")
        }

        return publicKey
    }

    private fun validateSignature(proof: SignedJWT, publicKey: JWK) {
        val signatureValid = try {
            proof.verify(ECDSAVerifier(publicKey.toECKey()))
        } catch (_: Exception) {
            false
        }
        if (!signatureValid) {
            throw DpopProofException("The DPoP proof signature is invalid")
        }
    }

    private fun claims(proof: SignedJWT): DpopProofClaims {
        val claims = try {
            proof.jwtClaimsSet
        } catch (_: Exception) {
            throw DpopProofException("The DPoP proof claims are invalid")
        }

        try {
            return DpopProofClaims(
                id = claims.jwtid
                    ?: throw DpopProofException("The DPoP proof jti claim is required"),
                issuedAt = claims.issueTime?.toInstant()
                    ?: throw DpopProofException("The DPoP proof iat claim is required"),
                httpMethod = claims.getStringClaim("htm")
                    ?: throw DpopProofException("The DPoP proof htm claim is required"),
                targetUri = claims.getStringClaim("htu")
                    ?: throw DpopProofException("The DPoP proof htu claim is required"),
                accessTokenHash = optionalStringClaim(claims.getClaim("ath"), "ath"),
            )
        } catch (error: DpopProofException) {
            throw error
        } catch (_: Exception) {
            throw DpopProofException("The DPoP proof claims have invalid types")
        }
    }

    private fun validateRequestBinding(claims: DpopProofClaims, httpMethod: String, targetUri: String) {
        if (claims.id.isBlank()) {
            throw DpopProofException("The DPoP proof jti claim must not be blank")
        }

        validateTime(claims.issuedAt)

        if (claims.httpMethod != httpMethod) {
            throw DpopProofException("The DPoP proof htm claim does not match the request")
        }

        if (normalizeTargetUri(claims.targetUri) != normalizeTargetUri(targetUri)) {
            throw DpopProofException("The DPoP proof htu claim does not match the request")
        }
    }

    private fun validateAccessTokenBinding(accessTokenHash: String?, accessToken: String?) {
        if (accessToken == null && accessTokenHash != null) {
            throw DpopProofException("The DPoP proof ath claim is not allowed without an access token")
        }

        if (accessToken != null) {
            validateAccessTokenHash(accessTokenHash, accessToken)
        }
    }

    private fun keyThumbprint(publicKey: JWK): String {
        return try {
            publicKey.computeThumbprint().toString()
        } catch (_: Exception) {
            throw DpopProofException("The DPoP proof public key thumbprint cannot be computed")
        }
    }

    private fun validateKeyBinding(actualThumbprint: String, expectedThumbprint: String?) {
        if (expectedThumbprint != null && actualThumbprint != expectedThumbprint) {
            throw DpopProofException("The DPoP proof key does not match the bound key")
        }
    }

    private fun validateTime(issuedAt: Instant) {
        val now = clock.instant()
        if (issuedAt.isBefore(now.minus(proofLifetime)) || issuedAt.isAfter(now.plus(futureClockSkew))) {
            throw DpopProofException("The DPoP proof iat claim is outside the accepted time window")
        }
    }

    private fun validateAccessTokenHash(actualHash: String?, accessToken: String) {
        if (actualHash == null) {
            throw DpopProofException("The DPoP proof ath claim is required")
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray(Charsets.US_ASCII))
        val expectedHash = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)

        if (!MessageDigest.isEqual(actualHash.toByteArray(), expectedHash.toByteArray())) {
            throw DpopProofException("The DPoP proof ath claim does not match the access token")
        }
    }

    private fun optionalStringClaim(value: Any?, name: String): String? {
        if (value == null) {
            return null
        }

        if (value !is String) {
            throw DpopProofException("The DPoP proof $name claim must be a string")
        }

        return value
    }

    private fun preventReplay(keyThumbprint: String, jti: String) {
        val now = clock.instant()
        replayExpirations.entries.removeIf { !now.isBefore(it.value) }
        val replayKey = "$keyThumbprint:$jti"
        val previous = replayExpirations.putIfAbsent(replayKey, now.plus(proofLifetime))

        if (previous != null) {
            throw DpopProofException("The DPoP proof has already been used")
        }
    }

    private fun normalizeTargetUri(value: String): URI {
        val uri = try {
            URI(value)
        } catch (_: IllegalArgumentException) {
            throw DpopProofException("The DPoP proof htu claim is not a valid URI")
        }

        if (!uri.isAbsolute) {
            throw DpopProofException("The DPoP proof htu claim must be an absolute URI")
        }

        return URI(uri.scheme?.lowercase(), uri.rawAuthority?.lowercase(), uri.rawPath, null, null)
    }
}
