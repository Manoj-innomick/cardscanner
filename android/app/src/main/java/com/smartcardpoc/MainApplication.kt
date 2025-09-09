package com.smartcardpoc

import android.app.Application
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.shell.MainReactPackage
import com.facebook.soloader.SoLoader
import java.util.Arrays
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.NativeModule
import com.facebook.react.uimanager.ViewManager

class MainApplication : Application(), ReactApplication {

    override val reactNativeHost: ReactNativeHost = object : ReactNativeHost(this) {
        override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

        override fun getPackages(): List<ReactPackage> {
            return Arrays.asList(
                MainReactPackage(),
                object : ReactPackage {
                    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
                        return listOf(CprScannerModule(reactContext))
                    }

                    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
                        return emptyList<ViewManager<*, *>>()
                    }
                }
            )
        }

        override fun getJSMainModuleName(): String = "index"
    }
    

    override fun onCreate() {
        super.onCreate()
        SoLoader.init(this, false)
    }

    // Override onBackPressed for the activity
    override fun onBackPressed() {
        val reactContext = mReactNativeHost.reactInstanceManager.currentReactContext
        if (reactContext != null) {
            val uiManager = reactContext.getNativeModule(UIManagerModule::class.java)
            if (uiManager != null && !uiManager.onBackPressed()) {
                super.onBackPressed()
            }
        } else {
            super.onBackPressed()
        }
    }
}