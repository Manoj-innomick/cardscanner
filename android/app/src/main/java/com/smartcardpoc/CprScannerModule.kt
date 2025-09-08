package com.smartcardpoc

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.LifecycleEventListener
import android.util.Log

class CprScannerModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

    private val reactContext: ReactApplicationContext = reactContext
    private var cprScanner: CprScanner? = null
    private var isInitialized = false

    init {
        Log.d("CprScannerModule", "Module initialized")
        reactContext.addLifecycleEventListener(this)
    }

    override fun getName(): String = "CprScanner"

    @ReactMethod
    fun startScanning() {
        Log.d("CprScannerModule", "startScanning called")
        cprScanner?.resume()
    }

    @ReactMethod
    fun stopScanning() {
        Log.d("CprScannerModule", "stopScanning called")
        cprScanner?.pause()
    }

    override fun onHostResume() {
        Log.d("CprScannerModule", "onHostResume")
        try {
            if (!isInitialized) {
                cprScanner = CprScanner(reactContext) // Instantiate here
                               isInitialized = true
            }
        } catch (e: Exception) {
            Log.e("CprScannerModule", "Error in onHostResume: ${e.message}", e)
        }
    }

    override fun onHostPause() {
        Log.d("CprScannerModule", "onHostPause")
        cprScanner?.pause()
    }

    override fun onHostDestroy() {
        Log.d("CprScannerModule", "onHostDestroy")
        cprScanner?.pause()
        cprScanner = null
        reactContext.removeLifecycleEventListener(this)
    }
}