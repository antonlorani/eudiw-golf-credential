package de.antonlorani.eudi.golfmembership.landing

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class LandingPageController(private val configuration: LandingPageConfiguration) {

    @GetMapping("/")
    fun landing(model: Model): String {
        model.addAttribute("config", configuration)
        return "landing"
    }
}
