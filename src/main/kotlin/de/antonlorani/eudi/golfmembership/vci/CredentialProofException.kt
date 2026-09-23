package de.antonlorani.eudi.golfmembership.vci

class CredentialProofException(
    val error: String = "invalid_proof",
    val description: String,
) : RuntimeException(description)
