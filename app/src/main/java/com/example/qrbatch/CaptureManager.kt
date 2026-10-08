package com.example.qrbatch

import android.content.Intent
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator

/**
 * 扫码结果：包含内容 + 识别到的码制。
 */
data class ScanResult(val text: String, val format: BarcodeFormat)

/**
 * 摄像头扫码管理器（基于 ZXing-Android-Embedded）。
 * 支持 QR 与常见一维条形码（EAN-13、Code-128、Code-39、UPC-A 等）。
 */
class CaptureManager(private val activity: android.app.Activity) {

    private val requestCode: Int = IntentIntegrator.REQUEST_CODE

    fun getRequestCode(): Int = requestCode

    /**
     * 启动扫码。默认支持 QR + 一维条形码。
     */
    fun startScan(prompt: String = "将二维码/条形码对准取景框") {
        try {
            val integrator = IntentIntegrator(activity)
            integrator.setOrientationLocked(true)
            integrator.setPrompt(prompt)
            integrator.setBeepEnabled(true)
            integrator.setBarcodeImageEnabled(false)
            integrator.setDesiredBarcodeFormats(
                IntentIntegrator.QR_CODE,
                IntentIntegrator.EAN_13,
                IntentIntegrator.EAN_8,
                IntentIntegrator.UPC_A,
                IntentIntegrator.UPC_E,
                IntentIntegrator.CODE_39,
                IntentIntegrator.CODE_93,
                IntentIntegrator.CODE_128,
                IntentIntegrator.ITF,
                IntentIntegrator.RSS_14,
                IntentIntegrator.RSS_EXPANDED
            )
            integrator.initiateScan()
        } catch (e: Exception) {
            Log.e("CaptureManager", "启动扫码失败", e)
        }
    }

    fun handleResult(requestCode: Int, resultCode: Int, intent: Intent?): ScanResult? {
        val result = IntentIntegrator.parseActivityResult(
            IntentIntegrator.REQUEST_CODE, resultCode, intent
        )
        if (result == null) {
            Log.w("CaptureManager",
                "扫码结果为空 requestCode=$requestCode resultCode=$resultCode")
            return null
        }
        val formatName = result.formatName ?: "QR_CODE"
        val format = runCatching { BarcodeFormat.valueOf(formatName) }
            .getOrDefault(BarcodeFormat.QR_CODE)
        return ScanResult(result.contents.orEmpty(), format)
    }
}