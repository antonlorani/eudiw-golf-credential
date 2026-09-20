package de.antonlorani.eudi.golfmembership.authorizecredential

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class AuthorizeCredentialPageController(private val configuration: AuthorizeCredentialPageConfiguration) {

    @GetMapping("/authorize-credential")
    fun authorizeCredential(model: Model): String {
        model.addAttribute("config", configuration)
        return "authorize-credential"
    }
}
