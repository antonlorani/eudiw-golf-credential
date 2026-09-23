package de.antonlorani.eudi.golfmembership.demo

import de.antonlorani.eudi.golfmembership.booking.BookingPageConfiguration
import de.antonlorani.eudi.golfmembership.failure.FailurePageConfiguration
import de.antonlorani.eudi.golfmembership.issuecredential.IssueCredentialPageConfiguration
import de.antonlorani.eudi.golfmembership.qr.QrCodeService
import de.antonlorani.eudi.golfmembership.success.SuccessPageConfiguration
import de.antonlorani.eudi.golfmembership.vci.VciConfiguration
import de.antonlorani.eudi.golfmembership.vci.VciIssuanceService
import de.antonlorani.eudi.golfmembership.verifyhcp.VerifyHcpPageConfiguration
import de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp.AuthorizationRequestService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.net.URLEncoder

@Controller
class DemoFlowController(
    private val demoFlowService: DemoFlowService,
    private val issueCredentialConfig: IssueCredentialPageConfiguration,
    private val bookingConfig: BookingPageConfiguration,
    private val verifyHcpConfig: VerifyHcpPageConfiguration,
    private val successConfig: SuccessPageConfiguration,
    private val failureConfig: FailurePageConfiguration,
    private val verificationService: VerificationService,
    private val authorizationRequestService: AuthorizationRequestService,
    private val vciIssuanceService: VciIssuanceService,
    private val vciConfiguration: VciConfiguration,
    private val qrCodeService: QrCodeService,
) {

    @GetMapping("/demo")
    fun demo(@RequestParam session: String?, model: Model): String {
        if (session == null) {
            val offer = vciIssuanceService.createOffer()
            val offerUri = "${vciConfiguration.issuerUrl}/credential-offers/${offer.id}"
            val deepLink = "openid-credential-offer://?credential_offer_uri=${URLEncoder.encode(offerUri, Charsets.UTF_8)}"
            model.addAttribute("config", issueCredentialConfig)
            model.addAttribute("issueDeeplinkUrl", deepLink)
            model.addAttribute("qrCodeDataUrl", qrCodeService.dataUrl(deepLink))
            return "issue-credential"
        }
        val demoSession = demoFlowService.getSession(session)
            ?: return "redirect:/demo"
        model.addAttribute("sessionId", session)
        return when (val state = demoSession.state) {
            DemoState.Booking -> showBooking(model)
            is DemoState.PendingVerification -> showPendingVerification(model, demoSession.id, state)
            is DemoState.VerificationAccepted -> showSuccess(model)
            is DemoState.VerificationRejected -> showFailure(model, state)
        }
    }

    private fun showBooking(model: Model): String {
        model.addAttribute("config", bookingConfig)
        return "booking"
    }

    private fun showPendingVerification(
        model: Model,
        sessionId: String,
        pending: DemoState.PendingVerification,
    ): String {
        val deepLink = authorizationRequestService.create(sessionId, pending)
        model.addAttribute("config", verifyHcpConfig)
        model.addAttribute("deeplinkUrl", deepLink)
        model.addAttribute("qrCodeDataUrl", qrCodeService.dataUrl(deepLink))
        return "verify-hcp"
    }

    private fun showSuccess(model: Model): String {
        model.addAttribute("config", successConfig)
        return "success"
    }

    private fun showFailure(model: Model, state: DemoState.VerificationRejected): String {
        model.addAttribute("config", failureConfig)
        val headline = when (state.reason) {
            VerificationRejectionReason.HCP_TOO_HIGH -> failureConfig.headline.hcpTooHigh
            null -> failureConfig.headline.general
        }
        model.addAttribute("failureHeadline", headline)
        return "failure"
    }

    @PostMapping("/demo")
    fun advance(
        @RequestParam session: String?,
        @RequestParam(required = false) selection: String?
    ): String {
        if (session == null) {
            val newSession = demoFlowService.createSession()
            return "redirect:/demo?session=${newSession.id}"
        }
        val current = demoFlowService.getSession(session) ?: return "redirect:/demo"
        if (current.state == DemoState.Booking) {
            val selected = selection ?: return "redirect:/demo?session=$session"
            val result = verificationService.start(session, selected)
            if (result !is VerificationStartResult.Started) {
                return "redirect:/demo?session=$session"
            }
        }
        return "redirect:/demo?session=$session"
    }
}
