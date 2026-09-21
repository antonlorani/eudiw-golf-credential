package de.antonlorani.eudi.golfmembership.vci

data class CredentialData(
    val documentNumber: String,
    val givenName: String,
    val familyName: String,
    val hcpIndex: Double,
    val isHcpBelow36: Boolean,
    val clubName: String,
    val membershipValidUntil: String,
    val expired: Boolean = false,
    val spoofed: Boolean = false,
) {
    companion object {
        const val VCT = "urn:eu.europa.ec.eudi:golf_membership:1"
        const val CONFIGURATION_ID = "golf_membership_credential"
        const val FORMAT = "vc+sd-jwt"

        val ALL = listOf(
            CredentialData(
                documentNumber = "GM-2026-000001",
                givenName = "John",
                familyName = "Doe",
                hcpIndex = 36.0,
                isHcpBelow36 = false,
                clubName = "Royal St. Andrews Links",
                membershipValidUntil = "2027-12-31",
            ),
            CredentialData(
                documentNumber = "GM-2026-000002",
                givenName = "John",
                familyName = "Doe",
                hcpIndex = 12.0,
                isHcpBelow36 = true,
                clubName = "Royal St. Andrews Links",
                membershipValidUntil = "2027-12-31",
            ),
            CredentialData(
                documentNumber = "GM-2026-000003",
                givenName = "John",
                familyName = "Doe",
                hcpIndex = 24.0,
                isHcpBelow36 = true,
                clubName = "Royal St. Andrews Links",
                membershipValidUntil = "2027-12-31",
                expired = true,
            ),
            CredentialData(
                documentNumber = "GM-2026-000004",
                givenName = "John",
                familyName = "Doe",
                hcpIndex = 24.0,
                isHcpBelow36 = true,
                clubName = "Royal St. Andrews Links",
                membershipValidUntil = "2027-12-31",
                spoofed = true,
            ),
        )
    }
}
