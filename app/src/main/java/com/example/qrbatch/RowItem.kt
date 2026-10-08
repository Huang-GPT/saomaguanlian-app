package com.example.qrbatch

/**
 * 列表 adapter 用的展开/折叠 UI 模型。
 * mainIndex = 主项在原始列表中的下标；isHeader = true 时显示主项。
 */
data class RowItem(
    val mainIndex: Int,
    val isHeader: Boolean,
    val childIndex: Int = -1
)
