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
        @RequestParam("client_id") clientId: String,
        @RequestParam("request_uri", required = false) requestUri: String?,
        @RequestParam("response_type", required = false) responseType: String?,
        @RequestParam("redirect_uri", required = false) redirectUri: String?,
        @RequestParam("state", required = false) state: String?,
        @RequestParam("code_challenge", required = false) codeChallenge: String?,
        @RequestParam("code_challenge_method", required = false) codeChallengeMethod: String?,
        @RequestParam("scope", required = false) scope: String?,
        @RequestParam("issuer_state", required = false) issuerState: String?,
        model: Model,
    ): String {
        val offerId = when {
            requestUri != null -> vciIssuanceService.resolveRequestUri(requestUri)
            issuerState != null -> {
                vciIssuanceService.setAuthorizationParams(
                    offerId = issuerState,
                    redirectUri = redirectUri!!,
                    clientState = state,
                    codeChallenge = codeChallenge!!,
                    codeChallengeMethod = codeChallengeMethod!!,
                    clientId = clientId,
                    scope = scope!!,
                )
                issuerState
            }
            else -> null
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
        return "redirect:${session.redirectUri}?code=$code$state"
    }
}
