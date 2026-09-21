package de.antonlorani.eudi.golfmembership.vci

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CredentialOfferResponse(
    val credentialIssuer: String,
    val credentialConfigurationIds: List<String>,
    val grants: Grants,
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class Grants(
        val authorizationCode: AuthorizationCodeGrant,
    )

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class AuthorizationCodeGrant(
        val issuerState: String,
    )
}
