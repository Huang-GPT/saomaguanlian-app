package com.example.qrbatch

import android.graphics.Color

object DuplicateDetector {

    /** 半透明背景色（不同重复组）。alpha 较低，避免盖住文字。 */
    val PALETTE = intArrayOf(
        Color.argb(60, 239, 68, 68),    // red
        Color.argb(60, 245, 158, 11),   // amber
        Color.argb(60, 16, 185, 129),   // emerald
        Color.argb(60, 59, 130, 246),   // blue
        Color.argb(60, 168, 85, 247),   // purple
        Color.argb(60, 236, 72, 153),   // pink
    )

    data class Group(
        val text: String,
        val color: Int,
        val childPositions: List<Int>
    )

    fun detect(children: List<QrItem>): List<Group> {
        val map = mutableMapOf<String, MutableList<Int>>()
        children.forEachIndexed { idx, c ->
            if (c.text.isNotBlank()) {
                map.getOrPut(c.text) { mutableListOf() }.add(idx)
            }
        }
        val groups = mutableListOf<Group>()
        val sorted = map.values.filter { it.size > 1 }.sortedBy { it.first() }
        sorted.forEachIndexed { i, positions ->
            groups.add(Group(
                text = children[positions.first()].text,
                color = PALETTE[i % PALETTE.size],
                childPositions = positions
            ))
        }
        return groups
    }
}
