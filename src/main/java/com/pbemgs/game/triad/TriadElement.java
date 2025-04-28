package com.pbemgs.game.triad;

import java.util.Random;

public enum TriadElement {
    NONE(' ', " "),
    FIRE('F', "(Fire)"),
    ICE('I', "(Ice)"),
    LIT('L', "(Lit)");


    private final char dispChar;
    private final String dispStr;

    TriadElement(char dispChar, String dispStr) {
        this.dispChar = dispChar;
        this.dispStr = dispStr;
    }

    public char getDisplayChar() {
        return dispChar;
    }

    public String getDisplayStr() {
        return dispStr;
    }

    public static TriadElement fromChar(char c) {
        for (TriadElement element : values()) {
            if (element.dispChar == c) {
                return element;
            }
        }
        throw new IllegalArgumentException("Invalid element character: " + c);
    }

    /**
     * get random non-NONE element
     */
    public static TriadElement getRandom(Random rng) {
        TriadElement[] elements = values();
        return elements[rng.nextInt(1, 4)];
    }

}