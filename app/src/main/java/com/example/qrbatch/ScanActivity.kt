package com.example.qrbatch

import android.graphics.Rect
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.camera.CameraSettings
import kotlin.math.roundToInt

/**
 * 自定义扫码页：
 *  - 右上角闪光灯开关（仅手动）
 *  - 点击取景框手动对焦
 *  - 默认开启连续对焦 + 曝光/测光，解决"扫码对不上焦、画面糊"的问题
 */
class ScanActivity : CaptureActivity() {

    private companion object {
        const val TAG = "ScanActivity"
        /** 焦点区域半径（Camera.Parameters 坐标系 -1000..1000） */
        const val FOCUS_RADIUS = 300
        /** 焦点权重 */
        const val FOCUS_WEIGHT = 1000
        const val FOCUS_FEEDBACK_MS = 900L
    }

    private var torchOn = false
    private lateinit var btnTorch: ImageButton
    private lateinit var tvHint: TextView
    private var hintHideRunnable: Runnable? = null

    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_scan)
        val view = findViewById<DecoratedBarcodeView>(R.id.zxing_barcode_scanner)
        btnTorch = findViewById(R.id.btn_torch)
        tvHint = findViewById(R.id.tv_focus_hint)

        setupCameraSettings(view)
        setupTorch(view)
        setupTapToFocus(view)
        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tvHint.visibility = View.GONE
    }

    /**
     * 打开连续对焦 + 曝光 + 测光。
     */
    private fun setupCameraSettings(view: DecoratedBarcodeView) {
        try {
            val settings = view.barcodeView.getCameraInstance().cameraSettings
            settings.setAutoFocusEnabled(true)
            settings.setContinuousFocusEnabled(true)
            settings.focusMode = CameraSettings.FocusMode.CONTINUOUS
            settings.setExposureEnabled(true)
            settings.setMeteringEnabled(true)
        } catch (e: Exception) {
            Log.w(TAG, "设置相机参数失败", e)
        }
    }

    private fun setupTorch(view: DecoratedBarcodeView) {
        btnTorch.setOnClickListener {
            torchOn = !torchOn
            try {
                view.barcodeView.setTorch(torchOn)
            } catch (e: Exception) {
                Log.w(TAG, "切换闪光灯失败", e)
                torchOn = false
            }
            btnTorch.setImageResource(
                if (torchOn) R.drawable.ic_torch_on else R.drawable.ic_torch_off
            )
            btnTorch.contentDescription =
                getString(if (torchOn) R.string.torch_on else R.string.torch_off)
        }
    }

    /**
     * 点击取景框手动对焦。
     */
    private fun setupTapToFocus(view: DecoratedBarcodeView) {
        view.barcodeView.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                performTapFocus(view, event.x, event.y)
                v.performClick()
            }
            true
        }
    }

    private fun performTapFocus(view: DecoratedBarcodeView, x: Float, y: Float) {
        try {
            val preview = view.barcodeView
            val area = computeFocusArea(preview, x, y)
            preview.changeCameraParameters { params ->
                if (area != null) {
                    if (params.maxNumFocusAreas > 0) {
                        params.focusAreas = listOf(Camera.Area(area, FOCUS_WEIGHT))
                    }
                    if (params.maxNumMeteringAreas > 0) {
                        params.meteringAreas = listOf(Camera.Area(area, FOCUS_WEIGHT))
                    }
                }
                params
            }
            triggerAutoFocus(preview)
            showFocusFeedback()
        } catch (e: Exception) {
            Log.w(TAG, "手动对焦失败", e)
        }
    }

    /**
     * 把触屏坐标换算为 Camera.Parameters 的焦点区域（-1000..1000）。
     *
     * 借助库自身的两个矩形做线性映射：
     *   framingRect         —— 取景框在屏幕上的位置
     *   previewFramingRect  —— 同一取景框在相机预览像素中的位置
     * 因此得到的点与用户实际看到的内容一致。
     */
    private fun computeFocusArea(preview: CameraPreview, x: Float, y: Float): Rect? {
        try {
            val previewSize = preview.previewSize ?: return null
            val framing = preview.framingRect ?: return null
            val previewFraming = preview.previewFramingRect ?: return null
            if (framing.width() <= 0 || framing.height() <= 0) return null
            if (previewFraming.width() <= 0 || previewFraming.height() <= 0) return null
            if (!framing.contains(x.toInt(), y.toInt())) return null

            // 触点在取景框内的相对位置 → 预览像素坐标
            val px = previewFraming.left +
                (x - framing.left) * previewFraming.width() / framing.width().toFloat()
            val py = previewFraming.top +
                (y - framing.top) * previewFraming.height() / framing.height().toFloat()

            // 归一化到相机预览（传感器）坐标系，再映射到 -1000..1000
            val nx = (px / previewSize.width).coerceIn(0f, 1f)
            val ny = (py / previewSize.height).coerceIn(0f, 1f)
            val cx = (nx * 2000 - 1000).roundToInt()
            val cy = (ny * 2000 - 1000).roundToInt()

            return Rect(
                (cx - FOCUS_RADIUS).coerceIn(-1000, 1000),
                (cy - FOCUS_RADIUS).coerceIn(-1000, 1000),
                (cx + FOCUS_RADIUS).coerceIn(-1000, 1000),
                (cy + FOCUS_RADIUS).coerceIn(-1000, 1000)
            )
        } catch (e: Exception) {
            Log.w(TAG, "计算焦点区域失败", e)
            return null
        }
    }

    /**
     * 触发一次自动对焦。
     *
     * CameraManager.camera 是私有字段，CameraThread.enqueue 是 protected，
     * 这里用反射取出，并**把任务派发到相机线程**执行，避免与预览线程竞争导致崩溃。
     * 任何一步失败都静默忽略（连续对焦仍然生效）。
     */
    private fun triggerAutoFocus(preview: CameraPreview) {
        try {
            val instance = preview.getCameraInstance()
            val threadField = instance.javaClass.getDeclaredField("cameraThread")
            threadField.isAccessible = true
            val cameraThread = threadField.get(instance) ?: return
            val enqueue = cameraThread.javaClass
                .getDeclaredMethod("enqueue", Runnable::class.java)
            enqueue.isAccessible = true
            enqueue.invoke(cameraThread, Runnable {
                try {
                    val camera = reflectCamera(instance) ?: return@Runnable
                    val modes = camera.parameters.supportedFocusModes ?: return@Runnable
                    if (!modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) return@Runnable
                    camera.autoFocus { _, _ -> }
                } catch (e: Throwable) {
                    Log.w(TAG, "autoFocus 失败（忽略）", e)
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "触发自动对焦失败（忽略）", e)
        }
    }

    /** 通过反射取出 CameraManager 持有的 Camera 实例。 */
    private fun reflectCamera(instance: Any): Camera? {
        val getManager = instance.javaClass.getDeclaredMethod("getCameraManager")
        getManager.isAccessible = true
        val manager = getManager.invoke(instance) ?: return null
        val cameraField = manager.javaClass.getDeclaredField("camera")
        cameraField.isAccessible = true
        return cameraField.get(manager) as? Camera
    }

    private fun showFocusFeedback() {
        tvHint.visibility = View.VISIBLE
        tvHint.alpha = 1f
        hintHideRunnable?.let { tvHint.removeCallbacks(it) }
        val r = Runnable {
            tvHint.animate().alpha(0f).setDuration(300).withEndAction {
                tvHint.visibility = View.GONE
            }.start()
        }
        hintHideRunnable = r
        tvHint.postDelayed(r, FOCUS_FEEDBACK_MS)
    }
}
