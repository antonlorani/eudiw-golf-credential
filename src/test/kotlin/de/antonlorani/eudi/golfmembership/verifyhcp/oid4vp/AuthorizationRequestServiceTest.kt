package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.SignedJWT
import de.antonlorani.eudi.golfmembership.demo.BookingSelection
import de.antonlorani.eudi.golfmembership.demo.DemoState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.FileSystemResource
import java.net.URI
import java.net.URLDecoder
import java.security.cert.X509Certificate
import java.time.Instant
import tools.jackson.module.kotlin.jacksonObjectMapper

class AuthorizationRequestServiceTest {
    @Test
    fun `creates an ES256 signed x509 san dns request`() {
        val configuration = Oid4vpConfiguration("https://localhost:8443", "localhost")
        val signingMaterialService = VerifierSigningMaterialService(
            keyStoreResource = FileSystemResource("docker/certs/oid4vp-verifier.p12"),
            keyStorePassword = "changeit",
            keyAlias = "oid4vp-verifier",
        )
        val responseEncryptionKey = VerifierResponseEncryptionKey(
            ECKeyGenerator(Curve.P_256)
                .keyUse(KeyUse.ENCRYPTION)
                .algorithm(JWEAlgorithm.ECDH_ES)
                .keyID("response-key")
                .generate()
        )
        val service = AuthorizationRequestService(
            configuration,
            signingMaterialService,
            responseEncryptionKey,
            jacksonObjectMapper(),
        )
        val pending = DemoState.PendingVerification(
            selection = BookingSelection.GolfCourse("course", 36.0),
            nonce = "nonce",
            responseState = "state",
            expiresAt = Instant.now().plusSeconds(60),
        )

        val authorizationUri = URI(service.create("session", pending))
        val parameters = authorizationUri.rawQuery.split('&').associate { parameter ->
            parameter.substringBefore('=') to URLDecoder.decode(parameter.substringAfter('='), Charsets.UTF_8)
        }
        val requestObject = SignedJWT.parse(service.createRequestObject("session", pending))
        val certificate = signingMaterialService.verifierSigningMaterial().certificateChain.first()

        assertEquals("x509_san_dns:localhost", parameters["client_id"])
        assertEquals("https://localhost:8443/oid4vp/requests/session", parameters["request_uri"])
        assertEquals(JWSAlgorithm.ES256, requestObject.header.algorithm)
        assertTrue(requestObject.verify(ECDSAVerifier(ECKey.parse(certificate))))
        assertEquals("x509_san_dns:localhost", requestObject.jwtClaimsSet.issuer)
        assertEquals("x509_san_dns:localhost", requestObject.jwtClaimsSet.getStringClaim("client_id"))
        assertEquals("direct_post.jwt", requestObject.jwtClaimsSet.getStringClaim("response_mode"))
        val clientMetadata = requestObject.jwtClaimsSet.getJSONObjectClaim("client_metadata")
        assertEquals(listOf("A128GCM"), clientMetadata["encrypted_response_enc_values_supported"])
        val jwks = clientMetadata["jwks"] as Map<*, *>
        val advertisedKey = (jwks["keys"] as List<*>).single() as Map<*, *>
        assertEquals("response-key", advertisedKey["kid"])
        assertEquals("ECDH-ES", advertisedKey["alg"])
        assertEquals(null, advertisedKey["d"])
        assertTrue(certificate.hasDnsName("localhost"))
    }

    @Test
    fun `course at 36 requests only the boolean predicate`() {
        assertEquals(listOf(listOf("is_hcp_below_37")), requestedClaimPaths(BookingSelection.GolfCourse("course", 36.0)))
    }

    @Test
    fun `course below 36 requests the exact hcp`() {
        assertEquals(listOf(listOf("hcp_index")), requestedClaimPaths(BookingSelection.GolfCourse("course", 28.0)))
    }

    @Test
    fun `course above 36 requests no claims`() {
        assertEquals(null, requestedClaimPaths(BookingSelection.GolfCourse("course", 54.0)))
    }

    private fun requestedClaimPaths(selection: BookingSelection): List<*>? {
        val service = AuthorizationRequestService(
            Oid4vpConfiguration("https://localhost:8443", "localhost"),
            VerifierSigningMaterialService(
                keyStoreResource = FileSystemResource("docker/certs/oid4vp-verifier.p12"),
                keyStorePassword = "changeit",
                keyAlias = "oid4vp-verifier",
            ),
            VerifierResponseEncryptionKey(
                ECKeyGenerator(Curve.P_256)
                    .keyUse(KeyUse.ENCRYPTION)
                    .algorithm(JWEAlgorithm.ECDH_ES)
                    .generate()
            ),
            jacksonObjectMapper(),
        )
        val pending = DemoState.PendingVerification(
            selection = selection,
            nonce = "nonce",
            responseState = "state",
            expiresAt = Instant.now().plusSeconds(60),
        )
        val requestObject = SignedJWT.parse(service.createRequestObject("session", pending))
        val dcql = requestObject.jwtClaimsSet.getJSONObjectClaim("dcql_query")
        val credential = (dcql["credentials"] as List<*>).single() as Map<*, *>
        return (credential["claims"] as List<*>?)?.map { (it as Map<*, *>)["path"] }
    }

    private fun X509Certificate.hasDnsName(expected: String): Boolean =
        subjectAlternativeNames.orEmpty().any { alternativeName ->
            alternativeName[0] == 2 && alternativeName[1] == expected
        }
}
