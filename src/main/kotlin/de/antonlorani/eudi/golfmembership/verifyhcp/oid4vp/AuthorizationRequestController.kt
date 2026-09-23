package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import de.antonlorani.eudi.golfmembership.demo.DemoFlowService
import de.antonlorani.eudi.golfmembership.demo.DemoState
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthorizationRequestController(
    private val sessions: DemoFlowService,
    private val authorizationRequestService: AuthorizationRequestService,
) {
    @GetMapping("/oid4vp/requests/{id}")
    fun requestObject(@PathVariable id: String): ResponseEntity<String> {
        val pending = sessions.getSession(id)?.state as? DemoState.PendingVerification
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/oauth-authz-req+jwt"))
            .body(authorizationRequestService.createRequestObject(id, pending))
    }
}
