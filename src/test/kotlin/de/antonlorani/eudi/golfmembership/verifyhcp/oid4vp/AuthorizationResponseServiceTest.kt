package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import de.antonlorani.eudi.golfmembership.demo.BookingSelection
import de.antonlorani.eudi.golfmembership.demo.DemoFlowService
import de.antonlorani.eudi.golfmembership.demo.DemoState
import de.antonlorani.eudi.golfmembership.demo.VerificationRejectionReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AuthorizationResponseServiceTest {
    private val now = Instant.parse("2026-09-22T12:00:00Z")

    @Test
    fun `tournament accepts an hcp at its maximum`() {
        val sessions = DemoFlowService(Clock.fixed(now, ZoneOffset.UTC))
        val session = sessions.createSession()
        sessions.startVerification(
            session.id,
            BookingSelection.Tournament("tournament", 18.0),
            "nonce",
            "state",
            now.plusSeconds(60),
        )
        val service = AuthorizationResponseService(
            sessions = sessions,
            vpTokenValidationService = { _, _ ->
                ValidPresentationResult(ValidatedPresentation(isHcpBelow37 = null, hcpIndex = 18.0))
            },
        )

        val processed = service.process(session.id, "state", "vp-token")

        assertEquals(true, processed)
        assertInstanceOf(
            DemoState.VerificationAccepted::class.java,
            sessions.getSession(session.id)?.state,
        )
    }

    @Test
    fun `course uses only the boolean predicate`() {
        val sessions = DemoFlowService(Clock.fixed(now, ZoneOffset.UTC))
        val session = sessions.createSession()
        sessions.startVerification(
            session.id,
            BookingSelection.GolfCourse("course", 36.0),
            "nonce",
            "state",
            now.plusSeconds(60),
        )
        val service = AuthorizationResponseService(
            sessions = sessions,
            vpTokenValidationService = { _, _ ->
                ValidPresentationResult(ValidatedPresentation(isHcpBelow37 = false, hcpIndex = 12.0))
            },
        )

        val processed = service.process(session.id, "state", "vp-token")

        assertEquals(true, processed)
        val rejected = assertInstanceOf(
            DemoState.VerificationRejected::class.java,
            sessions.getSession(session.id)?.state,
        )
        assertEquals(VerificationRejectionReason.HCP_TOO_HIGH, rejected.reason)
    }

    @Test
    fun `course below 36 rejects an hcp above its maximum despite the boolean predicate`() {
        assertEquals(
            DemoState.VerificationRejected::class.java,
            courseOutcome(28.0, ValidatedPresentation(isHcpBelow37 = true, hcpIndex = 35.0))?.javaClass,
        )
    }

    @Test
    fun `course below 36 accepts an hcp at its maximum`() {
        assertEquals(
            DemoState.VerificationAccepted::class.java,
            courseOutcome(28.0, ValidatedPresentation(isHcpBelow37 = null, hcpIndex = 28.0))?.javaClass,
        )
    }

    private fun courseOutcome(maximumHcp: Double, presentation: ValidatedPresentation): DemoState? {
        val sessions = DemoFlowService(Clock.fixed(now, ZoneOffset.UTC))
        val session = sessions.createSession()
        sessions.startVerification(
            session.id,
            BookingSelection.GolfCourse("course", maximumHcp),
            "nonce",
            "state",
            now.plusSeconds(60),
        )
        val service = AuthorizationResponseService(
            sessions = sessions,
            vpTokenValidationService = { _, _ -> ValidPresentationResult(presentation) },
        )
        service.process(session.id, "state", "vp-token")
        return sessions.getSession(session.id)?.state
    }

    @Test
    fun `course above 36 accepts a valid credential without disclosed claims`() {
        val sessions = DemoFlowService(Clock.fixed(now, ZoneOffset.UTC))
        val session = sessions.createSession()
        sessions.startVerification(
            session.id,
            BookingSelection.GolfCourse("course", 54.0),
            "nonce",
            "state",
            now.plusSeconds(60),
        )
        val service = AuthorizationResponseService(
            sessions = sessions,
            vpTokenValidationService = { _, _ ->
                ValidPresentationResult(ValidatedPresentation(isHcpBelow37 = null, hcpIndex = null))
            },
        )

        val processed = service.process(session.id, "state", "vp-token")

        assertEquals(true, processed)
        assertInstanceOf(
            DemoState.VerificationAccepted::class.java,
            sessions.getSession(session.id)?.state,
        )
    }
}
