package com.smartcardpoc

import javax.smartcardio.Card
import javax.smartcardio.CardChannel
import javax.smartcardio.CardException
import javax.smartcardio.CardTerminal
import javax.smartcardio.CommandAPDU
import javax.smartcardio.ResponseAPDU
import android.util.Log

class FutronicSdkWrapper {
    private var terminal: CardTerminal? = null
    private var card: Card? = null
    private var channel: CardChannel? = null

    fun open(cardTerminal: CardTerminal): Boolean {
        Log.d("FutronicSdkWrapper", "Opening terminal")
        return try {
            this.terminal = cardTerminal
            true
        } catch (e: Exception) {
            Log.e("FutronicSdkWrapper", "Open error: ${e.message}", e)
            false
        }
    }

    fun isReaderOpen(): Boolean = terminal != null

    fun transmit(command: String): ByteArray? {
        Log.d("FutronicSdkWrapper", "Transmitting command: $command")
        try {
            if (terminal?.isCardPresent != true) {
                throw CardException("No card present in the reader. Please insert a CPR card.")
            }

            if (card == null) {
                card?.disconnect(false)
                try {
                    card = terminal!!.connect("T=1")
                } catch (e1: CardException) {
                    try {
                        card = terminal!!.connect("T=0")
                    } catch (e2: CardException) {
                        throw CardException("Failed to connect with both T=1 and T=0 protocols.", e2)
                    }
                }
            }

            val currentChannel = card!!.basicChannel
                ?: throw CardException("Could not get a valid card channel.")

            val commandBytes = stringToBytes(command)
            val commandAPDU = CommandAPDU(commandBytes)
            val responseAPDU: ResponseAPDU = currentChannel.transmit(commandAPDU)

            return responseAPDU.bytes
        } catch (e: Exception) {
            Log.e("FutronicSdkWrapper", "Transmit error: ${e.message}", e)
            close()
            throw e
        }
    }

    fun close() {
        Log.d("FutronicSdkWrapper", "Closing wrapper")
        try {
            card?.disconnect(false)
        } catch (e: Exception) {
            Log.e("FutronicSdkWrapper", "Close error: ${e.message}", e)
        }
        terminal = null
        card = null
        channel = null
    }

    private fun stringToBytes(s: String): ByteArray {
        val cleanString = s.replace(Regex("[^0-9a-fA-F]"), "")
        if (cleanString.length % 2 != 0) {
            throw IllegalArgumentException("Hex string must have an even number of characters")
        }
        return cleanString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}