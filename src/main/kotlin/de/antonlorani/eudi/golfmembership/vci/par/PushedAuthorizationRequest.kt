package de.antonlorani.eudi.golfmembership.vci.par

data class PushedAuthorizationRequest(
    val responseType: String?,
    val clientId: String?,
    val redirectUri: String?,
    val state: String?,
    val codeChallenge: String?,
    val codeChallengeMethod: String?,
    val scope: String?,
    val issuerState: String?,
    val dpopKeyThumbprint: String?,
    val dpopProof: String?,
)
