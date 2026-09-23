package de.antonlorani.eudi.golfmembership.vci.dpop

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DpopProofServiceTest {
    private val now = Instant.parse("2026-09-23T10:00:00Z")
    private val targetUri = "https://issuer.example/token"
    private val proofService = DpopProofTestService()
    private val service = DpopProofService(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `accepts a valid proof`() {
        val serializedProof = proofService.create(targetUri, now)

        val proof = service.verify(serializedProof, "POST", targetUri)

        assertEquals(proofService.signingKey.computeThumbprint().toString(), proof.keyThumbprint)
    }

    @Test
    fun `rejects a replayed proof`() {
        val serializedProof = proofService.create(targetUri, now)
        service.verify(serializedProof, "POST", targetUri)

        assertThrows(DpopProofException::class.java) {
            service.verify(serializedProof, "POST", targetUri)
        }
    }

    @Test
    fun `rejects a proof for another endpoint`() {
        val serializedProof = proofService.create("https://issuer.example/par", now)

        assertThrows(DpopProofException::class.java) {
            service.verify(serializedProof, "POST", targetUri)
        }
    }

    @Test
    fun `rejects an invalid access token hash`() {
        val serializedProof = proofService.create(targetUri, now, accessToken = "another-token")

        assertThrows(DpopProofException::class.java) {
            service.verify(serializedProof, "POST", targetUri, accessToken = "access-token")
        }
    }

    @Test
    fun `rejects an access token hash at the token endpoint`() {
        val serializedProof = proofService.create(targetUri, now, accessToken = "access-token")

        assertThrows(DpopProofException::class.java) {
            service.verify(serializedProof, "POST", targetUri)
        }
    }

    @Test
    fun `rejects a proof made with a different bound key`() {
        val serializedProof = proofService.create(targetUri, now)
        val anotherKey = ECKeyGenerator(Curve.P_256).generate()

        assertThrows(DpopProofException::class.java) {
            service.verify(
                serializedProof,
                "POST",
                targetUri,
                expectedKeyThumbprint = anotherKey.computeThumbprint().toString(),
            )
        }
    }
}
