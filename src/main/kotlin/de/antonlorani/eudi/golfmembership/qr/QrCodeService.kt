package de.antonlorani.eudi.golfmembership.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

@Service
class QrCodeService {
    fun dataUrl(content: String): String {
        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            SIZE,
            SIZE,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
            ),
        )
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until SIZE) {
            for (y in 0 until SIZE) {
                image.setRGB(x, y, if (matrix[x, y]) BLACK else WHITE)
            }
        }
        val png = ByteArrayOutputStream().use { output ->
            check(ImageIO.write(image, "png", output))
            output.toByteArray()
        }
        return "data:image/png;base64,${Base64.getEncoder().encodeToString(png)}"
    }

    private companion object {
        const val SIZE = 260
        const val BLACK = 0x000000
        const val WHITE = 0xFFFFFF
    }
}
