package de.antonlorani.eudi.golfmembership.vci

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

@Configuration
class VciConfiguration(@Value("\${vci.issuer-url}") val issuerUrl: String) {

    @Bean
    fun issuerSigningKey(): ECKey =
        ECKeyGenerator(Curve.P_256)
            .keyID(UUID.randomUUID().toString())
            .generate()

    @Bean
    fun issuerCertificate(issuerSigningKey: ECKey): X509Certificate {
        val subject = X500Name("CN=localhost")
        val now = Instant.now()
        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(now.toEpochMilli()),
            Date.from(now),
            Date.from(now.plus(3650, ChronoUnit.DAYS)),
            subject,
            issuerSigningKey.toECPublicKey(),
        )
        val signer = JcaContentSignerBuilder("SHA256withECDSA")
            .build(issuerSigningKey.toECPrivateKey())
        return JcaX509CertificateConverter().getCertificate(certBuilder.build(signer))
    }

    @Bean
    fun credentialIssuerMetadata(): CredentialIssuerMetadata {
        return CredentialIssuerMetadata(
            credentialIssuer = issuerUrl,
            authorizationServers = listOf(issuerUrl),
            credentialEndpoint = "$issuerUrl/credential",
            display = listOf(
                CredentialIssuerMetadata.DisplayInfo(name = "National Golf Association", locale = "en"),
            ),
            credentialConfigurationsSupported = mapOf(
                CredentialData.CONFIGURATION_ID to CredentialIssuerMetadata.CredentialConfiguration(
                    format = CredentialData.FORMAT,
                    scope = CredentialData.SCOPE,
                    vct = CredentialData.VCT,
                    credentialSigningAlgValuesSupported = listOf("ES256"),
                    cryptographicBindingMethodsSupported = listOf("jwk"),
                    proofTypesSupported = mapOf(
                        "jwt" to CredentialIssuerMetadata.ProofTypeSupported(
                            proofSigningAlgValuesSupported = listOf("ES256"),
                        ),
                    ),
                    claims = GolfMembershipClaim.entries.associate { claim ->
                        claim.claimName to CredentialIssuerMetadata.ClaimMetadata(
                            display = listOf(CredentialIssuerMetadata.DisplayInfo(name = claim.displayName)),
                        )
                    },
                    display = listOf(
                        CredentialIssuerMetadata.DisplayInfo(name = "Golf Membership", locale = "en"),
                    ),
                ),
            ),
        )
    }
}
