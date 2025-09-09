package com.smartcardpoc;

import android.content.Context;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.abc.terminalfactory.UsbSmartCard;
import javax.smartcardio.CardTerminal;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;

public class CprScanner {
    private final Context context;
    private final ReactApplicationContext reactContext;
    private String scannerStatus = "Please connect the Futronic FS82HD scanner.";
    private FutronicSdkWrapper sdkWrapper;
    private CardTerminal cardTerminal;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> refreshJob;
    private boolean isCardPresentState = false;

    public CprScanner(Context context, ReactApplicationContext reactContext) {
        this.context = context;
        this.reactContext = reactContext;

        android.util.Log.d("CprScanner", "Scanner initializing...");
        updateStatus("Scanner initialization started.");
        try {
            UsbSmartCard usbSmartCard = UsbSmartCard.getInstance(context);
            if (usbSmartCard == null) {
                android.util.Log.e("CprScanner", "USB SmartCard instance is null");
                updateStatus("USB Host not supported or not initialized.");
            } else {
                List<CardTerminal> terminals = usbSmartCard.terminals().list();
                if (terminals == null || terminals.isEmpty()) {
                    android.util.Log.e("CprScanner", "No card terminals detected");
                    updateStatus("USB Host not supported or no terminals detected.");
                } else {
                    android.util.Log.d("CprScanner", "USB Host supported with " + terminals.size() + " terminals");
                }
            }
        } catch (Exception e) {
            android.util.Log.e("CprScanner", "Error checking USB host: " + e.getMessage());
            updateStatus("USB Host check failed: " + e.getMessage());
        }
    }

    public boolean isScannerReady() {
        return sdkWrapper != null && sdkWrapper.isReaderOpen();
    }

