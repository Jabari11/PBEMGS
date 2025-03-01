package com.pbemgs.model;

public record Location(int row, int col) {
    @Override
    public String toString() {
        char column = (char) ('A' + col);
        return String.valueOf(column) + (row + 1);
    }

    /**
     * Converts a board position (e.g., "A10") to a Location.
     * A null return means the parsing was invalid.
     */
    public static Location fromString(String pos) {
        if (pos.length() < 2 || pos.length() > 3) {
            return null;
        }
        String uPos = pos.toUpperCase();
        char colChar = uPos.charAt(0);
        if (colChar < 'A' || colChar > 'Z') {
            return null;
        }

        try {
            int col = colChar - 'A';
            int row = Integer.parseInt(uPos.substring(1)) - 1;
            return new Location(row, col);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

