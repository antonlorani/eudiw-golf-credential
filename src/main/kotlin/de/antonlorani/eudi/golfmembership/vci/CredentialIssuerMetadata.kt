package de.antonlorani.eudi.golfmembership.vci

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CredentialIssuerMetadata(
    val credentialIssuer: String,
    val authorizationServers: List<String>,
    val credentialEndpoint: String,
    val display: List<DisplayInfo>,
    val credentialConfigurationsSupported: Map<String, CredentialConfiguration>,
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class DisplayInfo(
        val name: String,
        val locale: String? = null,
        val logo: Logo? = null,
    )

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class Logo(
        val uri: String,
        val altText: String? = null,
    )

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class CredentialConfiguration(
        val format: String,
        val scope: String,
        val vct: String,
        val credentialSigningAlgValuesSupported: List<String>,
        val cryptographicBindingMethodsSupported: List<String>,
        val proofTypesSupported: Map<String, ProofTypeSupported>,
        val claims: Map<String, ClaimMetadata>,
        val display: List<DisplayInfo>,
    )

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class ProofTypeSupported(
        val proofSigningAlgValuesSupported: List<String>,
        val keyAttestationsRequired: KeyAttestationsRequired? = null,
    )

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class KeyAttestationsRequired(
        val keyStorage: List<String>,
        val userAuthentication: List<String>,
    )

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class ClaimMetadata(
        val display: List<DisplayInfo>,
    )
}
