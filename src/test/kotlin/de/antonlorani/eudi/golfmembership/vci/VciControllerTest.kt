package de.antonlorani.eudi.golfmembership.vci

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import de.antonlorani.eudi.golfmembership.vci.dpop.DpopProofService
import de.antonlorani.eudi.golfmembership.vci.par.PushedAuthorizationRequestService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class VciControllerTest {
    private val issuanceService = mock(VciIssuanceService::class.java)
    private val signingService = mock(CredentialSigningService::class.java)
    private val configuration = VciConfiguration("https://issuer.example")
    private val parService = mock(PushedAuthorizationRequestService::class.java)
    private val dpopService = mock(DpopProofService::class.java)
    private val proofService = mock(CredentialProofService::class.java)
    private val nonceService = mock(NonceService::class.java)
    private val controller = VciController(
        issuanceService,
        signingService,
        configuration,
        parService,
        dpopService,
        proofService,
        nonceService,
    )

    @Test
    fun `nonce response is not cacheable`() {
        `when`(nonceService.issue()).thenReturn("nonce")

        val response = controller.nonce()

        assertEquals("nonce", response.body?.cNonce)
        assertEquals("no-store", response.headers.cacheControl)
    }

    @Test
    fun `invalid proof does not consume the access token and is not cacheable`() = runBlocking {
        `when`(issuanceService.findByAccessToken("token")).thenReturn(pendingSession())

        val response = controller.credential(
            authorization = "DPoP token",
            dpopProof = "dpop-proof",
            request = CredentialRequest(
                credentialConfigurationId = CredentialData.CONFIGURATION_ID,
                proofs = CredentialRequest.Proofs(jwt = emptyList()),
            ),
        )

        assertEquals(400, response.statusCode.value())
        assertEquals("invalid_proof", (response.body as VciErrorResponse).error)
        assertEquals("no-store", response.headers.cacheControl)
        verify(issuanceService, never()).consumeAccessToken(anyString())
    }

    @Test
    fun `unknown credential configuration is rejected without consuming the token`() = runBlocking {
        `when`(issuanceService.findByAccessToken("token")).thenReturn(pendingSession())

        val response = controller.credential(
            authorization = "DPoP token",
            dpopProof = "dpop-proof",
            request = CredentialRequest(
                credentialConfigurationId = "unknown",
                proofs = CredentialRequest.Proofs(jwt = listOf("proof")),
            ),
        )

        assertEquals(400, response.statusCode.value())
        assertEquals("unknown_credential_configuration", (response.body as VciErrorResponse).error)
        assertEquals("no-store", response.headers.cacheControl)
        verify(issuanceService, never()).consumeAccessToken(anyString())
    }

    @Test
    fun `successful final request returns a non-cacheable credentials array`() = runBlocking {
        val session = pendingSession()
        val holderKey = ECKeyGenerator(Curve.P_256).generate().toPublicJWK()
        `when`(issuanceService.findByAccessToken("token")).thenReturn(session)
        `when`(issuanceService.consumeAccessToken("token")).thenReturn(session)
        `when`(proofService.verify("proof", session.clientId)).thenReturn(holderKey)
        `when`(signingService.sign(CredentialData.ALL.first(), holderKey)).thenReturn("credential")

        val response = controller.credential(
            authorization = "DPoP token",
            dpopProof = "dpop-proof",
            request = CredentialRequest(
                credentialConfigurationId = CredentialData.CONFIGURATION_ID,
                proofs = CredentialRequest.Proofs(jwt = listOf("proof")),
            ),
        )

        assertEquals(200, response.statusCode.value())
        assertEquals("no-store", response.headers.cacheControl)
        val body = response.body as BatchCredentialResponse
        assertEquals(listOf(CredentialResponse("credential")), body.credentials)
        assertNull(response.headers.location)
    }

    private fun pendingSession(): VciSession {
        return VciSession(
            id = "offer",
            credentialConfigurationIds = listOf(CredentialData.CONFIGURATION_ID),
            clientId = "wallet-client",
            selectedCredentialIndex = 0,
            accessToken = "token",
            dpopKeyThumbprint = "thumbprint",
        )
    }
}
