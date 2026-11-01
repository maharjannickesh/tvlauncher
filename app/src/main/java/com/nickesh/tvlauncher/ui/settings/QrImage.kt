package com.nickesh.tvlauncher.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.nickesh.tvlauncher.ui.theme.dpx

/** A QR code for [text], [sizePx] design pixels square, dark modules on white with a small quiet zone. */
@Composable
fun QrImage(text: String, sizePx: Int, modifier: Modifier = Modifier) {
    val bitmap = remember(text, sizePx) { qrBitmap(text, sizePx) }
    Image(
        bitmap = bitmap,
        contentDescription = "QR code for the connect page",
        modifier = modifier.size(sizePx.dpx()),
        filterQuality = FilterQuality.None,
    )
}

private fun qrBitmap(text: String, sizePx: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(EncodeHintType.MARGIN to 2))
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h) { i -> if (matrix.get(i % w, i / w)) DARK else LIGHT }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private const val DARK = 0xFF0D1020.toInt()
private const val LIGHT = 0xFFFFFFFF.toInt()
