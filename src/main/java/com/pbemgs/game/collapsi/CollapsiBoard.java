package com.pbemgs.game.collapsi;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.VisibleForTesting;
import com.pbemgs.model.Location;
import com.pbemgs.model.MonoSymbol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.pbemgs.game.collapsi.CollapsiDisplayDefs.BOT_BORDER;
import static com.pbemgs.game.collapsi.CollapsiDisplayDefs.TOP_BORDER;

/**
 * Board for a Collapsi game.
 * Each square has the card value, 0 if collapsed.  The two token
 * locations are stored separately.
 */
public class CollapsiBoard {

    private static final int SIZE = 4;
    private static final char SYMBOL_0 = 'X';
    private static final char SYMBOL_1 = 'O';

    private int[][] grid;
    private Location token0;
    private Location token1;


    public CollapsiBoard() {
        grid = new int[SIZE][SIZE];
    }

    public static char getPlayerSymbol(int playerSeat) {
        return switch (playerSeat) {
            case 0 -> SYMBOL_0;
            case 1 -> SYMBOL_1;
            default -> '@';
        };
    }

    /**
     * Serialization format: comma-separated list by row, followed
     * by the two token location strings
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                sb.append(grid[r][c]).append(",");
            }
        }
        sb.append(token0.toString()).append(",").append(token1.toString());
        return sb.toString();
    }

    public void deserialize(String serializedData) {
        String[] parts = serializedData.split(",");
        if (parts.length != 18) {
            throw new IllegalArgumentException("Invalid serialized data: expected 18 elements, got " + parts.length);
        }
        int index = 0;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                grid[r][c] = Integer.parseInt(parts[index++]);
            }
        }
        token0 = Location.fromString(parts[index++]);
        token1 = Location.fromString(parts[index]);
    }

    public void initializeNewBoard() {
        // Create and shuffle the deck: 4 aces (1s), 4 twos, 4 threes, 2 fours, 2 jokers (-1 and -2)
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            deck.add(1);
            deck.add(2);
            deck.add(3);
        }
        for (int i = 0; i < 2; i++) {
            deck.add(4);
        }
        deck.add(-1); // Player 0's joker
        deck.add(-2); // Player 1's joker
        Collections.shuffle(deck);

        // Assign deck to grid
        int index = 0;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                grid[r][c] = deck.get(index++);
            }
        }

        // Set initial token positions on their respective jokers
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c] == -1) {
                    token0 = new Location(r, c);
                } else if (grid[r][c] == -2) {
                    token1 = new Location(r, c);
                }
            }
        }
    }

    /**
     * Get valid moves, plus the string for path to each.
     */
    public Map<Location, String> getValidMoves(int playerSeat) {
        Location token = (playerSeat == 0) ? token0 : token1;
        int r = token.row();
        int c = token.col();
        int value = grid[r][c];

        // Determine possible move distances
        int moveVal = value;

        if ((playerSeat == 0 && value == -1) || (playerSeat == 1 && value == -2)) {
            moveVal = 1;
        }

        Map<Location, String> validMoveMap = new HashMap<>();
        List<Location> currentPath = new ArrayList<>();
        // DFS for the path, staring at the token location
        performPathDFS(token, moveVal, currentPath, validMoveMap);

        return validMoveMap;
    }

    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}; // up, down, left, right

    private void performPathDFS(Location curr, int remaining,
                                List<Location> currPath, Map<Location, String> validMoveMap) {
        currPath.add(curr);
        if (remaining == 0) {
            // at end, valid if not on either token's location, and
            // don't replace if a path already exists to here.
            if (!curr.equals(token0) && !curr.equals(token1) &&
                    !validMoveMap.containsKey(curr)) {
                validMoveMap.put(curr, convertPathToString(currPath));
            }
        } else {
            for (int d = 0; d < DIRS.length; ++d) {
                int newR = (curr.row() + DIRS[d][0] + SIZE) % SIZE;
                int newC = (curr.col() + DIRS[d][1] + SIZE) % SIZE;
                if (grid[newR][newC] != 0) {
                    Location newLoc = new Location(newR, newC);
                    if (!currPath.contains(newLoc)) {
                        performPathDFS(newLoc, remaining - 1, currPath, validMoveMap);
                    }
                }
            }  // end for (each direction)
        }  // end else (moves remaining)

        // remove curr (last element) when done for backtracking
        currPath.remove(currPath.size() - 1);
    }

    private String convertPathToString(List<Location> currPath) {
        return currPath.stream().map(Location::toString).collect(Collectors.joining(" -> "));
    }

    /**
     * Execute the move - assumed validated already.
     */
    public void makeMove(int playerSeat, Location loc) {
        Location token = (playerSeat == 0) ? token0 : token1;
        int r = token.row();
        int c = token.col();
        grid[r][c] = 0; // Collapse the starting square
        if (playerSeat == 0) {
            token0 = loc;
        } else {
            token1 = loc;
        }
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
                    Location curr = new Location(r, c);
                    int val = grid[r][c] >= 0 ? grid[r][c] : 1;

                    boolean occupied = (curr.equals(token0) || curr.equals(token1));
                    char playerSymbol = (curr.equals(token0) ? SYMBOL_0 : curr.equals(token1) ? SYMBOL_1 : ' ');
                    if (txt == 0) {
                        sb.append(TOP_BORDER);
                    }
                    if (txt == 4) {
                        sb.append(BOT_BORDER);
                    }
                    if (txt == 1 || txt == 3) {
                        if (grid[r][c] == 0) {
                            sb.append(MonoSymbol.GRID_VERTICAL.getSymbol());
                            sb.append("###");
                            sb.append(MonoSymbol.GRID_VERTICAL.getSymbol());
                        } else {
                            sb.append(MonoSymbol.GRID_VERTICAL.getSymbol());
                            sb.append(" ").append(playerSymbol).append(" ");
                            sb.append(MonoSymbol.GRID_VERTICAL.getSymbol());
                        }
                    }
                    if (txt == 2) {
                        if (grid[r][c] == 0) {
                            sb.append(MonoSymbol.GRID_VERTICAL.getSymbol());
                            sb.append("###");
                            sb.append(MonoSymbol.GRID_VERTICAL.getSymbol());
                        } else {
                            sb.append(MonoSymbol.GRID_VERTICAL.getSymbol()).append(playerSymbol).append(val);
                            sb.append(playerSymbol).append(MonoSymbol.GRID_VERTICAL.getSymbol());
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

    // Unit test support
    @VisibleForTesting
    public int getGrid(int r, int c) {
        return grid[r][c];
    }

}