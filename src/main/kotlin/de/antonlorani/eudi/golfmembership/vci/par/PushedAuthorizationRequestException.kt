package de.antonlorani.eudi.golfmembership.vci.par

class PushedAuthorizationRequestException(
    val error: String,
    val description: String,
) : RuntimeException(description)
