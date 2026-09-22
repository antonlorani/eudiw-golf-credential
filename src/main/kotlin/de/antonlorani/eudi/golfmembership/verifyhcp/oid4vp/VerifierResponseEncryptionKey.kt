package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import com.nimbusds.jose.jwk.ECKey

data class VerifierResponseEncryptionKey(val value: ECKey)
