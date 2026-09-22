package de.antonlorani.eudi.golfmembership.demo

import de.antonlorani.eudi.golfmembership.booking.BookingPageConfiguration
import de.antonlorani.eudi.golfmembership.failure.FailurePageConfiguration
import de.antonlorani.eudi.golfmembership.issuecredential.IssueCredentialPageConfiguration
import de.antonlorani.eudi.golfmembership.success.SuccessPageConfiguration
import de.antonlorani.eudi.golfmembership.verifyhcp.VerifyHcpPageConfiguration
import de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp.AuthorizationRequestService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

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
) {

    @GetMapping("/demo")
    fun demo(@RequestParam session: String?, model: Model): String {
        if (session == null) {
            model.addAttribute("config", issueCredentialConfig)
            return "issue-credential"
        }
        val demoSession = demoFlowService.getSession(session)
            ?: return "redirect:/demo"
        model.addAttribute("sessionId", session)
        return when (val state = demoSession.state) {
            DemoState.Booking -> showBooking(model)
            is DemoState.PendingVerification -> showPendingVerification(model, demoSession.id, state)
            is DemoState.VerificationAccepted -> showSuccess(model)
            is DemoState.VerificationRejected -> showFailure(model)
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
        return "verify-hcp"
    }

    private fun showSuccess(model: Model): String {
        model.addAttribute("config", successConfig)
        return "success"
    }

    private fun showFailure(model: Model): String {
        model.addAttribute("config", failureConfig)
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
