package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class PresentationResponseController(
    private val authorizationResponseService: AuthorizationResponseService,
    private val configuration: Oid4vpConfiguration,
    private val authorizationResponseDecodingService: AuthorizationResponseDecodingService,
) {
    @PostMapping("/oid4vp/responses/{id}")
    fun receive(
        @PathVariable id: String,
        @RequestParam(required = false) response: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Map<String, String>> {
        val authorizationResponse = when {
            response == null -> null
            else -> when (val decoding = authorizationResponseDecodingService.decode(response)) {
                is AuthorizationResponseDecodingResult.Decoded -> decoding.response
                is AuthorizationResponseDecodingResult.Invalid -> {
                    return ResponseEntity.badRequest().body(mapOf("error" to "invalid_response"))
                }
            }
        }
        val responseState = authorizationResponse?.state ?: state
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "invalid_request"))
        val vpToken = if (error == null) authorizationResponse?.vpToken else null
        val processed = authorizationResponseService.process(
            sessionId = id,
            state = responseState,
            vpToken = vpToken,
        )
        if (!processed) return ResponseEntity.badRequest().body(mapOf("error" to "invalid_request"))

        return ResponseEntity.ok(
            mapOf("redirect_uri" to "${configuration.verifierUrl}/demo?session=$id")
        )
    }
}
