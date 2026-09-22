package de.antonlorani.eudi.golfmembership.demo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DemoFlowServiceTest {
    private val now = Instant.parse("2026-09-22T12:00:00Z")
    private val service = DemoFlowService(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `verification can be completed only once`() {
        val session = service.createSession()
        service.startVerification(
            session.id,
            BookingSelection.GolfCourse("course", 36.0),
            "nonce",
            "state",
            now.plusSeconds(60),
        )

        val first = service.completeVerification(session.id, "state", VerificationOutcome.ACCEPTED)
        val replay = service.completeVerification(session.id, "state", VerificationOutcome.ACCEPTED)

        val completed = assertInstanceOf(VerificationCompletionResult.Completed::class.java, first)
        assertInstanceOf(DemoState.VerificationAccepted::class.java, completed.session.state)
        assertEquals(VerificationCompletionResult.NotPending, replay)
    }

    @Test
    fun `expired verification cannot be completed`() {
        val session = service.createSession()
        service.startVerification(
            session.id,
            BookingSelection.GolfCourse("course", 36.0),
            "nonce",
            "state",
            now.minusSeconds(1),
        )

        assertEquals(
            VerificationCompletionResult.Expired,
            service.completeVerification(session.id, "state", VerificationOutcome.ACCEPTED),
        )
    }
}
