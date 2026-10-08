package com.example.qrbatch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * 把一个 QrItem（主 + 子）合成到一张大图：
 *   - QR 主项：上方正方形
 *   - 条形码主项：上方横向条
 *   - 子项：依次往下排
 *
 * 文本标签自动按宽度换行；图整体宽度根据最宽的码或最长标签自适应。
 */
object QrComposer {

    private const val PADDING = 32
    private const val GAP = 24
    private const val MAIN_CODE_WIDTH = 512   // QR/条形码横向目标宽度
    private const val CHILD_CODE_WIDTH = 384
    private const val MAX_LABEL_WIDTH = 720
    private const val LABEL_TEXT_SIZE = 28f
    private const val SUB_LABEL_TEXT_SIZE = 22f

    fun compose(item: QrItem): Bitmap {
        val mainBitmap = CodeBitmapFactory.generate(item.text, item.format, MAIN_CODE_WIDTH)
            ?: error("无法生成码: ${item.text}")
        val childBitmaps = item.children.map { child ->
            CodeBitmapFactory.generate(child.text, child.format, CHILD_CODE_WIDTH)
                ?: error("无法生成码: ${child.text}")
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = LABEL_TEXT_SIZE
            isFakeBoldText = true
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = SUB_LABEL_TEXT_SIZE
        }

        val mainLabelWidth = computeLabelWidth(item.text, titlePaint, mainBitmap.width)
        val childLabelWidths = item.children.mapIndexed { i, c ->
            computeLabelWidth("关联 ${i + 1}: ${c.text}", subtitlePaint, childBitmaps[i].width)
        }
        val maxChildLabelWidth = childLabelWidths.maxOrNull() ?: 0

        val width = maxOf(
            mainBitmap.width + PADDING * 2,
            childBitmaps.maxOfOrNull { it.width + PADDING * 2 } ?: 0,
            mainLabelWidth,
            maxChildLabelWidth
        )

        val mainLabel = buildLayout(item.text, titlePaint, width - PADDING * 2)
        val childLabels = item.children.mapIndexed { i, c ->
            buildLayout("关联 ${i + 1}: ${c.text}", subtitlePaint, width - PADDING * 2)
        }

        val totalHeight = PADDING +
            (mainBitmap.height + GAP + mainLabel.height + GAP) +
            childBitmaps.mapIndexed { i, bmp ->
                bmp.height + GAP + childLabels[i].height + GAP
            }.sum() +
            PADDING

        val result = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val cx = width / 2
        var y = PADDING

        // 主项
        canvas.drawBitmap(mainBitmap, cx - mainBitmap.width / 2f, y.toFloat(), null)
        y += mainBitmap.height + GAP
        canvas.save()
        canvas.translate(PADDING.toFloat(), y.toFloat())
        mainLabel.draw(canvas)
        canvas.restore()
        y += mainLabel.height + GAP

        // 子项
        childBitmaps.forEachIndexed { idx, bmp ->
            canvas.drawBitmap(bmp, cx - bmp.width / 2f, y.toFloat(), null)
            y += bmp.height + GAP
            canvas.save()
            canvas.translate(PADDING.toFloat(), y.toFloat())
            childLabels[idx].draw(canvas)
            canvas.restore()
            y += childLabels[idx].height + GAP
        }

        return result
    }

    private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.1f)
            .setIncludePad(false)
            .build()

    private fun computeLabelWidth(text: String, paint: Paint, codeWidth: Int): Int {
        val single = paint.measureText(text).toInt() + PADDING * 2
        if (single <= MAX_LABEL_WIDTH) return codeWidth + PADDING * 2
        return MAX_LABEL_WIDTH + PADDING * 2
    }
}