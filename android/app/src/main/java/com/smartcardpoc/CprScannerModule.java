package com.smartcardpoc;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import java.util.Collections;
import java.util.List;

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
        if (cprScanner != null) {
            cprScanner.resume();
            promise.resolve("Initialized and scanner resumed");
        } else {
            promise.reject("InitializationError", "Scanner instance is null");
        }
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