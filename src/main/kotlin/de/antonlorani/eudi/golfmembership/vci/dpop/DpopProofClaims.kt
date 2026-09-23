package de.antonlorani.eudi.golfmembership.vci.dpop

import java.time.Instant

data class DpopProofClaims(
    val id: String,
    val issuedAt: Instant,
    val httpMethod: String,
    val targetUri: String,
    val accessTokenHash: String?,
)
