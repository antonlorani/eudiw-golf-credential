package de.antonlorani.eudi.golfmembership.vci

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AuthorizationServerMetadata(
    val issuer: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val pushedAuthorizationRequestEndpoint: String,
    val requirePushedAuthorizationRequests: Boolean,
    val responseTypesSupported: List<String>,
    val grantTypesSupported: List<String>,
    val codeChallengeMethodsSupported: List<String>,
    val dpopSigningAlgValuesSupported: List<String>,
)
