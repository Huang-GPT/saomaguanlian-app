package com.example.qrbatch

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 批量生成二维码并保存到设备存储。
 * - Android Q+: 通过 MediaStore 写入公共相册 Pictures/QRBatch/
 * - Android < Q: 写入公共 Pictures/QRBatch/ 然后扫描媒体文件
 */
object BatchQrSaver {

    private const val TAG = "BatchQrSaver"
    private const val FOLDER = "QRBatch"
    private const val QR_SIZE = 512
    private const val MIME_PNG = "image/png"
    private val RELATIVE_DIR: String = Environment.DIRECTORY_PICTURES + "/" + FOLDER

    /**
     * 生成二维码 Bitmap 列表（不落盘）
     */
    fun generateBitmaps(items: List<String>): List<Bitmap> {
        return CodeBitmapFactory.generateBatch(items, QR_SIZE)
    }

    /**
     * 保存到公共相册 Pictures/QRBatch/。返回保存成功的 Uri 列表。
     */
    fun saveAllToGallery(context: Context, items: List<String>): List<Uri> {
        val bitmaps = CodeBitmapFactory.generateBatch(items, QR_SIZE)
        if (bitmaps.size != items.size) {
            Log.w(TAG, "部分二维码生成失败：期望 ${items.size}，实际 ${bitmaps.size}")
        }
        val results = mutableListOf<Uri>()
        items.forEachIndexed { index, _ ->
            if (index >= bitmaps.size) {
                Log.e(TAG, "第 ${index + 1} 个二维码生成失败，跳过")
                return@forEachIndexed
            }
            val name = "QR_${index + 1}.png"
            try {
                val uri = saveBitmapToGallery(context, bitmaps[index], name)
                if (uri != null) results.add(uri)
            } catch (e: Exception) {
                Log.e(TAG, "保存第 ${index + 1} 张失败", e)
            }
        }
        return results
    }

    /**
     * 把 QrItem 列表逐项合成大图并保存到相册。
     */
    fun saveItemsToGallery(context: Context, items: List<QrItem>): List<Uri> {
        val results = mutableListOf<Uri>()
        items.forEachIndexed { index, item ->
            try {
                val bmp = QrComposer.compose(item)
                val name = "QR_${index + 1}.png"
                val uri = saveBitmapToGallery(context, bmp, name)
                if (uri != null) results.add(uri)
            } catch (e: Exception) {
                Log.e(TAG, "合成/保存第 ${index + 1} 张失败: ${item.text}", e)
            }
        }
        return results
    }

    /**
     * 保存到应用私有目录（无需运行时权限）。返回文件列表。
     */
    fun saveAllToFiles(context: Context, items: List<String>): List<File> {
        val dir = File(context.filesDir, FOLDER)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "无法创建目录 ${dir.absolutePath}")
            return emptyList()
        }
        val bitmaps = CodeBitmapFactory.generateBatch(items, QR_SIZE)
        val results = mutableListOf<File>()
        items.forEachIndexed { index, _ ->
            val file = File(dir, "QR_${index + 1}.png")
            try {
                if (index < bitmaps.size) {
                    file.outputStream().use { out ->
                        bitmaps[index].compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    results.add(file)
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存第 ${index + 1} 张失败", e)
            }
        }
        return results
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, name: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, bitmap, name)
        } else {
            saveViaLegacyFile(context, bitmap, name)
        }
    }

    private fun saveViaMediaStore(context: Context, bitmap: Bitmap, name: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_PNG)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return null
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore 写入失败", e)
            resolver.delete(uri, null, null)
            return null
        }
    }

    @Suppress("DEPRECATION")
    private fun saveViaLegacyFile(context: Context, bitmap: Bitmap, name: String): Uri? {
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dir = File(pictures, FOLDER)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "无法创建目录 ${dir.absolutePath}")
            return null
        }
        val file = File(dir, name)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入文件失败", e)
            return null
        }
        // 通知媒体扫描器
        context.sendBroadcast(android.content.Intent(
            android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
            Uri.fromFile(file)
        ))
        return Uri.fromFile(file)
    }

    fun hasCameraPermission(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= 23) {
            activity.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun requestCameraPermission(activity: Activity, code: Int,
                                 onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 23 &&
            activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), code)
        } else {
            onGranted()
        }
    }
}