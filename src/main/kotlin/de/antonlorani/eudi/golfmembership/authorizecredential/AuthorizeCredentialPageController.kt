package de.antonlorani.eudi.golfmembership.authorizecredential

import de.antonlorani.eudi.golfmembership.vci.VciIssuanceService
import de.antonlorani.eudi.golfmembership.vci.VciConfiguration
import de.antonlorani.eudi.golfmembership.vci.par.PushedAuthorizationRequestService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.net.URLEncoder

@Controller
class AuthorizeCredentialPageController(
    private val configuration: AuthorizeCredentialPageConfiguration,
    private val vciIssuanceService: VciIssuanceService,
    private val vciConfiguration: VciConfiguration,
    private val pushedAuthorizationRequestService: PushedAuthorizationRequestService,
) {

    @GetMapping("/authorize")
    fun authorize(
        @RequestParam("client_id") clientId: String,
        @RequestParam("request_uri") requestUri: String,
        model: Model,
        response: HttpServletResponse,
    ): String {
        val offerId = pushedAuthorizationRequestService.consume(requestUri, clientId)
        if (offerId == null) {
            response.status = HttpServletResponse.SC_BAD_REQUEST
        }
        model.addAttribute("config", configuration)
        model.addAttribute("offerId", offerId)
        return "authorize-credential"
    }

    @PostMapping("/authorize")
    fun authorizeSubmit(
        @RequestParam("offerId") offerId: String,
        @RequestParam("selection") selection: Int,
    ): String {
        val session = vciIssuanceService.authorize(offerId, selection)
            ?: return "redirect:/authorize?error=invalid_session"
        val code = URLEncoder.encode(session.authorizationCode!!, Charsets.UTF_8)
        val state = session.clientState?.let { "&state=${URLEncoder.encode(it, Charsets.UTF_8)}" } ?: ""
        val issuer = URLEncoder.encode(vciConfiguration.issuerUrl, Charsets.UTF_8)
        return "redirect:${session.redirectUri}?code=$code&iss=$issuer$state"
    }
}
