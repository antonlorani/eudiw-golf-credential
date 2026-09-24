package de.antonlorani.eudi.golfmembership.vci

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CredentialRequest(
    val credentialConfigurationId: String? = null,
    val proofs: Proofs? = null,
    val credentialIdentifier: String? = null,
) {

    fun extractSingleProofJwt(): String? = proofs?.jwt?.singleOrNull()

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class Proofs(
        val jwt: List<String>? = null,
    )
}
