package de.antonlorani.eudi.golfmembership.demo

import de.antonlorani.eudi.golfmembership.booking.BookingPageConfiguration
import de.antonlorani.eudi.golfmembership.booking.GolfCourse
import de.antonlorani.eudi.golfmembership.booking.Tournament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class VerificationServiceTest {
    private val now = Instant.parse("2026-09-22T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val sessions = DemoFlowService(clock)
    private val service = VerificationService(
        sessions = sessions,
        booking = BookingPageConfiguration(
            headline = "",
            segmentLabels = emptyList(),
            golfCourses = listOf(GolfCourse("course", "Course", 36.0)),
            tournaments = listOf(Tournament("open", "Open", "Course", 18.0)),
            primaryButtonLabel = "",
            footerText = "",
        ),
        clock = clock,
    )

    @Test
    fun `starts verification for the selected booking`() {
        val session = sessions.createSession()

        val result = service.start(session.id, "tournament:open")
        val updated = assertInstanceOf(VerificationStartResult.Started::class.java, result).session

        val state = assertInstanceOf(DemoState.PendingVerification::class.java, updated.state)
        assertEquals(BookingSelection.Tournament("open", 18.0), state.selection)
        assertEquals(now.plusSeconds(300), state.expiresAt)
    }
}
