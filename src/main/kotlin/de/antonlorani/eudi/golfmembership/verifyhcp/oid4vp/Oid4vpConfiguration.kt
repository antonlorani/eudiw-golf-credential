package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class Oid4vpConfiguration(
    @Value("\${oid4vp.verifier-url}") val verifierUrl: String,
    @Value("\${oid4vp.client-id}") val originalClientId: String,
) {
    val clientId: String get() = "x509_san_dns:$originalClientId"
}
