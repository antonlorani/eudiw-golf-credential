package de.antonlorani.eudi.golfmembership.failure

data class FailurePageConfiguration(
    val headline: FailurePageHeadlineConfiguration,
    val secondaryButtonLabel: String,
    val primaryButtonLabel: String,
    val footerText: String,
)

data class FailurePageHeadlineConfiguration(
    val general: String,
    val hcpTooHigh: String,
)
