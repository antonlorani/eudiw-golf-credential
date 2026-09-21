package de.antonlorani.eudi.golfmembership.vci

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CredentialRequest(
    val format: String? = null,
    val proof: Proof? = null,
    val proofs: Proofs? = null,
    val credentialIdentifier: String? = null,
) {

    fun isBatch(): Boolean = proofs != null

    fun extractProofJwt(): String? {
        proof?.jwt?.let { return it }
        return proofs?.jwt?.firstOrNull()
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class Proof(
        val proofType: String? = null,
        val jwt: String? = null,
    )

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class Proofs(
        val jwt: List<String>? = null,
    )
}
