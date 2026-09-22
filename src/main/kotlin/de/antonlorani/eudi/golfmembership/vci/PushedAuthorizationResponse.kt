package de.antonlorani.eudi.golfmembership.vci

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class PushedAuthorizationResponse(
    val requestUri: String,
    val expiresIn: Int,
)
