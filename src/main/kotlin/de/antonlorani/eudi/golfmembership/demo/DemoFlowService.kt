package de.antonlorani.eudi.golfmembership.demo

import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.LinkedHashMap
import java.util.UUID

@Service
class DemoFlowService(
    private val clock: Clock,
) {

    private val maxSessions = 1000
    private val sessions = LinkedHashMap<String, DemoSession>()

    @Synchronized
    fun createSession(): DemoSession {
        val id = UUID.randomUUID().toString().substring(0, 8)
        val session = DemoSession(id)
        sessions[id] = session
        evict()
        return session
    }

    private fun evict() {
        while (sessions.size > maxSessions) {
            val oldest = sessions.keys.first()
            sessions.remove(oldest)
        }
    }

    @Synchronized
    fun getSession(id: String): DemoSession? = sessions[id]

    @Synchronized
    fun startVerification(
        id: String,
        selection: BookingSelection,
        nonce: String,
        responseState: String,
        expiresAt: Instant,
    ): VerificationStartResult {
        val session = sessions[id] ?: return VerificationStartResult.SessionNotFound
        if (session.state != DemoState.Booking) {
            return VerificationStartResult.NotBooking
        }

        val updated = session.copy(
            state = DemoState.PendingVerification(selection, nonce, responseState, expiresAt),
        )
        sessions[id] = updated
        return VerificationStartResult.Started(updated)
    }

    @Synchronized
    fun completeVerification(
        id: String,
        responseState: String,
        outcome: VerificationOutcome,
        rejectionReason: VerificationRejectionReason? = null,
    ): VerificationCompletionResult {
        val session = sessions[id] ?: return VerificationCompletionResult.SessionNotFound
        val pending = when (val currentState = session.state) {
            is DemoState.PendingVerification -> currentState
            else -> return VerificationCompletionResult.NotPending
        }

        if (pending.responseState != responseState) {
            return VerificationCompletionResult.ResponseStateMismatch
        }
        if (clock.instant().isAfter(pending.expiresAt)) {
            return VerificationCompletionResult.Expired
        }

        val completedState = when (outcome) {
            VerificationOutcome.ACCEPTED -> DemoState.VerificationAccepted(pending.selection)
            VerificationOutcome.REJECTED -> DemoState.VerificationRejected(pending.selection, rejectionReason)
        }
        val completedSession = session.copy(state = completedState)
        sessions[id] = completedSession
        return VerificationCompletionResult.Completed(completedSession)
    }
}
