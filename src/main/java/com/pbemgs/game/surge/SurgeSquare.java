package com.pbemgs.game.surge;

import com.pbemgs.model.Location;
import com.pbemgs.model.MonoSymbol;

import java.util.EnumMap;

/**
 * Represents a single square on a Surge board.
 * Geyser location/strength is done separately.
 * Serialization format for the board is done from 0,0 outward.
 * Each square has it's contents (player/quantity), and then the state of the east and south gates.
 * Deserialization setting of the north and west gates needs to be done at the same time that populating
 * those equivalent gates on the adjacent square is done.
 * For display, there will be a difference between html and plain-text formats (form-factor included).
 */
public class SurgeSquare {

    private int row;
    private int col;
    private int playerNum; // 0 = neutral, otherwise player ID
    private int quantity; // Liquid % (0-1000)
    private boolean isObstacle;
    private EnumMap<SurgeDirection, Boolean> gates; // Store gates as a map, true == open


    public SurgeSquare(int row, int col) {
        this.row = row;
        this.col = col;
        this.playerNum = 0;
        this.quantity = 0;
        this.isObstacle = false;
        this.gates = new EnumMap<>(SurgeDirection.class);

        for (SurgeDirection dir : SurgeDirection.values()) {
            gates.put(dir, false);
        }
    }

    public Location getLocation() {
        return new Location(row, col);
    }

    public int getPlayerNum() {
        return playerNum;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean isObstacle() {
        return isObstacle;
    }

    public boolean isGateOpen(SurgeDirection dir) {
        return gates.getOrDefault(dir, false);
    }

    public void setGate(SurgeDirection dir, boolean isOpen) {
        gates.put(dir, isOpen);
        if (isOpen && isObstacle) {
            throw new IllegalArgumentException("Attempting to open a gate against an obstacle");
        }
    }

    public void update(int playerNum, int qty) {
        this.playerNum = playerNum;
        this.quantity = qty;
    }

    public void truncate() {
        quantity = Math.min(quantity, 1000);
    }

    public void setAsObstacle() {
        this.isObstacle = true;
    }

    /**
     * Serializes this SurgeSquare into a compact format.
     * Example: "530:1:OC" (53% full, player 1, East open, South closed)
     */
    public String serialize() {
        if (isObstacle) {
            return "X";
        }
        char eGate = isGateOpen(SurgeDirection.EAST) ? 'O' : 'C';
        char sGate = isGateOpen(SurgeDirection.SOUTH) ? 'O' : 'C';
        return quantity + ":" + playerNum + ":" + eGate + sGate;
    }

    /**
     * (Partially) deserializes a SurgeSquare from a string.
     * North/West gates must be set separately from the board parsing.
     */
    public void deserialize(String data) {
        if (data.equals("X")) {
            isObstacle = true;
            return;
        }
        String[] parts = data.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid SurgeSquare serialization: " + data);
        }

        quantity = Integer.parseInt(parts[0]);
        playerNum = Integer.parseInt(parts[1]);
        boolean eastGate = parts[2].charAt(0) == 'O';
        boolean southGate = parts[2].charAt(1) == 'O';

        setGate(SurgeDirection.EAST, eastGate);
        setGate(SurgeDirection.SOUTH, southGate);
    }

    // Text Display generation methods

    /**
     * Single-character display in the player's color for html clients.
     */
    public String toHtmlDisplay() {
        if (isObstacle) {
            return "###";
        }
        if (quantity == 0) {
            return "   ";
        }

        char liquidSymbol = getLiquidSymbol(quantity);
        return " <span style='color:" + SurgeColor.COLOR.get(playerNum) + ";'>" + liquidSymbol + "</span> ";
    }

    /**
     * Non-color display for the cell.
     */
    public String toTextDisplay() {
        if (quantity == 0) {
            return "   ";
        }
        if (isObstacle) {
            return " # ";
        }
        char liquidSymbol = getLiquidSymbol(quantity);

        // Return a 1x3 formatted grid per square (for now)
        return String.valueOf(playerNum) + liquidSymbol + playerNum;
    }

    /**
     * Determines which Unicode symbol represents the liquid level.
     */
    public static char getLiquidSymbol(int quantity) {
        if (quantity <= 200) return MonoSymbol.BULLET_OPERATOR.getSymbol();
        if (quantity <= 400) return MonoSymbol.BULLET.getSymbol();
        if (quantity <= 600) return MonoSymbol.BLACK_CIRCLE.getSymbol();
        if (quantity <= 800) return MonoSymbol.INVERSE_CIRCLE.getSymbol();
        return MonoSymbol.BLACK_SQUARE.getSymbol();
    }
}
