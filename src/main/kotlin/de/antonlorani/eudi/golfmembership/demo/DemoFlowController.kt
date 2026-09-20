package de.antonlorani.eudi.golfmembership.demo

import de.antonlorani.eudi.golfmembership.booking.BookingPageConfiguration
import de.antonlorani.eudi.golfmembership.failure.FailurePageConfiguration
import de.antonlorani.eudi.golfmembership.issuecredential.IssueCredentialPageConfiguration
import de.antonlorani.eudi.golfmembership.success.SuccessPageConfiguration
import de.antonlorani.eudi.golfmembership.verifyhcp.VerifyHcpPageConfiguration
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
    private val failureConfig: FailurePageConfiguration
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
        return when (demoSession.step) {
            DemoStep.BOOKING -> {
                model.addAttribute("config", bookingConfig)
                "booking"
            }
            DemoStep.VERIFY_HCP -> {
                model.addAttribute("config", verifyHcpConfig)
                "verify-hcp"
            }
            DemoStep.SUCCESS -> {
                model.addAttribute("config", successConfig)
                "success"
            }
            DemoStep.FAILURE -> {
                model.addAttribute("config", failureConfig)
                "failure"
            }
        }
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
        demoFlowService.advance(session, selection)
        return "redirect:/demo?session=$session"
    }
}
