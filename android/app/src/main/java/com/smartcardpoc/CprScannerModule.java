package com.smartcardpoc;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ViewManager;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

public class CprScannerModule extends ReactContextBaseJavaModule {
    private CprScanner cprScanner;

    public CprScannerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.cprScanner = new CprScanner(reactContext.getBaseContext(), reactContext);
    }

    @Override
    public String getName() {
        return "CprScanner";
    }

    @ReactMethod
    public void initialize(Promise promise) {
        promise.resolve("Initialized");
    }

    @ReactMethod
    public void resume() {
        if (cprScanner != null) {
            cprScanner.resume();
        }
    }

    @ReactMethod
    public void pause() {
        if (cprScanner != null) {
            cprScanner.pause();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (cprScanner != null) {
            cprScanner.destroy();
            cprScanner = null;
        }
    }
}

class CprScannerPackage implements ReactPackage {
    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        List<NativeModule> modules = new ArrayList<>();
        modules.add(new CprScannerModule(reactContext));
        return modules;
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        return Collections.emptyList();
    }
}