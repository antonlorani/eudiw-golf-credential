package de.antonlorani.eudi.golfmembership.qr

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class QrCodeServiceTest {
    @Test
    fun `creates a PNG data URL`() {
        val dataUrl = QrCodeService().dataUrl("openid4vp://authorize?request=test")
        val png = Base64.getDecoder().decode(dataUrl.substringAfter(','))

        assertTrue(dataUrl.startsWith("data:image/png;base64,"))
        assertTrue(png.take(8).toByteArray().contentEquals(PNG_SIGNATURE))
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
