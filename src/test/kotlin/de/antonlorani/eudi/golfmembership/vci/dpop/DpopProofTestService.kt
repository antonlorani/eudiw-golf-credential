package de.antonlorani.eudi.golfmembership.vci.dpop

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

class DpopProofTestService {
    val signingKey: ECKey = ECKeyGenerator(Curve.P_256).generate()

    fun create(
        targetUri: String,
        issuedAt: Instant,
        httpMethod: String = "POST",
        accessToken: String? = null,
        jti: String = UUID.randomUUID().toString(),
        key: ECKey = signingKey,
    ): String {
        val claims = JWTClaimsSet.Builder()
            .jwtID(jti)
            .issueTime(Date.from(issuedAt))
            .claim("htm", httpMethod)
            .claim("htu", targetUri)
        if (accessToken != null) {
            claims.claim("ath", accessTokenHash(accessToken))
        }
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType("dpop+jwt"))
            .jwk(key.toPublicJWK())
            .build()
        return SignedJWT(header, claims.build()).apply {
            sign(ECDSASigner(key))
        }.serialize()
    }

    private fun accessTokenHash(accessToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
