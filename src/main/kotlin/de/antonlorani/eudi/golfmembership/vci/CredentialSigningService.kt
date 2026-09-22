package de.antonlorani.eudi.golfmembership.vci

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import eu.europa.ec.eudi.sdjwt.NimbusSdJwtOps
import eu.europa.ec.eudi.sdjwt.dsl.values.sdJwt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class CredentialSigningService(
    private val issuerSigningKey: ECKey,
    private val vciConfiguration: VciConfiguration,
) {

    suspend fun sign(data: CredentialData, holderPublicKey: JWK): String {
        val signingKey = if (data.spoofed) {
            ECKeyGenerator(Curve.P_256).generate()
        } else {
            issuerSigningKey
        }

        val now = Instant.now()
        val exp = if (data.expired) {
            now.minus(30, ChronoUnit.DAYS)
        } else {
            now.plus(365, ChronoUnit.DAYS)
        }

        val cnf = JsonObject(mapOf("jwk" to JsonObject(
            holderPublicKey.toPublicJWK().toJSONObject()
                .mapValues { (_, v) -> JsonPrimitive(v.toString()) }
        )))

        val spec = sdJwt {
            claim("iss", vciConfiguration.issuerUrl)
            claim("iat", now.epochSecond)
            claim("exp", exp.epochSecond)
            claim("vct", CredentialData.VCT)
            claim("cnf", cnf)
            sdClaim(GolfMembershipClaim.DOCUMENT_NUMBER.claimName, data.documentNumber)
            sdClaim(GolfMembershipClaim.GIVEN_NAME.claimName, data.givenName)
            sdClaim(GolfMembershipClaim.FAMILY_NAME.claimName, data.familyName)
            sdClaim(GolfMembershipClaim.HCP_INDEX.claimName, data.hcpIndex)
            sdClaim(GolfMembershipClaim.IS_HCP_BELOW_37.claimName, data.isHcpBelow37)
            sdClaim(GolfMembershipClaim.CLUB_NAME.claimName, data.clubName)
            sdClaim(GolfMembershipClaim.MEMBERSHIP_VALID_UNTIL.claimName, data.membershipValidUntil)
        }

        return with(NimbusSdJwtOps) {
            val issuer = issuer(
                signer = ECDSASigner(signingKey),
                signAlgorithm = JWSAlgorithm.ES256,
            ) { keyID(signingKey.keyID) }
            val sdJwt = issuer.issue(spec).getOrThrow()
            sdJwt.serialize()
        }
    }
}
