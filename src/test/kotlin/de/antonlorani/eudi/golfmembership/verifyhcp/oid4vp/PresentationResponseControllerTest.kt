package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import de.antonlorani.eudi.golfmembership.demo.BookingSelection
import de.antonlorani.eudi.golfmembership.demo.DemoFlowService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PresentationResponseControllerTest {
    @Test
    fun `redirects to the demo session that owns the presentation`() {
        val now = Instant.parse("2026-09-22T12:00:00Z")
        val sessions = DemoFlowService(Clock.fixed(now, ZoneOffset.UTC))
        val session = sessions.createSession()
        sessions.startVerification(
            session.id,
            BookingSelection.GolfCourse("course", 36.0),
            "nonce",
            "expected-state",
            now.plusSeconds(60),
        )
        val service = AuthorizationResponseService(
            sessions = sessions,
            vpTokenValidationService = { _, _ ->
                ValidPresentationResult(ValidatedPresentation(isHcpBelow37 = true, hcpIndex = null))
            },
        )
        val controller = PresentationResponseController(
            authorizationResponseService = service,
            configuration = Oid4vpConfiguration("https://localhost:8443", "localhost"),
            authorizationResponseDecodingService = {
                AuthorizationResponseDecodingResult.Decoded(
                    DecryptedAuthorizationResponse("expected-state", "vp-token"),
                )
            },
        )

        val response = controller.receive(
            id = session.id,
            response = "encrypted-response",
            state = null,
            error = null,
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(
            "https://localhost:8443/demo?session=${session.id}",
            response.body?.get("redirect_uri"),
        )
    }
}
