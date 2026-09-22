package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import com.nimbusds.jose.jwk.ECKey
import java.security.cert.X509Certificate

data class VerifierSigningMaterial(
    val signingKey: ECKey,
    val certificate: X509Certificate,
)
