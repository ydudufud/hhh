package com.example.target_app

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.example.target/service")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "start" -> result.success(true)
                    else -> result.notImplemented()
                }
            }
    }
}
