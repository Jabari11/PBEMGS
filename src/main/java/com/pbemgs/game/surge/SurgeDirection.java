package com.pbemgs.game.surge;

import com.pbemgs.model.Location;

public enum SurgeDirection {
    NORTH('N', 'U', 0, -1),
    SOUTH('S', 'D', 0, 1),
    EAST('E', 'L', 1, 0),
    WEST('W', 'R', -1, 0);

    private final char symbol, secSymbol;
    private final int deltaX, deltaY;
    private SurgeDirection opposite;

    SurgeDirection(char symbol, char secSymbol, int deltaX, int deltaY) {
        this.symbol = symbol;
        this.secSymbol = secSymbol;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    // Static block to set opposites
    static {
        NORTH.opposite = SOUTH;
        SOUTH.opposite = NORTH;
        EAST.opposite = WEST;
        WEST.opposite = EAST;
    }

    public char toChar() {
        return symbol;
    }

    public static SurgeDirection fromChar(char c) {
        for (SurgeDirection dir : values()) {
            if (c == dir.symbol || c == dir.secSymbol) return dir;
        }
        throw new IllegalArgumentException("Invalid direction: " + c);
    }

    public int getAdjacentRow(int row) {
        return row + deltaY;
    }

    public int getAdjacentCol(int col) {
        return col + deltaX;
    }

    public SurgeDirection getOpposite() {
        return opposite;
    }

    public Location getAdjacentLoc(Location loc) {
        return new Location(loc.row() + deltaY, loc.col() + deltaX);
    }
}