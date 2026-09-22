package de.antonlorani.eudi.golfmembership.verifyhcp.oid4vp

fun interface AuthorizationResponseDecodingService {
    fun decode(response: String): AuthorizationResponseDecodingResult
}

sealed interface AuthorizationResponseDecodingResult {
    data class Decoded(val response: DecryptedAuthorizationResponse) : AuthorizationResponseDecodingResult
    data class Invalid(val error: AuthorizationResponseDecodingError) : AuthorizationResponseDecodingResult
}

enum class AuthorizationResponseDecodingError {
    MALFORMED_RESPONSE,
    UNSUPPORTED_ENCRYPTION,
    UNKNOWN_ENCRYPTION_KEY,
    DECRYPTION_FAILED,
    INVALID_PAYLOAD,
}
