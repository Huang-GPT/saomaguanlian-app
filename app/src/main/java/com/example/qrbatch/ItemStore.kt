package com.example.qrbatch

/**
 * 跨 Activity 共享的主项数据模型。
 * 主页 onCreate 中 init / onDestroy 中 clear。
 */
object ItemStore {
    val items: MutableList<QrItem> = mutableListOf()
    var initialized: Boolean = false
}