    private void refreshScanner() {
        try {
            if (!isScannerReady()) {
                List<CardTerminal> terminals = UsbSmartCard.terminals().list();
                if (!terminals.isEmpty()) {
                    cardTerminal = terminals.get(0);
                    sdkWrapper = new FutronicSdkWrapper();
                    if (sdkWrapper.open(cardTerminal)) {
                        updateStatus("Scanner connected. Please insert a card.");
                    }
                } else {
                    cardTerminal = null;
                    sdkWrapper = null;
                    updateStatus("Scanner not found. Please connect it.");
                    return;
                }
            }

            boolean cardCurrentlyPresent = cardTerminal != null && cardTerminal.isCardPresent();
            if (cardCurrentlyPresent && !isCardPresentState) {
                isCardPresentState = true;
                executor.submit(() -> {
                    updateStatus("Card inserted. Reading...");
                    CprData data = readCard();
                    updateCprData(data);
                });
            } else if (!cardCurrentlyPresent && isCardPresentState) {
                isCardPresentState = false;
                updateCprData(null);
                updateStatus("Scanner connected. Please insert a card.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            updateStatus("Error: " + e.getMessage());
        }
    }

    private CprData readCard() {
        if (!isScannerReady()) {
            updateStatus("Error: Scanner is not ready.");
            return null;
        }
        try {
            updateStatus("Step 1: Selecting card applet...");
            String selectAppletCommand = "00A404000DD4990000010101000100000001";
            byte[] response = sdkWrapper.transmit(selectAppletCommand);
            if (!isSuccess(response)) {
                throw new Exception("Failed to select applet: " + bytesToHex(response));
            }

            ByteArrayOutputStream allData = new ByteArrayOutputStream();
            allData.write(readFileInChunks("0001", "0101").toByteArray());
            updateStatus("Reading Personal Data (0101/0001)...");
            allData.write(readFileInChunks("0002", "0102").toByteArray());
            updateStatus("Reading Address Data (0102/0002)...");
            allData.write(readFileInChunks("0003", "0103").toByteArray());
            updateStatus("Reading Photo Data (0103/0003)...");
            allData.write(readFileInChunks("0004", "0104").toByteArray());
            updateStatus("Reading Signature Data (0104/0004)...");
            allData.write(readFileInChunks("0001", "0105").toByteArray());
            updateStatus("Reading Fingerprint 1 (0105/0001)...");
            allData.write(readFileInChunks("0002", "0105").toByteArray());
            updateStatus("Reading Fingerprint 2 (0105/0002)...");
            allData.write(readFileInChunks("0005", "0106").toByteArray());
            updateStatus("Reading Visa/Permit Data (0106/0005)...");
            allData.write(readFileInChunks("0006", "0107").toByteArray());
            updateStatus("Reading Employment Data (0107/0006)...");

            byte[] completeData = allData.toByteArray();
            if (completeData.length == 0) {
                throw new Exception("Failed to read any data from the card.");
            }

            return parseCprData(completeData);
        } catch (Exception e) {
            updateStatus("Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private ByteArrayOutputStream readFileInChunks(String fileId, String directoryId) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            updateStatus("Selecting Directory " + directoryId + "...");
            String selectDfCommand = "00A4000C02" + directoryId;
            byte[] dfResponse = sdkWrapper.transmit(selectDfCommand);
            if (!isSuccess(dfResponse)) {
                updateStatus("Warning: Could not select directory " + directoryId + ". Skipping.");
                return outputStream;
            }

            updateStatus("Selecting File " + fileId + "...");
            String selectFileCommand = "00A4020C02" + fileId;
            byte[] fileResponse = sdkWrapper.transmit(selectFileCommand);
            if (!isSuccess(fileResponse)) {
                updateStatus("Warning: Could not select file " + fileId + " in dir " + directoryId + ". Skipping.");
                return outputStream;
            }

            int offset = 0;
            int chunkSize = 255;
            while (true) {
                int p1 = (offset >> 8) & 0xFF;
                int p2 = offset & 0xFF;
                String readFileCommand = String.format("00B0%02X%02X%02X", p1, p2, chunkSize);

                byte[] readResponse = sdkWrapper.transmit(readFileCommand);

                if (readResponse != null && readResponse.length > 2) {
                    byte[] dataChunk = new byte[readResponse.length - 2];
                    System.arraycopy(readResponse, 0, dataChunk, 0, dataChunk.length);
                    if (dataChunk.length == 0) break;
                    outputStream.write(dataChunk, 0, dataChunk.length);
                    offset += dataChunk.length;
                    if (dataChunk.length < chunkSize) break;
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return outputStream;
    }

    private String getField(byte[] data, int start, int length) {
        if (start + length > data.length) return "";
        byte[] fieldBytes = new byte[length];
        System.arraycopy(data, start, fieldBytes, 0, length);
        String field = new String(fieldBytes, StandardCharsets.UTF_8);
        return field.replaceAll("\\p{C}", "").trim();
    }

    private String getRemainingField(byte[] data, int start) {
        if (start >= data.length) return "";
        byte[] remainingBytes = new byte[data.length - start];
        System.arraycopy(data, start, remainingBytes, 0, remainingBytes.length);
        String remaining = new String(remainingBytes, StandardCharsets.UTF_8);
        return remaining.replaceAll("\\p{C}", "").trim();
    }

    private CprData parseCprData(byte[] data) {
        try {
            String cprNumber = getField(data, 0, 9);
            String fullName = getField(data, 9, 50);
            String remainingData = getRemainingField(data, 59);

            return new CprData(
                cprNumber.isEmpty() ? "Parse Error" : cprNumber,
                fullName.isEmpty() ? "Parse Error" : fullName,
                "RAW: " + remainingData,
                "---",
                "---",
                "---"
            );
        } catch (Exception e) {
            e.printStackTrace();
            return new CprData("Parsing Failed", e.getMessage() != null ? e.getMessage() : "Unknown parsing error", "", "", "", "");
        }
    }

    private boolean isSuccess(byte[] response) {
        return response != null && response.length >= 2 &&
                response[response.length - 2] == (byte) 0x90 && response[response.length - 1] == (byte) 0x00;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private void updateStatus(String status) {
        this.scannerStatus = status;
        sendEvent("ScannerStatus", status);
    }

    private void updateCprData(CprData data) {
        WritableMap map = Arguments.createMap();
        if (data != null) {
            map.putString("cprNumber", data.cprNumber);
            map.putString("fullName", data.fullName);
            map.putString("dateOfBirth", data.dateOfBirth);
            map.putString("nationality", data.nationality);
            map.putString("gender", data.gender);
            map.putString("cardExpiryDate", data.cardExpiryDate);
        }
        sendEvent("CprData", map);
    }

    private void sendEvent(String eventName, Object params) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
    }

    public void resume() {
        UsbSmartCard.getInstance(context).onResume();
        if (refreshJob != null && !refreshJob.isDone()) {
            refreshJob.cancel(true);
        }
        refreshJob = executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                refreshScanner();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public void pause() {
        if (refreshJob != null) {
            refreshJob.cancel(true);
            refreshJob = null;
        }
        if (sdkWrapper != null) {
            sdkWrapper.close();
            sdkWrapper = null;
        }
        UsbSmartCard.getInstance(context).onStop();
    }

    public void destroy() {
        pause();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}