package de.antonlorani.eudi.golfmembership.authorizecredential

import de.antonlorani.eudi.golfmembership.vci.VciIssuanceService
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
) {

    @GetMapping("/authorize")
    fun authorize(
        @RequestParam("response_type") responseType: String,
        @RequestParam("client_id") clientId: String,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("state", required = false) state: String?,
        @RequestParam("code_challenge") codeChallenge: String,
        @RequestParam("code_challenge_method") codeChallengeMethod: String,
        @RequestParam("issuer_state", required = false) issuerState: String?,
        model: Model,
    ): String {
        if (issuerState != null) {
            vciIssuanceService.setAuthorizationParams(
                offerId = issuerState,
                redirectUri = redirectUri,
                clientState = state,
                codeChallenge = codeChallenge,
                codeChallengeMethod = codeChallengeMethod,
                clientId = clientId,
            )
        }
        model.addAttribute("config", configuration)
        model.addAttribute("offerId", issuerState)
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
        return "redirect:${session.redirectUri}?code=$code$state"
    }
}
