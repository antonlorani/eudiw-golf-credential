package de.antonlorani.eudi.golfmembership

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class IndexControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `index returns hello world`() {
        mockMvc.get("/")
            .andExpect {
                status { isOk() }
                content { string("Hello, world!") }
            }
    }
}
