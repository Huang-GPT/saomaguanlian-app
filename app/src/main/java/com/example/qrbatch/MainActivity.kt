package com.example.qrbatch

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.integration.android.IntentIntegrator
class MainActivity : Activity() {

    companion object {
        private const val REQUEST_CAMERA = 100
        private const val REQUEST_DETAIL = 200
    }

    private lateinit var btnScan: Button
    private lateinit var btnAdd: Button
    private lateinit var btnGenerate: Button
    private lateinit var etInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyView: TextView

    private val items: MutableList<QrItem> get() = ItemStore.items
    private val expanded = mutableSetOf<Int>()
    private val rows = mutableListOf<RowItem>()
    private lateinit var adapter: FlatAdapter

    private lateinit var captureManager: CaptureManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnScan = findViewById(R.id.btn_scan)
        btnAdd = findViewById(R.id.btn_add)
        btnGenerate = findViewById(R.id.btn_generate)
        etInput = findViewById(R.id.et_input)
        recyclerView = findViewById(R.id.rv_items)
        progress = findViewById(R.id.progress)
        emptyView = findViewById(R.id.tv_empty)

        adapter = FlatAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.itemAnimator = null

        attachSwipeHandler()

        captureManager = CaptureManager(this)

        btnScan.setOnClickListener {
            BatchQrSaver.requestCameraPermission(this, REQUEST_CAMERA) {
                captureManager.startScan()
            }
        }
        btnAdd.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            items.add(QrItem(text))
            etInput.text.clear()
            rebuildRows()
        }
        btnGenerate.setOnClickListener {
            if (items.isEmpty()) {
                Toast.makeText(this, "列表为空，请先扫码或输入内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            progress.visibility = View.VISIBLE
            btnGenerate.isEnabled = false
            try {
                val uris = BatchQrSaver.saveItemsToGallery(this, items)
                progress.visibility = View.GONE
                btnGenerate.isEnabled = true
                if (uris.isNotEmpty()) {
                    val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        "相册 / Pictures/QRBatch/" else "相册 Pictures/QRBatch/"
                    Toast.makeText(this,
                        "已生成 ${uris.size} 张合成二维码，保存到$folder",
                        Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "生成失败，请查看日志", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                progress.visibility = View.GONE
                btnGenerate.isEnabled = true
                Toast.makeText(this, "出错：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun attachSwipeHandler() {
        // 自实现左滑显示"删除"按钮：滑动期间 row 平移露出右侧按钮，
        // 松手后保持显示；点击"删除"才删除；点击空白区域或再次左滑回弹。
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var horizontal = false
        var activeVh: RecyclerView.ViewHolder? = null

        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = e.x
                        downY = e.y
                        horizontal = false
                        val child = rv.findChildViewUnder(e.x, e.y)
                        activeVh = if (child != null) rv.getChildViewHolder(child) else null
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = e.x - downX
                        val dy = e.y - downY
                        if (!horizontal) {
                            if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) {
                                horizontal = kotlin.math.abs(dx) > kotlin.math.abs(dy) && dx < 0
                                if (horizontal) {
                                    val vh = activeVh
                                    val pos = vh?.bindingAdapterPosition ?: -1
                                    val row = rows.getOrNull(pos)
                                    if (vh == null || row == null || !row.isHeader) {
                                        horizontal = false
                                        activeVh = null
                                        return false
                                    }
                                    rv.parent.requestDisallowInterceptTouchEvent(true)
                                } else {
                                    // 用户是垂直滚动，把控制权还给 RV
                                    activeVh = null
                                    return false
                                }
                            }
                        }
                        if (horizontal && activeVh != null) {
                            val maxDx = -activeVh!!.itemView.width.toFloat() * 0.4f
                            activeVh!!.itemView.translationX = dx.coerceIn(maxDx, 0f)
                            return true
                        }
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        if (horizontal && activeVh != null) {
                            val vh = activeVh!!
                            val revealThreshold = -vh.itemView.width * 0.25f
                            val target = if (vh.itemView.translationX <= revealThreshold) {
                                vh.itemView.width * 0.4f
                            } else 0f
                            vh.itemView.animate().translationX(-target).setDuration(160).start()
                            vh.itemView.findViewById<View>(R.id.btn_row_delete)?.visibility =
                                if (target > 0) View.VISIBLE else View.GONE
                        }
                        horizontal = false
                        activeVh = null
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: android.view.MotionEvent) {
                if (!horizontal || activeVh == null) return
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = e.x - downX
                        val maxDx = -activeVh!!.itemView.width.toFloat() * 0.4f
                        activeVh!!.itemView.translationX = dx.coerceIn(maxDx, 0f)
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        val vh = activeVh!!
                        val revealThreshold = -vh.itemView.width * 0.25f
                        val target = if (vh.itemView.translationX <= revealThreshold) {
                            vh.itemView.width * 0.4f
                        } else 0f
                        vh.itemView.animate().translationX(-target).setDuration(160).start()
                        vh.itemView.findViewById<View>(R.id.btn_row_delete)?.visibility =
                            if (target > 0) View.VISIBLE else View.GONE
                        horizontal = false
                        activeVh = null
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    for (i in 0 until rv.childCount) {
                        val child = rv.getChildAt(i) ?: continue
                        child.translationX = 0f
                        child.findViewById<View>(R.id.btn_row_delete)?.visibility = View.GONE
                    }
                }
            }
        })
    }

    private fun triggerDeleteConfirmation(vh: RecyclerView.ViewHolder) {
        val pos = vh.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return
        val row = rows.getOrNull(pos)
        if (row == null || !row.isHeader) return
        val mainIndex = row.mainIndex
        val item = items.getOrNull(mainIndex) ?: return
        val snapshotText = item.text
        val snapshotChildCount = item.children.size
        AlertDialog.Builder(this)
            .setTitle("删除该项？")
            .setMessage(snapshotText + if (snapshotChildCount > 0)
                "\n（含 $snapshotChildCount 个关联子项）" else "")
            .setCancelable(true)
            .setPositiveButton("删除") { _, _ ->
                val idx = items.indexOfFirst { it.text == snapshotText }
                if (idx >= 0) {
                    items.removeAt(idx)
                    expanded.remove(idx)
                    val remap = expanded.map { if (it > idx) it - 1 else it }.toSet()
                    expanded.clear(); expanded.addAll(remap)
                    rebuildRows()
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun rebuildRows() {
        rows.clear()
        items.forEachIndexed { idx, item ->
            rows.add(RowItem(idx, isHeader = true))
            if (expanded.contains(idx)) {
                item.children.forEachIndexed { cIdx, _ ->
                    rows.add(RowItem(idx, isHeader = false, childIndex = cIdx))
                }
            }
        }
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openDetail(position: Int, item: QrItem) {
        val intent = Intent(this, ItemDetailActivity::class.java).apply {
            putExtra(ItemDetailActivity.EXTRA_INDEX, position)
        }
        startActivityForResult(intent, REQUEST_DETAIL)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            captureManager.startScan()
        } else {
            Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            IntentIntegrator.REQUEST_CODE -> handleScanResult(resultCode, data)
            REQUEST_DETAIL -> rebuildRows()
        }
    }

    override fun onResume() {
        super.onResume()
        rebuildRows()
    }

    private fun handleScanResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            if (resultCode == Activity.RESULT_CANCELED)
                Toast.makeText(this, "已取消扫码", Toast.LENGTH_SHORT).show()
            return
        }
        val result = captureManager.handleResult(
            IntentIntegrator.REQUEST_CODE, resultCode, data
        )
        if (result == null || result.text.isBlank()) {
            Toast.makeText(this, "扫码结果为空", Toast.LENGTH_SHORT).show()
            return
        }
        items.add(QrItem(result.text, format = result.format))
        Toast.makeText(this,
            "已添加主项（${formatName(result.format)}）：${result.text}",
            Toast.LENGTH_SHORT).show()
        rebuildRows()
    }

    private fun formatName(format: com.google.zxing.BarcodeFormat): String {
        return when (format) {
            com.google.zxing.BarcodeFormat.QR_CODE -> "二维码"
            com.google.zxing.BarcodeFormat.EAN_13 -> "EAN-13"
            com.google.zxing.BarcodeFormat.EAN_8 -> "EAN-8"
            com.google.zxing.BarcodeFormat.UPC_A -> "UPC-A"
            com.google.zxing.BarcodeFormat.UPC_E -> "UPC-E"
            com.google.zxing.BarcodeFormat.CODE_128 -> "Code-128"
            com.google.zxing.BarcodeFormat.CODE_39 -> "Code-39"
            com.google.zxing.BarcodeFormat.CODE_93 -> "Code-93"
            com.google.zxing.BarcodeFormat.ITF -> "ITF"
            else -> format.toString()
        }
    }

    private inner class FlatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val inflater = LayoutInflater.from(this@MainActivity)

        override fun getItemCount(): Int = rows.size

        override fun getItemViewType(position: Int): Int =
            if (rows[position].isHeader) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layout = if (viewType == 0) R.layout.row_main_item else R.layout.row_child_item
            val v = inflater.inflate(layout, parent, false)
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = rows[position]
            if (row.isHeader) {
                val item = items[row.mainIndex]
                val titleView = holder.itemView.findViewById<TextView>(R.id.tv_title)
                val countView = holder.itemView.findViewById<TextView>(R.id.tv_count)
                val subtitle = holder.itemView.findViewById<TextView>(R.id.tv_subtitle)
                titleView.text = item.text
                countView.text = if (item.children.isEmpty()) "" else "${item.children.size} 个"
                subtitle.text = when {
                    item.children.isEmpty() -> "左滑删除 · 长按编辑 · 点击展开"
                    expanded.contains(row.mainIndex) -> "已展开 · 点击折叠"
                    else -> "点击展开查看子项"
                }
                val chevron = holder.itemView.findViewById<TextView>(R.id.tv_chevron)
                chevron.rotation = if (expanded.contains(row.mainIndex)) 90f else 0f
                holder.itemView.setOnClickListener {
                    if (expanded.contains(row.mainIndex))
                        expanded.remove(row.mainIndex)
                    else expanded.add(row.mainIndex)
                    rebuildRows()
                }
                holder.itemView.setOnLongClickListener {
                    openDetail(row.mainIndex, item)
                    true
                }
                val btnDelete = holder.itemView.findViewById<View>(R.id.btn_row_delete)
                btnDelete.setOnClickListener {
                    triggerDeleteConfirmation(holder)
                    holder.itemView.translationX = 0f
                    btnDelete.visibility = View.GONE
                }
            } else {
                val child = items[row.mainIndex].children[row.childIndex]
                holder.itemView.findViewById<TextView>(R.id.tv_child_text).text = child.text
                val groups = DuplicateDetector.detect(items[row.mainIndex].children)
                val group = groups.firstOrNull { row.childIndex in it.childPositions }
                holder.itemView.setBackgroundColor(
                    group?.color ?: android.graphics.Color.TRANSPARENT
                )
                holder.itemView.findViewById<TextView>(R.id.tv_child_delete).setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("删除该子项？")
                        .setMessage(child.text)
                        .setPositiveButton("删除") { _, _ ->
                            items[row.mainIndex].children.removeAt(row.childIndex)
                            rebuildRows()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
        }
    }
}
