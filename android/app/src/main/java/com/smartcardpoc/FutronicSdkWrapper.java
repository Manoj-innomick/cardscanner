package com.smartcardpoc;

import javax.smartcardio.*;
import java.util.regex.Pattern;

public class FutronicSdkWrapper {
    private CardTerminal terminal;
    private Card card;
    private CardChannel channel;

    public boolean open(CardTerminal cardTerminal) {
        try {
            this.terminal = cardTerminal;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isReaderOpen() {
        return terminal != null;
    }

    public byte[] transmit(String command) {
        try {
            if (!terminal.isCardPresent()) {
                throw new CardException("No card present in the reader. Please insert a CPR card.");
            }

            if (card == null) {
                try {
                    card = terminal.connect("T=1");
                } catch (CardException e1) {
                    try {
                        card = terminal.connect("T=0");
                    } catch (CardException e2) {
                        throw new CardException("Failed to connect with both T=1 and T=0 protocols.", e2);
                    }
                }
            }

            CardChannel currentChannel = card.getBasicChannel();
            if (currentChannel == null) {
                throw new CardException("Could not get a valid card channel.");
            }

            byte[] commandBytes = stringToBytes(command);
            CommandAPDU commandAPDU = new CommandAPDU(commandBytes);
            ResponseAPDU responseAPDU = currentChannel.transmit(commandAPDU);

            return responseAPDU.getBytes();
        } catch (Exception e) {
            close();
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    public void close() {
        try {
            if (card != null) {
                card.disconnect(false);
            }
        } catch (Exception e) {
            // Ignore
        }
        terminal = null;
        card = null;
        channel = null;
    }

    private byte[] stringToBytes(String s) {
        String cleanString = s.replaceAll("[^0-9a-fA-F]", "");
        if (cleanString.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have an even number of characters");
        }
        byte[] bytes = new byte[cleanString.length() / 2];
        for (int i = 0; i < cleanString.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(cleanString.substring(i, i + 2), 16);
        }
        return bytes;
    }
}