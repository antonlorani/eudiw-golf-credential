package de.antonlorani.eudi.golfmembership.vci

enum class GolfMembershipClaim(val claimName: String, val displayName: String) {
    DOCUMENT_NUMBER("document_number", "Document Number"),
    GIVEN_NAME("given_name", "Given Name"),
    FAMILY_NAME("family_name", "Family Name"),
    HCP_INDEX("hcp_index", "HCP Index"),
    IS_HCP_BELOW_37("is_hcp_below_37", "HCP Below 37"),
    CLUB_NAME("club_name", "Club Name"),
    MEMBERSHIP_VALID_UNTIL("membership_valid_until", "Membership Valid Until"),
}
