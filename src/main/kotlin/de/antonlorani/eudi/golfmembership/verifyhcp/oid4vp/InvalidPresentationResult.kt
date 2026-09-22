package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

data class InvalidPresentationResult(
    val error: PresentationValidationError,
) : PresentationValidationResult

enum class PresentationValidationError {
    INVALID_PRESENTATION,
    UNTRUSTED_ISSUER,
    UNEXPECTED_CREDENTIAL_TYPE,
    CREDENTIAL_EXPIRED,
    INVALID_CLAIMS,
}
