package de.antonlorani.eudi.golfmembership.vci

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Int,
    val cNonce: String,
    val cNonceExpiresIn: Int,
)
