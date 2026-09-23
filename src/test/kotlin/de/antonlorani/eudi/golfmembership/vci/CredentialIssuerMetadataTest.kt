package de.antonlorani.eudi.golfmembership.vci

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class CredentialIssuerMetadataTest {
    private val issuerUrl = "https://issuer.example"

    @Test
    fun `publishes wallet display data as credential metadata`() {
        val metadata = VciConfiguration(issuerUrl).credentialIssuerMetadata()
        val json = jacksonObjectMapper().valueToTree<tools.jackson.databind.JsonNode>(metadata)
        val configuration = json["credential_configurations_supported"][CredentialData.CONFIGURATION_ID]
        val credentialMetadata = configuration["credential_metadata"]

        assertFalse(configuration.has("display"))
        assertFalse(configuration.has("claims"))
        assertEquals("Golf Membership", credentialMetadata["display"][0]["name"].asText())
        assertEquals(
            "$issuerUrl/images/national-golf-association.png",
            credentialMetadata["display"][0]["logo"]["uri"].asText(),
        )
        assertEquals("#264A2C", credentialMetadata["display"][0]["background_color"].asText())
        assertEquals("document_number", credentialMetadata["claims"][0]["path"][0].asText())
        assertEquals("Document Number", credentialMetadata["claims"][0]["display"][0]["name"].asText())
    }
}
