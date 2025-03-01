package com.pbemgs.game.surge;

import com.pbemgs.model.Location;

import java.util.ArrayList;
import java.util.List;

public class SurgeCommand {

    private final int row;
    private final int col;
    private final SurgeDirection direction;
    private final boolean open; // true = open, false = close

    public SurgeCommand(int row, int col, SurgeDirection direction, boolean open) {
        this.row = row;
        this.col = col;
        this.direction = direction;
        this.open = open;
    }

    public SurgeCommand(Location loc, SurgeDirection direction, boolean open) {
        this.row = loc.row();
        this.col = loc.col();
        this.direction = direction;
        this.open = open;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public Location getLocation() { return new Location(row, col); }

    public SurgeDirection getDirection() {
        return direction;
    }

    public boolean isOpen() {
        return open;
    }

    /**
     * Serializes this command to a string (e.g., "OA12E" for open A12 east).
     */
    public String serialize() {
        String action = open ? "O" : "C";
        char column = (char) ('A' + col);
        int boardRow = row + 1;
        return action + column + boardRow + direction.toChar();
    }

    /**
     * Parses a command string into a SurgeCommand (e.g., "CA10S" → Close gate at A10 south).
     */
    public static SurgeCommand deserialize(String commandStr) {
        if (commandStr.length() < 4) {
            throw new IllegalArgumentException("Invalid command format: " + commandStr);
        }
        boolean open = commandStr.charAt(0) == 'O';
        Location loc = Location.fromString(commandStr.substring(1, commandStr.length() - 1));
        SurgeDirection direction = SurgeDirection.fromChar(commandStr.charAt(commandStr.length() - 1));
        return new SurgeCommand(loc.row(), loc.col(), direction, open);
    }

    /**
     * Parses a comma-separated player command string into a list of SurgeCommands.
     */
    public static List<SurgeCommand> parseCommandList(String commandString) {
        List<SurgeCommand> commands = new ArrayList<>();
        if (commandString == null || commandString.isEmpty()) {
            return commands;
        }
        String[] parts = commandString.split(",");
        for (String part : parts) {
            commands.add(deserialize(part.trim()));
        }
        return commands;
    }

    public String getPrettyString() {
        return (isOpen() ? "Open: " : "Close: ") + new Location(getRow(), getCol()) + "-" + getDirection().name();
    }
}
