package com.example.qrbatch

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.common.BitMatrix
import java.util.EnumMap

/**
 * 统一生成 QR / 一维条形码 Bitmap。
 *
 * - QR：正方形，边长 size
 * - 一维条形码：宽 size、高 size/2 像素的横条
 */
object CodeBitmapFactory {

    /**
     * 按指定码制生成 Bitmap。失败返回 null。
     */
    fun generate(content: String, format: BarcodeFormat, size: Int = 512): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val (w, h) = dimensionsFor(format, size)
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.MARGIN, 2)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                if (format == BarcodeFormat.QR_CODE) {
                    put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                }
            }
            val matrix = MultiFormatWriter().encode(content, format, w, h, hints)
            bitmapFromMatrix(matrix)
        } catch (e: Exception) {
            null
        }
    }

    /** 兼容旧调用：默认按 QR 生成正方形。 */
    fun generate(content: String, size: Int = 512): Bitmap? =
        generate(content, BarcodeFormat.QR_CODE, size)

    fun generateBatch(contents: List<String>, size: Int = 512): List<Bitmap> {
        return contents.mapNotNull { generate(it, size) }
    }

    /** 判断 [format] 是否为一维条形码。 */
    fun isOneDimensional(format: BarcodeFormat): Boolean = format != BarcodeFormat.QR_CODE

    private fun dimensionsFor(format: BarcodeFormat, size: Int): Pair<Int, Int> {
        return if (format == BarcodeFormat.QR_CODE) Pair(size, size)
        else Pair(size, (size * 0.45f).toInt().coerceAtLeast(80))
    }

    private fun bitmapFromMatrix(matrix: BitMatrix): Bitmap {
        val w = matrix.width
        val h = matrix.height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        for (x in 0 until w) {
            for (y in 0 until h) {
                val dark = matrix.get(x, y)
                bmp.setPixel(x, y, if (dark) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}