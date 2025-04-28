package com.pbemgs.model;

/**
 * Win/Loss (and draw) record for a player - can apply to a single game or across the platform.
 */
public record PlayerRecord(int wins, int losses, int draws) {
}
