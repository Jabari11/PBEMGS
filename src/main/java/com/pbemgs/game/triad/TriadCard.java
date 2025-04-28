package com.pbemgs.game.triad;

import com.pbemgs.model.Direction;

/**
 * Record for an individual card (in the deck).
 * Values are in order Up, Right, Down, Left.
 */
public record TriadCard(int cardId, String name, int[] values, TriadElement element) {
    public TriadCard {
        if (values.length != 4) {
            throw new IllegalArgumentException("Values array must have exactly 4 elements.");
        }
    }

    public int valueOfSide(Direction dir) {
        return switch (dir) {
            case NORTH -> values[0];
            case EAST -> values[1];
            case SOUTH -> values[2];
            case WEST -> values[3];
        };
    }
}
