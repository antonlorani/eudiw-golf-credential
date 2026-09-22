package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import de.antonlorani.eudi.golfmembership.vci.CredentialData
import de.antonlorani.eudi.golfmembership.vci.VciConfiguration
import de.antonlorani.eudi.golfmembership.demo.DemoState
import eu.europa.ec.eudi.sdjwt.ChallengePredicate
import eu.europa.ec.eudi.sdjwt.KeyBindingVerifier
import eu.europa.ec.eudi.sdjwt.NimbusSdJwtOps
import eu.europa.ec.eudi.sdjwt.NimbusSdJwtOps.asJwtVerifier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import org.springframework.stereotype.Component
import java.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toKotlinInstant

@Component
class SdJwtVpTokenValidationService(
    private val issuerSigningKey: ECKey,
    private val issuerConfiguration: VciConfiguration,
    private val oid4vpConfiguration: Oid4vpConfiguration,
    private val clock: Clock,
) : VpTokenValidationService {

    override fun validate(
        pending: DemoState.PendingVerification,
        vpToken: String,
    ): PresentationValidationResult {
        return try {
            val claims = runBlocking { validateOrThrow(pending, vpToken) }
            ValidPresentationResult(parseClaims(claims))
        } catch (error: PresentationValidationException) {
            InvalidPresentationResult(error.error)
        } catch (_: Exception) {
            InvalidPresentationResult(PresentationValidationError.INVALID_PRESENTATION)
        }
    }

    private suspend fun validateOrThrow(pending: DemoState.PendingVerification, serialized: String): JsonObject {
        val verified = with(NimbusSdJwtOps) {
            val issuerVerifier = ECDSAVerifier(issuerSigningKey.toPublicJWK()).asJwtVerifier()
            val keyBindingVerifier = KeyBindingVerifier.mustBePresentAndValid(challenge = null)
            val challenge = ChallengePredicate(
                issuedAt = clock.instant().toKotlinInstant(),
                audience = oid4vpConfiguration.clientId,
                nonce = pending.nonce,
                skew = 5.minutes,
            )
            verify(issuerVerifier, keyBindingVerifier, challenge, serialized).getOrThrow().sdJwt
        }

        val issuerClaims = verified.jwt.jwtClaimsSet
        if (issuerClaims.issuer != issuerConfiguration.issuerUrl) {
            throw PresentationValidationException(PresentationValidationError.UNTRUSTED_ISSUER)
        }
        if (issuerClaims.getStringClaim("vct") != CredentialData.VCT) {
            throw PresentationValidationException(PresentationValidationError.UNEXPECTED_CREDENTIAL_TYPE)
        }
        if (issuerClaims.expirationTime?.toInstant()?.isAfter(clock.instant()) != true) {
            throw PresentationValidationException(PresentationValidationError.CREDENTIAL_EXPIRED)
        }
        return with(NimbusSdJwtOps) {
            verified.recreateClaimsAndDisclosuresPerClaim().first
        }
    }

    private fun parseClaims(claims: JsonObject): ValidatedPresentation {
        return ValidatedPresentation(
            isHcpBelow37 = optionalBoolean(claims, "is_hcp_below_37"),
            hcpIndex = optionalDouble(claims, "hcp_index"),
        )
    }

    private fun optionalBoolean(claims: JsonObject, name: String): Boolean? {
        val value = claims[name] ?: return null
        if (value !is JsonPrimitive) {
            throw PresentationValidationException(PresentationValidationError.INVALID_CLAIMS)
        }
        return value.booleanOrNull
            ?: throw PresentationValidationException(PresentationValidationError.INVALID_CLAIMS)
    }

    private fun optionalDouble(claims: JsonObject, name: String): Double? {
        val value = claims[name] ?: return null
        if (value !is JsonPrimitive) {
            throw PresentationValidationException(PresentationValidationError.INVALID_CLAIMS)
        }
        return value.doubleOrNull
            ?: throw PresentationValidationException(PresentationValidationError.INVALID_CLAIMS)
    }

    private class PresentationValidationException(val error: PresentationValidationError) : Exception()
}
