package de.antonlorani.eudi.golfmembership.vci

data class VciSession(
    val id: String,
    val credentialConfigurationIds: List<String>,
    val redirectUri: String? = null,
    val clientState: String? = null,
    val codeChallenge: String? = null,
    val codeChallengeMethod: String? = null,
    val clientId: String? = null,
    val scope: String? = null,
    val authorizationCode: String? = null,
    val selectedCredentialIndex: Int? = null,
    val accessToken: String? = null,
    val cNonce: String? = null,
)
