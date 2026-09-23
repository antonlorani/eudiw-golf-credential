package de.antonlorani.eudi.golfmembership.vci.par

import de.antonlorani.eudi.golfmembership.vci.CredentialData
import de.antonlorani.eudi.golfmembership.vci.VciIssuanceService
import de.antonlorani.eudi.golfmembership.vci.VciConfiguration
import de.antonlorani.eudi.golfmembership.vci.dpop.DpopProofService
import de.antonlorani.eudi.golfmembership.vci.dpop.DpopProofTestService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class PushedAuthorizationRequestServiceTest {
    private val now = Instant.parse("2026-09-23T10:00:00Z")
    private val issuerUrl = "https://issuer.example"
    private val dpopProofTestService = DpopProofTestService()

    @Test
    fun `stores a valid request and consumes it once for the same client`() {
        val issuanceService = VciIssuanceService()
        val offer = issuanceService.createOffer()
        val service = service(issuanceService, Clock.fixed(now, ZoneOffset.UTC))

        val requestUri = service.push(request(offer.id))

        assertEquals(offer.id, service.consume(requestUri, "wallet-client"))
        assertNull(service.consume(requestUri, "wallet-client"))
    }

    @Test
    fun `does not consume a request when the client does not match`() {
        val issuanceService = VciIssuanceService()
        val offer = issuanceService.createOffer()
        val service = service(issuanceService, Clock.fixed(now, ZoneOffset.UTC))
        val requestUri = service.push(request(offer.id))

        assertNull(service.consume(requestUri, "different-client"))
        assertEquals(offer.id, service.consume(requestUri, "wallet-client"))
    }

    @Test
    fun `accepts standard authorization code binding with dpop jkt`() {
        val issuanceService = VciIssuanceService()
        val offer = issuanceService.createOffer()
        val service = service(issuanceService, Clock.fixed(now, ZoneOffset.UTC))
        val keyThumbprint = dpopProofTestService.signingKey.computeThumbprint().toString()

        val requestUri = service.push(
            request(offer.id).copy(
                dpopKeyThumbprint = keyThumbprint,
                dpopProof = null,
            )
        )

        assertEquals(offer.id, service.consume(requestUri, "wallet-client"))
        assertEquals(keyThumbprint, issuanceService.getOffer(offer.id)?.dpopKeyThumbprint)
    }

    @Test
    fun `rejects dpop jkt that does not match the proof`() {
        val issuanceService = VciIssuanceService()
        val offer = issuanceService.createOffer()
        val service = service(issuanceService, Clock.fixed(now, ZoneOffset.UTC))

        val error = assertThrows(PushedAuthorizationRequestException::class.java) {
            service.push(request(offer.id).copy(dpopKeyThumbprint = "another-thumbprint"))
        }

        assertEquals("invalid_request", error.error)
    }

    @Test
    fun `rejects an expired request`() {
        val issuanceService = VciIssuanceService()
        val offer = issuanceService.createOffer()
        val clock = MutableClock(now, ZoneOffset.UTC)
        val service = service(issuanceService, clock)
        val requestUri = service.push(request(offer.id))

        clock.advance(Duration.ofSeconds(60))

        assertNull(service.consume(requestUri, "wallet-client"))
    }

    @Test
    fun `rejects an unsupported response type`() {
        val issuanceService = VciIssuanceService()
        val offer = issuanceService.createOffer()
        val service = service(issuanceService, Clock.fixed(now, ZoneOffset.UTC))

        val error = assertThrows(PushedAuthorizationRequestException::class.java) {
            service.push(request(offer.id).copy(responseType = "token"))
        }

        assertEquals("unsupported_response_type", error.error)
    }

    @Test
    fun `rejects a missing required parameter`() {
        val issuanceService = VciIssuanceService()
        val offer = issuanceService.createOffer()
        val service = service(issuanceService, Clock.fixed(now, ZoneOffset.UTC))

        val error = assertThrows(PushedAuthorizationRequestException::class.java) {
            service.push(request(offer.id).copy(redirectUri = null))
        }

        assertEquals("invalid_request", error.error)
        assertEquals("redirect_uri is required", error.description)
    }

    @Test
    fun `rejects an unknown issuer state`() {
        val issuanceService = VciIssuanceService()
        val service = service(issuanceService, Clock.fixed(now, ZoneOffset.UTC))

        val error = assertThrows(PushedAuthorizationRequestException::class.java) {
            service.push(request("unknown"))
        }

        assertEquals("invalid_request", error.error)
    }

    @Test
    fun `rejects a code challenge method other than S256`() {
        val issuanceService = VciIssuanceService()
        val offer = issuanceService.createOffer()
        val service = service(issuanceService, Clock.fixed(now, ZoneOffset.UTC))

        val error = assertThrows(PushedAuthorizationRequestException::class.java) {
            service.push(request(offer.id).copy(codeChallengeMethod = "plain"))
        }

        assertEquals("invalid_request", error.error)
    }

    private fun service(issuanceService: VciIssuanceService, clock: Clock): PushedAuthorizationRequestService {
        return PushedAuthorizationRequestService(
            issuanceService,
            VciConfiguration(issuerUrl),
            DpopProofService(clock),
            clock,
        )
    }

    private fun request(issuerState: String): PushedAuthorizationRequest {
        return PushedAuthorizationRequest(
            responseType = "code",
            clientId = "wallet-client",
            redirectUri = "eudi-wallet://authorization",
            state = "state",
            codeChallenge = "challenge",
            codeChallengeMethod = "S256",
            scope = CredentialData.SCOPE,
            issuerState = issuerState,
            dpopKeyThumbprint = null,
            dpopProof = dpopProofTestService.create("$issuerUrl/par", now),
        )
    }
}
