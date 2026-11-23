package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeUtils {

    private const val QR_SCHEME = "vitremote"
    private const val QR_HOST = "pair"

    fun buildPairingPayload(deviceCode: String): String =
        "$QR_SCHEME://$QR_HOST?code=${Uri.encode(deviceCode.trim().uppercase())}"

    fun parseDeviceCodeFromQr(rawValue: String): String? {
        val trimmed = rawValue.trim()
        if (trimmed.startsWith("$QR_SCHEME://", ignoreCase = true)) {
            val uri = Uri.parse(trimmed)
            val code = uri.getQueryParameter("code")?.trim()?.uppercase()
            if (!code.isNullOrBlank() && isValidDeviceCodeFormat(code)) {
                return code
            }
        }
        val normalized = trimmed.uppercase()
        return if (isValidDeviceCodeFormat(normalized)) normalized else null
    }

    fun isValidDeviceCodeFormat(code: String): Boolean =
        code.matches(Regex("^[A-Z0-9_\\-]{4,32}$"))

    fun generateQrBitmap(
        content: String,
        sizePx: Int = 512,
        darkColor: Int = Color.BLACK,
        lightColor: Int = Color.WHITE
    ): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                val offset = y * sizePx
                for (x in 0 until sizePx) {
                    pixels[offset + x] = if (matrix.get(x, y)) darkColor else lightColor
                }
            }
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            }
        } catch (_: Exception) {
            null
        }
    }
}
