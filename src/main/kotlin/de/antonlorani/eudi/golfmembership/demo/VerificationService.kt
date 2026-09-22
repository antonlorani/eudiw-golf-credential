package de.antonlorani.eudi.golfmembership.demo

import de.antonlorani.eudi.golfmembership.booking.BookingPageConfiguration
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64

@Service
class VerificationService(
    private val sessions: DemoFlowService,
    private val booking: BookingPageConfiguration,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    fun start(demoSessionId: String, selection: String): VerificationStartResult {
        val bookingSelection = resolve(selection) ?: return VerificationStartResult.InvalidSelection
        return sessions.startVerification(
            id = demoSessionId,
            selection = bookingSelection,
            nonce = randomValue(),
            responseState = randomValue(),
            expiresAt = clock.instant().plus(Duration.ofMinutes(5)),
        )
    }

    private fun resolve(selection: String): BookingSelection? {
        val parts = selection.split(':', limit = 2)
        if (parts.size != 2) {
            return null
        }
        val type = parts[0]
        val id = parts[1]
        return when (type) {
            "course" -> {
                val course = booking.golfCourses.singleOrNull { it.id == id } ?: return null
                BookingSelection.GolfCourse(course.id, course.maximumHcp)
            }
            "tournament" -> {
                val tournament = booking.tournaments.singleOrNull { it.id == id } ?: return null
                BookingSelection.Tournament(tournament.id, tournament.maximumHcp)
            }
            else -> null
        }
    }

    private fun randomValue(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
