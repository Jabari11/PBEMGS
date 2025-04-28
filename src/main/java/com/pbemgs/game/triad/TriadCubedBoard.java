package com.pbemgs.game.triad;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.VisibleForTesting;
import com.pbemgs.model.Direction;
import com.pbemgs.model.Location;
import com.pbemgs.model.MonoSymbol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static com.pbemgs.game.triad.TriadDisplayDefs.BLANK_INNER;
import static com.pbemgs.game.triad.TriadDisplayDefs.BOT_BORDER;
import static com.pbemgs.game.triad.TriadDisplayDefs.SHADING_CHAR;
import static com.pbemgs.game.triad.TriadDisplayDefs.TOP_BORDER;

/**
 * Board for a TriadCubed game.  Each square has the card id, owner, and element (if set)
 */
public class TriadCubedBoard {

    // Color by seat
    public static final List<String> COLOR = List.of(
            "#FF0000", // Red - seat 0
            "#0000FF" // Blue - seat 1
    );

    public record Space(int cardId, Integer owner, TriadElement element) {
    }

    private static final int SIZE = 3;

    private LambdaLogger logger;
    private Space[][] grid;


    public TriadCubedBoard(LambdaLogger logger) {
        this.logger = logger;
        grid = new Space[SIZE][SIZE];
    }

    /**
     * Serialization format: id:owner:element(char), comma-separated, row by row.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                Space space = grid[r][c];
                if (!sb.isEmpty()) {
                    sb.append(",");
                }
                sb.append(space.cardId()).append(":")
                        .append(space.owner() == null ? "null" : space.owner()).append(":")
                        .append(space.element().getDisplayChar());
            }
        }
        return sb.toString();
    }

    public void deserialize(String serializedData) {
        String[] parts = serializedData.split(",");
        int index = 0;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                String[] spaceData = parts[index++].split(":");
                int cardId = Integer.parseInt(spaceData[0]);
                Integer owner = spaceData[1].equals("null") ? null : Integer.parseInt(spaceData[1]);
                TriadElement element = TriadElement.fromChar(spaceData[2].charAt(0));
                grid[r][c] = new Space(cardId, owner, element);
            }
        }
    }

    public void initializeNewBoard(boolean elementalOn) {
        Set<Location> elemLocs = new HashSet<>();
        Random rng = new Random();
        if (elementalOn) {
            int count = rng.nextInt(2, 6);
            while (elemLocs.size() < count) {
                Location loc = new Location(rng.nextInt(0, 3), rng.nextInt(0, 3));
                elemLocs.add(loc);
            }
        }

        for (int r = 0; r < SIZE; ++r) {
            for (int c = 0; c < SIZE; ++c) {
                if (!elemLocs.contains(new Location(r, c))) {
                    grid[r][c] = new Space(0, null, TriadElement.NONE);
                } else {
                    grid[r][c] = new Space(0, null, TriadElement.getRandom(rng));
                }
            }
        }
    }

    /**
     * Validate a move for the symbol.  Returns an error message string if there is
     * an error, or null if the move is valid.
     */
    public String validateMove(Location square) {
        if (!isOnBoard(square)) {
            return "Chosen Location " + square.toString() + " is not on the board.";
        }
        if (grid[square.row()][square.col()].owner() != null) {
            return "Chosen Location " + square.toString() + " is not empty!";
        }

        return null;
    }

    public boolean isBoardFull() {
        for (int r = 0; r < SIZE; ++r) {
            for (int c = 0; c < SIZE; ++c) {
                if (grid[r][c].owner() == null) {
                    return false;
                }
            }
        }
        return true;
    }

    public int getCardCount(int owner) {
        int count = 0;
        for (int r = 0; r < SIZE; ++r) {
            for (int c = 0; c < SIZE; ++c) {
                if (grid[r][c].owner() != null && grid[r][c].owner() == owner) {
                    ++count;
                }
            }
        }
        return count;
    }

    /**
     * Generates the "Board Element Count: Fire - 2, Lit - 1" string for hand selection.
     */
    public String getBoardElementString() {
        Map<TriadElement, Integer> countByElement = new HashMap<>();
        for (int r = 0; r < SIZE; ++r) {
            for (int c = 0; c < SIZE; ++c) {
                if (grid[r][c].element() != TriadElement.NONE) {
                    countByElement.merge(grid[r][c].element(), 1, Integer::sum);
                }
            }
        }
        if (countByElement.isEmpty()) {
            return "";
        }

        return "Board Element Count:  " +
                countByElement.entrySet().stream()
                        .map(e -> e.getKey().name() + ": " + e.getValue())  // Format "Fire: 2"
                        .collect(Collectors.joining(", "))
                + "\n\n";
    }

