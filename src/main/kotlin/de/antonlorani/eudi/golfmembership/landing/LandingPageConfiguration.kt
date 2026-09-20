package de.antonlorani.eudi.golfmembership.landing

data class LandingPageConfiguration(
    val headline: String,
    val steps: List<Step>,
    val primaryButtonLabel: String,
    val footerText: String,
)
