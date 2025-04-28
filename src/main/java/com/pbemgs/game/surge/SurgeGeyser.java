package com.pbemgs.game.surge;

import com.pbemgs.model.Location;
import com.pbemgs.model.MonoSymbol;

public class SurgeGeyser {
    public enum GeyserType {
        HOME('H', 1000, MonoSymbol.BLACK_TRIANGLE_UP.getSymbol(), "Home Base"),
        LARGE('L', 500, MonoSymbol.BLACK_LOZENGE.getSymbol(), "Large"),
        MEDIUM('M', 350, MonoSymbol.IDENTICAL_TO.getSymbol(), "Medium"),
        SMALL('S', 200, MonoSymbol.ALMOST_EQUAL_TO.getSymbol(), "Small");

        private final char symbol;
        private final int power;
        private final char displaySymbol;
        private final String displayName;

        GeyserType(char symbol, int power, char displaySymbol, String displayName) {
            this.symbol = symbol;
            this.power = power;
            this.displaySymbol = displaySymbol;
            this.displayName = displayName;
        }

        public char getSymbol() {
            return symbol;
        }

        public int getPower() {
            return power;
        }

        public char getDisplaySymbol() {
            return displaySymbol;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static GeyserType fromSymbol(char c) {
            for (GeyserType type : values()) {
                if (type.symbol == c) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Invalid geyser symbol: " + c);
        }
    }

    private final int row;
    private final int col;
    private final GeyserType type;

    public SurgeGeyser(int row, int col, GeyserType type) {
        this.row = row;
        this.col = col;
        this.type = type;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public int getPower() {
        return type.getPower();
    }

    public GeyserType getType() {
        return type;
    }

    /**
     * Serializes this geyser to "A12:H" format.
     */
    public String serialize() {
        char column = (char) ('A' + col);
        int boardRow = row + 1;
        return String.valueOf(column) + String.valueOf(boardRow) + ":" + type.getSymbol();
    }

    /**
     * Deserializes a SurgeGeyser from a string (e.g., "A12:H").
     */
    public static SurgeGeyser deserialize(String data) {
        String[] parts = data.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid SurgeGeyser serialization: " + data);
        }

        Location loc = Location.fromString(parts[0]);
        GeyserType type = GeyserType.fromSymbol(parts[1].charAt(0));

        return new SurgeGeyser(loc.row(), loc.col(), type);
    }

    /**
     * Generates the HTML display for this geyser.
     * Color is based on the controlling player's color.
     */
    public String toHtmlDisplay(int playerNum) {
        String color = SurgeColor.COLOR.get(playerNum);
        return " <span style='color:" + color + ";'>" + type.getDisplaySymbol() + "</span> ";
    }

    /**
     * Generates the plain-text display for this geyser.
     */
    public String toTextDisplay(int playerNum) {
        return String.valueOf(playerNum) + type.getDisplaySymbol() + playerNum;
    }

    public static String getLegendDisplayForType(GeyserType type) {
        return type.getDisplaySymbol() + ": " + type.getDisplayName() + String.format("\t(%4.1f%% / turn).", (float) type.getPower() / 10.0f);
    }

}
