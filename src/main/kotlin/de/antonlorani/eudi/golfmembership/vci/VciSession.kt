package de.antonlorani.eudi.golfmembership.vci

import java.time.Instant

data class VciSession(
    val id: String,
    val credentialConfigurationIds: List<String>,
    val expiresAt: Instant,
    val offerExpiresAt: Instant,
    val redirectUri: String? = null,
    val clientState: String? = null,
    val codeChallenge: String? = null,
    val codeChallengeMethod: String? = null,
    val clientId: String? = null,
    val scope: String? = null,
    val authorizationCode: String? = null,
    val authorizationCodeExpiresAt: Instant? = null,
    val selectedCredentialIndex: Int? = null,
    val accessToken: String? = null,
    val accessTokenExpiresAt: Instant? = null,
    val dpopKeyThumbprint: String? = null,
)