    /**
     * Execute the move - assumed validated already.
     * Checks captures in all directions.
     */
    public void makeMove(int playerSeat, int cardId, Location loc) {
        grid[loc.row()][loc.col()] = new Space(cardId, playerSeat, grid[loc.row()][loc.col()].element());
        logger.log("updated loc: " + loc.toString() + " to " + grid[loc.row()][loc.col()].toString());
        for (Direction dir : Direction.values()) {
            Location checkLoc = dir.getAdjacentLoc(loc);
            if (isOnBoard(checkLoc) &&
                    grid[checkLoc.row()][checkLoc.col()].owner() != null &&
                    grid[checkLoc.row()][checkLoc.col()].owner() != playerSeat) {
                int thisVal = getCardSideValue(loc, dir, true);
                int thatVal = getCardSideValue(checkLoc, dir.getOpposite(), true);
                if (thisVal > thatVal) {
                    Space flipped = new Space(grid[checkLoc.row()][checkLoc.col()].cardId, playerSeat,
                            grid[checkLoc.row()][checkLoc.col()].element());
                    grid[checkLoc.row()][checkLoc.col()] = flipped;
                }
            }
        }
    }

    public Location getRandomEmptyLocation() {
        List<Location> emptyLocs = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c].owner() == null) {
                    emptyLocs.add(new Location(r, c));
                }
            }
        }
        if (emptyLocs.isEmpty()) {
            throw new IllegalStateException("No empty squares on board!");
        }
        Collections.shuffle(emptyLocs);
        return emptyLocs.get(0);
    }

    /**
     * Generates the board text.
     */
    public String getBoardTextBody() {
        // Column identifier row
        StringBuilder sb = new StringBuilder("     ");
        for (int i = 0; i < SIZE; i++) {
            sb.append((char) ('A' + i)).append("     ");
        }
        sb.append("\n");

        // Card rows - each row has 5 text rows (top-border, top, mid, bot, bot-border)
        for (int r = 0; r < SIZE; r++) {
            for (int txt = 0; txt < 5; txt++) {
                sb.append(txt == 2 ? String.format("%d  ", (r + 1)) : "   ");  // row ids
                for (int c = 0; c < SIZE; c++) {
                    String colorStart = "";
                    String colorEnd = "";
                    char spaceChar = ' ';
                    if (grid[r][c].owner() != null) {
                        colorStart = "<span style='color:" + COLOR.get(grid[r][c].owner()) + ";'>";
                        colorEnd = "</span>";
                        if (grid[r][c].element() != TriadElement.NONE &&
                                grid[r][c].element() == TriadCardSet.getById(grid[r][c].cardId()).element()) {
                            spaceChar = SHADING_CHAR;
                        }
                    }
                    if (txt == 0) {
                        sb.append(colorStart).append(TOP_BORDER).append(colorEnd);
                    }
                    if (txt == 4) {
                        sb.append(colorStart).append(BOT_BORDER).append(colorEnd);
                    }
                    if (txt == 1 || txt == 3) {
                        if (grid[r][c].owner() == null) {
                            sb.append(BLANK_INNER);
                        } else {
                            Direction dir = txt == 1 ? Direction.NORTH : Direction.SOUTH;
                            int val = getCardSideValue(new Location(r, c), dir, false);
                            sb.append(colorStart);
                            sb.append(MonoSymbol.GRID_VERTICAL.getSymbol()).append(spaceChar);
                            sb.append(val).append(spaceChar).append(MonoSymbol.GRID_VERTICAL.getSymbol()).append(colorEnd);
                        }
                    }
                    if (txt == 2) {
                        if (grid[r][c].owner() == null) {
                            sb.append(MonoSymbol.GRID_VERTICAL.getSymbol()).append(" ");
                            sb.append(grid[r][c].element().getDisplayChar());
                            sb.append(" ").append(MonoSymbol.GRID_VERTICAL.getSymbol());
                        } else {
                            sb.append(colorStart).append(MonoSymbol.GRID_VERTICAL.getSymbol());
                            sb.append(getCardSideValue(new Location(r, c), Direction.WEST, false)).append(spaceChar);
                            sb.append(getCardSideValue(new Location(r, c), Direction.EAST, false));
                            sb.append(MonoSymbol.GRID_VERTICAL.getSymbol()).append(colorEnd);
                        }
                    }

                    sb.append(" ");
                }
                sb.append("\n");
            }  // end for (text row within card row)
            sb.append("\n");
        }  // end for (card row)
        sb.append("\n");

        return sb.toString();
    }

    // Utility methods
    private boolean isOnBoard(Location loc) {
        return (loc.row() >= 0 && loc.row() < SIZE && loc.col() >= 0 && loc.col() < SIZE);
    }

    /**
     * Get the value of a card side at a location.  "adjusted" adjusts the value for elemental
     * effects if set.
     */
    private int getCardSideValue(Location loc, Direction dir, boolean adjust) {
        TriadCard card = TriadCardSet.getById(grid[loc.row()][loc.col()].cardId());
        int adjustment = (adjust && grid[loc.row()][loc.col()].element() != TriadElement.NONE &&
                grid[loc.row()][loc.col()].element() == card.element()) ? 1 : 0;
        return card.valueOfSide(dir) + adjustment;
    }

    // Unit test support
    @VisibleForTesting
    public Space getGrid(int r, int c) {
        return grid[r][c];
    }

}