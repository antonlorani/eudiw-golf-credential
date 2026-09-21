package de.antonlorani.eudi.golfmembership.vci

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.stereotype.Service
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Date

@Service
class MetadataSigningService(
    private val issuerSigningKey: ECKey,
    private val issuerCertificate: X509Certificate,
    private val vciConfiguration: VciConfiguration,
    private val objectMapper: ObjectMapper,
) {

    fun sign(metadata: CredentialIssuerMetadata): String {
        val now = Instant.now()
        val issuerUrl = vciConfiguration.issuerUrl

        val claims = JWTClaimsSet.Builder()
            .issuer(issuerUrl)
            .subject(issuerUrl)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(86400)))

        val metadataMap: Map<String, Any> = objectMapper.convertValue(
            metadata, object : TypeReference<Map<String, Any>>() {}
        )
        metadataMap.forEach { (key, value) -> claims.claim(key, value) }

        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType("openidvci-issuer-metadata+jwt"))
            .x509CertChain(listOf(Base64.encode(issuerCertificate.encoded)))
            .build()

        val jwt = SignedJWT(header, claims.build())
        jwt.sign(ECDSASigner(issuerSigningKey))
        return jwt.serialize()
    }
}
