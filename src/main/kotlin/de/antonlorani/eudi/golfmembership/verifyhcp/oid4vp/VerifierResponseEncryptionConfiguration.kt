package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.UUID

@Configuration
class VerifierResponseEncryptionConfiguration {
    @Bean
    fun verifierResponseEncryptionKey(): VerifierResponseEncryptionKey =
        VerifierResponseEncryptionKey(
            ECKeyGenerator(Curve.P_256)
                .keyUse(KeyUse.ENCRYPTION)
                .algorithm(JWEAlgorithm.ECDH_ES)
                .keyID(UUID.randomUUID().toString())
                .generate()
        )
}
