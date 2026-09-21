package de.antonlorani.eudi.golfmembership.vci

data class JwtVcIssuerMetadata(
    val issuer: String,
    val jwks: Jwks,
) {

    data class Jwks(
        val keys: List<Map<String, Any>>,
    )
}
