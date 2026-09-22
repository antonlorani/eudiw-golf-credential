package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import com.nimbusds.jose.jwk.ECKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.security.KeyStore
import java.security.cert.X509Certificate

@Service
class VerifierSigningMaterialService(
    @Value("\${oid4vp.signing-keystore.location}") private val keyStoreResource: Resource,
    @Value("\${oid4vp.signing-keystore.password}") private val keyStorePassword: String,
    @Value("\${oid4vp.signing-keystore.alias}") private val keyAlias: String,
) {
    private val signingMaterial by lazy(::loadSigningMaterial)

    fun verifierSigningMaterial(): VerifierSigningMaterial = signingMaterial

    private fun loadSigningMaterial(): VerifierSigningMaterial {
        val password = keyStorePassword.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStoreResource.inputStream.use { keyStore.load(it, password) }
        val certificate = keyStore.getCertificate(keyAlias)
        if (certificate !is X509Certificate) {
            throw IllegalStateException("Verifier certificate is not an X.509 certificate")
        }
        return VerifierSigningMaterial(
            signingKey = ECKey.load(keyStore, keyAlias, password),
            certificate = certificate,
        )
    }
}
