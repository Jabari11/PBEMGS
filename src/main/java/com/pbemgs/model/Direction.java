package com.pbemgs.model;

public enum Direction {
    NORTH('N', 'U', 0, -1),
    EAST('E', 'L', 1, 0),
    SOUTH('S', 'D', 0, 1),
    WEST('W', 'R', -1, 0);

    private final char symbol, secSymbol;
    private final int deltaX, deltaY;
    private Direction opposite;

    Direction(char symbol, char secSymbol, int deltaX, int deltaY) {
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

    public static Direction fromChar(char c) {
        for (Direction dir : values()) {
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

    public Direction getOpposite() {
        return opposite;
    }

    public Location getAdjacentLoc(Location loc) {
        return new Location(loc.row() + deltaY, loc.col() + deltaX);
    }
}