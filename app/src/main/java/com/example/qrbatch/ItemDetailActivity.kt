package com.example.qrbatch

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import com.google.zxing.integration.android.IntentIntegrator

/**
 * 关联子项详情页：只读主项，新增/扫码/删除子项。
 * 直接修改共享 [ItemStore]，主页始终看到最新数据。
 */
class ItemDetailActivity : Activity() {

    companion object {
        private const val REQUEST_CAMERA = 100
        const val EXTRA_INDEX = "index"
    }

    private lateinit var etMain: EditText
    private lateinit var etChild: EditText
    private lateinit var btnAddChild: Button
    private lateinit var btnLinkScan: Button
    private lateinit var lvChildren: ListView
    private lateinit var btnSave: Button

    private var mainIndex: Int = -1
    private val children: MutableList<QrItem> = mutableListOf()
    private lateinit var captureManager: CaptureManager
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_detail)

        etMain = findViewById(R.id.et_main)
        etChild = findViewById(R.id.et_child)
        btnAddChild = findViewById(R.id.btn_add_child)
        btnLinkScan = findViewById(R.id.btn_link_scan)
        lvChildren = findViewById(R.id.lv_children)
        btnSave = findViewById(R.id.btn_save)

        mainIndex = intent.getIntExtra(EXTRA_INDEX, -1)
        val parent = ItemStore.items.getOrNull(mainIndex)
        if (parent == null) { finish(); return }

        etMain.setText(parent.text)
        children.clear()
        children.addAll(parent.children)
        refreshAdapter()

        captureManager = CaptureManager(this)

        btnAddChild.setOnClickListener { addChildFromField() }

        lvChildren.setOnItemLongClickListener { _, _, position, _ ->
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("删除该子项？")
                .setMessage(children[position].text)
                .setPositiveButton("删除") { _, _ ->
                    children.removeAt(position)
                    refreshAdapter()
                    syncToStore()
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        btnLinkScan.setOnClickListener {
            BatchQrSaver.requestCameraPermission(this, REQUEST_CAMERA) {
                captureManager.startScan(prompt = "扫一扫关联到【${parent.text}】")
            }
        }

        btnSave.setOnClickListener {
            syncToStore()
            finish()
        }
    }

    private fun refreshAdapter() {
        val texts = children.map { it.text }
        if (!::adapter.isInitialized) {
            adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, texts)
            lvChildren.adapter = adapter
        } else {
            adapter.clear()
            adapter.addAll(texts)
            adapter.notifyDataSetChanged()
        }
    }

    private fun addChildFromField() {
        val text = etChild.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "请输入子项内容", Toast.LENGTH_SHORT).show()
            return
        }
        children.add(QrItem(text))
        etChild.text.clear()
        refreshAdapter()
        syncToStore()
    }

    private fun syncToStore() {
        val parent = ItemStore.items.getOrNull(mainIndex) ?: return
        parent.children.clear()
        parent.children.addAll(children)
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
            captureManager.startScan(prompt = "扫一扫关联到【${etMain.text}】")
        } else {
            Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != IntentIntegrator.REQUEST_CODE) return
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
        children.add(QrItem(result.text, format = result.format))
        refreshAdapter()
        syncToStore()
        Toast.makeText(this,
            if (children.count { it.text == result.text } > 1)
                "已添加（含重复）：${result.text}"
            else
                "已添加：${result.text}",
            Toast.LENGTH_SHORT).show()
    }
}
