package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import de.antonlorani.eudi.golfmembership.demo.BookingSelection
import de.antonlorani.eudi.golfmembership.demo.DemoFlowService
import de.antonlorani.eudi.golfmembership.demo.DemoState
import de.antonlorani.eudi.golfmembership.demo.VerificationCompletionResult
import de.antonlorani.eudi.golfmembership.demo.VerificationOutcome
import de.antonlorani.eudi.golfmembership.demo.VerificationRejectionReason
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AuthorizationResponseService(
    private val sessions: DemoFlowService,
    private val vpTokenValidationService: VpTokenValidationService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun process(sessionId: String, state: String, vpToken: String?): Boolean {
        val session = sessions.getSession(sessionId) ?: return false
        val pending = when (val currentState = session.state) {
            is DemoState.PendingVerification -> currentState
            else -> return false
        }
        if (pending.responseState != state) {
            return false
        }

        val validation = if (vpToken == null) {
            null
        } else {
            vpTokenValidationService.validate(pending, vpToken)
        }
        val accepted = if (validation == null) {
            false
        } else {
            isEligible(pending.selection, validation)
        }
        if (!accepted) {
            val reason = when (validation) {
                null -> "missing_vp_token"
                is InvalidPresentationResult -> validation.error.name.lowercase()
                else -> "hcp_requirement_not_met"
            }
            logger.warn("Presentation for demo session {} rejected: {}", sessionId, reason)
        }
        val outcome = if (accepted) VerificationOutcome.ACCEPTED else VerificationOutcome.REJECTED
        val rejectionReason = if (validation is ValidPresentationResult && !accepted) {
            VerificationRejectionReason.HCP_TOO_HIGH
        } else {
            null
        }
        val completion = sessions.completeVerification(sessionId, state, outcome, rejectionReason)
        return completion is VerificationCompletionResult.Completed
    }

    private fun isEligible(
        selection: BookingSelection,
        result: PresentationValidationResult,
    ): Boolean {
        if (result !is ValidPresentationResult) return false
        return when (selection) {
            is BookingSelection.GolfCourse -> when {
                selection.maximumHcp > 36.0 -> true
                selection.maximumHcp == 36.0 -> result.presentation.isHcpBelow37 == true
                else -> result.presentation.hcpIndex != null &&
                    result.presentation.hcpIndex <= selection.maximumHcp
            }
            is BookingSelection.Tournament ->
                result.presentation.hcpIndex != null &&
                    result.presentation.hcpIndex <= selection.maximumHcp
        }
    }
}
