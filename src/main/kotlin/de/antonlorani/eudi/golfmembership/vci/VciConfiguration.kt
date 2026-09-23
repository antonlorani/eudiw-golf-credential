package de.antonlorani.eudi.golfmembership.vci

import com.nimbusds.jose.jwk.ECKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import java.security.KeyStore
import java.security.cert.X509Certificate

@Configuration
class VciConfiguration(@Value("\${vci.issuer-url}") val issuerUrl: String) {

    @Bean
    fun issuerSigningKey(
        @Value("\${vci.signing-keystore.location}") keyStoreResource: Resource,
        @Value("\${vci.signing-keystore.password}") keyStorePassword: String,
        @Value("\${vci.signing-keystore.alias}") keyAlias: String,
    ): ECKey {
        val password = keyStorePassword.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStoreResource.inputStream.use { keyStore.load(it, password) }
        return ECKey.load(keyStore, keyAlias, password)
    }

    @Bean
    fun issuerCertificate(
        @Value("\${vci.signing-keystore.location}") keyStoreResource: Resource,
        @Value("\${vci.signing-keystore.password}") keyStorePassword: String,
        @Value("\${vci.signing-keystore.alias}") keyAlias: String,
    ): X509Certificate {
        val password = keyStorePassword.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStoreResource.inputStream.use { keyStore.load(it, password) }
        val certificate = keyStore.getCertificate(keyAlias)
        if (certificate !is X509Certificate) {
            throw IllegalStateException("Issuer certificate is not an X.509 certificate")
        }
        return certificate
    }

    @Bean
    fun credentialIssuerMetadata(): CredentialIssuerMetadata {
        return CredentialIssuerMetadata(
            credentialIssuer = issuerUrl,
            authorizationServers = listOf(issuerUrl),
            credentialEndpoint = "$issuerUrl/credential",
            display = listOf(
                CredentialIssuerMetadata.DisplayInfo(
                    name = "National Golf Association",
                    locale = "en",
                    logo = CredentialIssuerMetadata.Logo(
                        uri = "$issuerUrl/images/national-golf-association.png",
                        altText = "National Golf Association logo",
                    ),
                ),
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
                    credentialMetadata = CredentialIssuerMetadata.CredentialMetadata(
                        display = listOf(
                            CredentialIssuerMetadata.DisplayInfo(
                                name = "Golf Membership",
                                locale = "en",
                                logo = CredentialIssuerMetadata.Logo(
                                    uri = "$issuerUrl/images/national-golf-association.png",
                                    altText = "National Golf Association logo",
                                ),
                                backgroundColor = "#264A2C",
                            ),
                        ),
                        claims = GolfMembershipClaim.entries.map { claim ->
                            CredentialIssuerMetadata.ClaimMetadata(
                                path = listOf(claim.claimName),
                                display = listOf(CredentialIssuerMetadata.DisplayInfo(name = claim.displayName)),
                            )
                        },
                    ),
                ),
            ),
        )
    }
}
