package com.smartcardpoc

import android.content.Context
import com.abc.terminalfactory.UsbSmartCard
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import javax.smartcardio.CardTerminal
import android.util.Log

interface ScannerListener {
    fun onStatusChanged(status: String)
    fun onDataChanged(data: CprData?)
}

class CprScanner(private val context: Context) {

    private var listener: ScannerListener? = null
    fun setListener(l: ScannerListener) { listener = l }

    private val _initialStatus = "Please connect the Futronic FS82HD scanner."
    private var sdkWrapper: FutronicSdkWrapper? = null
    private var cardTerminal: CardTerminal? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scannerRefreshJob: Job? = null
    private var isCardPresentState: Boolean = false

    init {
        Log.d("CprScanner", "CprScanner init called")
        try {
            listener?.onStatusChanged(_initialStatus)
            val usbInstance = UsbSmartCard.getInstance(context)
            val usbManager = usbInstance.manager
            if (usbManager == null) {
                Log.w("CprScanner", "USB Host not supported on this device")
                listener?.onStatusChanged("USB Host not supported on this device.")
            } else {
                Log.d("CprScanner", "USB Manager loaded successfully")
            }
        } catch (e: Exception) {
            Log.e("CprScanner", "Init error: ${e.message}", e)
            listener?.onStatusChanged("Error during initialization: ${e.message}")
        }
    }

    fun isScannerReady(): Boolean = sdkWrapper?.isReaderOpen() ?: false

    private fun refreshScanner() {
        Log.d("CprScanner", "Refreshing scanner")
        try {
            if (!isScannerReady()) {
                val terminals = UsbSmartCard.terminals().list()
                if (terminals.isNotEmpty()) {
                    cardTerminal = terminals[0]
                    sdkWrapper = FutronicSdkWrapper()
                    if (sdkWrapper?.open(cardTerminal!!) == true) {
                        listener?.onStatusChanged("Scanner connected. Please insert a card.")
                    } else {
                        listener?.onStatusChanged("Failed to open scanner.")
                    }
                } else {
                    cardTerminal = null
                    sdkWrapper = null
                    listener?.onStatusChanged("Scanner not found. Please connect it.")
                    return
                }
            }

            val cardCurrentlyPresent = cardTerminal?.isCardPresent == true
            if (cardCurrentlyPresent && !isCardPresentState) {
                isCardPresentState = true
                scope.launch {
                    listener?.onStatusChanged("Card inserted. Reading...")
                    val data = readCard()
                    listener?.onDataChanged(data)
                }
            } else if (!cardCurrentlyPresent && isCardPresentState) {
                isCardPresentState = false
                listener?.onDataChanged(null)
                listener?.onStatusChanged("Scanner connected. Please insert a card.")
            }

        } catch (e: Exception) {
            Log.e("CprScanner", "Refresh error: ${e.message}", e)
            listener?.onStatusChanged("Error refreshing scanner: ${e.message}")
        }
    }

