package de.antonlorani.eudi.golfmembership.mock

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import de.antonlorani.eudi.golfmembership.authorizecredential.AuthorizeCredentialPageConfiguration
import de.antonlorani.eudi.golfmembership.booking.BookingPageConfiguration
import de.antonlorani.eudi.golfmembership.failure.FailurePageConfiguration
import de.antonlorani.eudi.golfmembership.issuecredential.IssueCredentialPageConfiguration
import de.antonlorani.eudi.golfmembership.landing.LandingPageConfiguration
import de.antonlorani.eudi.golfmembership.success.SuccessPageConfiguration
import de.antonlorani.eudi.golfmembership.verifyhcp.VerifyHcpPageConfiguration
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource

@Configuration
class MockDataConfiguration(
    private val objectMapper: ObjectMapper,
    @Value("classpath:mock.json") private val mockJson: Resource
) {

    private val root: JsonNode by lazy {
        mockJson.inputStream.use { objectMapper.readTree(it) }
    }

    @Bean
    fun landingPageConfiguration(): LandingPageConfiguration =
        objectMapper.treeToValue<LandingPageConfiguration>(root["landing"])

    @Bean
    fun issueCredentialPageConfiguration(): IssueCredentialPageConfiguration =
        objectMapper.treeToValue<IssueCredentialPageConfiguration>(root["issueCredential"])

    @Bean
    fun bookingPageConfiguration(): BookingPageConfiguration =
        objectMapper.treeToValue<BookingPageConfiguration>(root["booking"])

    @Bean
    fun authorizeCredentialPageConfiguration(): AuthorizeCredentialPageConfiguration =
        objectMapper.treeToValue<AuthorizeCredentialPageConfiguration>(root["authorizeCredential"])

    @Bean
    fun verifyHcpPageConfiguration(): VerifyHcpPageConfiguration =
        objectMapper.treeToValue<VerifyHcpPageConfiguration>(root["verifyHcp"])

    @Bean
    fun successPageConfiguration(): SuccessPageConfiguration =
        objectMapper.treeToValue<SuccessPageConfiguration>(root["success"])

    @Bean
    fun failurePageConfiguration(): FailurePageConfiguration =
        objectMapper.treeToValue<FailurePageConfiguration>(root["failure"])
}
