package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

data class ValidPresentationResult(
    val presentation: ValidatedPresentation,
) : PresentationValidationResult
