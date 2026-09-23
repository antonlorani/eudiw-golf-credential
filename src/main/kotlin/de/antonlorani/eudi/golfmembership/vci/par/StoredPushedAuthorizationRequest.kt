package de.antonlorani.eudi.golfmembership.vci.par

import java.time.Instant

data class StoredPushedAuthorizationRequest(
    val offerId: String,
    val clientId: String,
    val expiresAt: Instant,
)
