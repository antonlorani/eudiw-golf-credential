package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import de.antonlorani.eudi.golfmembership.demo.DemoState

fun interface VpTokenValidationService {
    fun validate(pending: DemoState.PendingVerification, vpToken: String): PresentationValidationResult
}