    private suspend fun readCard(): CprData? = withContext(Dispatchers.IO) {
        Log.d("CprScanner", "Reading card")
        if (!isScannerReady()) {
            listener?.onStatusChanged("Error: Scanner is not ready.")
            return@withContext null
        }
        try {
            listener?.onStatusChanged("Step 1: Selecting card applet...")
            val selectAppletCommand = "00A404000DD4990000010101000100000001"
            var response: ByteArray? = sdkWrapper?.transmit(selectAppletCommand)
            val appletResponse = response
            if (appletResponse?.isSuccess() != true) throw Exception("Failed to select applet: ${appletResponse?.toHexString()}")

            suspend fun readFileInChunks(fileId: String, directoryId: String): ByteArray {
                val outputStream = ByteArrayOutputStream()

                listener?.onStatusChanged("Selecting Directory $directoryId...")
                val selectDfCommand = "00A4000C02$directoryId"
                var innerResponse = sdkWrapper?.transmit(selectDfCommand)
                val dfResponse = innerResponse
                if (dfResponse?.isSuccess() != true) {
                    listener?.onStatusChanged("Warning: Could not select directory $directoryId. Skipping.")
                    return outputStream.toByteArray()
                }

                listener?.onStatusChanged("Selecting File $fileId...")
                val selectFileCommand = "00A4020C02$fileId"
                innerResponse = sdkWrapper?.transmit(selectFileCommand)
                val fileResponse = innerResponse
                if (fileResponse?.isSuccess() != true) {
                    listener?.onStatusChanged("Warning: Could not select file $fileId in dir $directoryId. Skipping.")
                    return outputStream.toByteArray()
                }

                var offset = 0
                val chunkSize = 255
                while (true) {
                    val p1 = (offset ushr 8) and 0xFF
                    val p2 = offset and 0xFF
                    val readFileCommand = String.format("00B0%02X%02X%02X", p1, p2, chunkSize)

                    innerResponse = sdkWrapper?.transmit(readFileCommand)
                    val readResponse = innerResponse

                    if (readResponse != null && readResponse.size > 2) {
                        val dataChunk = readResponse.copyOfRange(0, readResponse.size - 2)
                        if (dataChunk.isEmpty()) break
                        outputStream.write(dataChunk)
                        offset += dataChunk.size
                        if (dataChunk.size < chunkSize) break
                    } else {
                        break
                    }
                }
                return outputStream.toByteArray()
            }

            val allData = ByteArrayOutputStream()

            listener?.onStatusChanged("Reading Personal Data (0101/0001)...")
            allData.write(readFileInChunks("0001", "0101"))
            listener?.onStatusChanged("Reading Address Data (0102/0002)...")
            allData.write(readFileInChunks("0002", "0102"))
            listener?.onStatusChanged("Reading Photo Data (0103/0003)...")
            allData.write(readFileInChunks("0003", "0103"))
            listener?.onStatusChanged("Reading Signature Data (0104/0004)...")
            allData.write(readFileInChunks("0004", "0104"))
            listener?.onStatusChanged("Reading Fingerprint 1 (0105/0001)...")
            allData.write(readFileInChunks("0001", "0105"))
            listener?.onStatusChanged("Reading Fingerprint 2 (0105/0002)...")
            allData.write(readFileInChunks("0002", "0105"))
            listener?.onStatusChanged("Reading Visa/Permit Data (0106/0005)...")
            allData.write(readFileInChunks("0005", "0106"))
            listener?.onStatusChanged("Reading Employment Data (0107/0006)...")
            allData.write(readFileInChunks("0006", "0107"))

            val completeData = allData.toByteArray()
            if (completeData.isEmpty()) {
                throw Exception("Failed to read any data from the card.")
            }

            return@withContext parseCprData(completeData)
        } catch (e: Exception) {
            Log.e("CprScanner", "Read card error: ${e.message}", e)
            listener?.onStatusChanged("Error reading card: ${e.message}")
            return@withContext null
        }
    }

    private fun parseCprData(data: ByteArray): CprData {
        Log.d("CprScanner", "Parsing card data")
        try {
            val charset = Charsets.UTF_8

            fun getField(start: Int, length: Int): String {
                if (start + length > data.size) return ""
                val fieldBytes = data.copyOfRange(start, start + length)
                return String(fieldBytes, charset).replace(Regex("\\p{C}"), "").trim()
            }

            fun getRemainingField(start: Int): String {
                if (start >= data.size) return ""
                val remainingBytes = data.copyOfRange(start, data.size)
                return String(remainingBytes, charset).replace(Regex("\\p{C}"), "").trim()
            }

            val cprNumber = getField(0, 9)
            val fullName = getField(9, 50)
            val remainingData = getRemainingField(59)

            return CprData(
                cprNumber = cprNumber.ifEmpty { "Parse Error" },
                fullName = fullName.ifEmpty { "Parse Error" },
                dateOfBirth = "RAW: $remainingData",
                nationality = "---",
                gender = "---",
                cardExpiryDate = "---"
            )

        } catch (e: Exception) {
            Log.e("CprScanner", "Parse error: ${e.message}", e)
            return CprData("Parsing Failed", e.message ?: "Unknown parsing error", "", "", "", "")
        }
    }

    fun resume() {
        Log.d("CprScanner", "Resuming scanner")
        try {
            UsbSmartCard.getInstance(context).onResume()
            scannerRefreshJob?.cancel()
            scannerRefreshJob = scope.launch {
                while (isActive) {
                    refreshScanner()
                    delay(1000)
                }
            }
        } catch (e: Exception) {
            Log.e("CprScanner", "Resume error: ${e.message}", e)
            listener?.onStatusChanged("Error resuming scanner: ${e.message}")
        }
    }

    fun pause() {
        Log.d("CprScanner", "Pausing scanner")
        try {
            scannerRefreshJob?.cancel()
            sdkWrapper?.close()
            sdkWrapper = null
            UsbSmartCard.getInstance(context).onStop()
        } catch (e: Exception) {
            Log.e("CprScanner", "Pause error: ${e.message}", e)
        }
    }

    internal fun ByteArray.toHexString() = joinToString("") { "%02X".format(it) }
    internal fun ByteArray?.isSuccess(): Boolean = this != null && size >= 2 && this[this.size - 2] == 0x90.toByte() && this[this.size - 1] == 0x00.toByte()
}