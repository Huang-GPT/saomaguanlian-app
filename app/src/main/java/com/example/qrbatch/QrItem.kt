package com.example.qrbatch

import com.google.zxing.BarcodeFormat

data class QrItem(
    val text: String,
    val children: MutableList<QrItem> = mutableListOf(),
    val format: BarcodeFormat = BarcodeFormat.QR_CODE
) {
    val totalCount: Int get() = 1 + children.size

    /** 是否为 QR 系（正方形） */
    val isQr: Boolean get() = format == BarcodeFormat.QR_CODE
}