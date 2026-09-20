package de.antonlorani.eudi.golfmembership.authorizecredential

data class AuthorizeCredentialPageConfiguration(
    val headline: String,
    val infoText: String,
    val credentials: List<Credential>,
    val primaryButtonLabel: String,
    val footerText: String,
)
