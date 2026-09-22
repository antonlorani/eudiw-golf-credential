package de.antonlorani.eudi.golfmembership.demo

import java.time.Instant

sealed interface DemoState {
    data object Booking : DemoState

    data class PendingVerification(
        val selection: BookingSelection,
        val nonce: String,
        val responseState: String,
        val expiresAt: Instant,
    ) : DemoState

    data class VerificationAccepted(val selection: BookingSelection) : DemoState

    data class VerificationRejected(val selection: BookingSelection) : DemoState
}

sealed interface BookingSelection {
    data class GolfCourse(val id: String, val maximumHcp: Double) : BookingSelection
    data class Tournament(val id: String, val maximumHcp: Double) : BookingSelection
}

enum class VerificationOutcome {
    ACCEPTED,
    REJECTED,
}

sealed interface VerificationStartResult {
    data class Started(val session: DemoSession) : VerificationStartResult
    data object SessionNotFound : VerificationStartResult
    data object NotBooking : VerificationStartResult
    data object InvalidSelection : VerificationStartResult
}

sealed interface VerificationCompletionResult {
    data class Completed(val session: DemoSession) : VerificationCompletionResult
    data object SessionNotFound : VerificationCompletionResult
    data object NotPending : VerificationCompletionResult
    data object ResponseStateMismatch : VerificationCompletionResult
    data object Expired : VerificationCompletionResult
}
