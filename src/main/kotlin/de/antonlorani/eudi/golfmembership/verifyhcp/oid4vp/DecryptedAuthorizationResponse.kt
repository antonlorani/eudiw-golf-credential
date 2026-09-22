package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

data class DecryptedAuthorizationResponse(
    val state: String,
    val vpToken: String?,
)
