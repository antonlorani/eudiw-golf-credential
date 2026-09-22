package de.antonlorani.eudi.golfmembership.demo

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class DemoConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
