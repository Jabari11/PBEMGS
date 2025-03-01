package com.pbemgs.game.gomoku;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.VisibleForTesting;
import com.pbemgs.model.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GoMokuBoard {
    public final static char PLAYER_X = 'X';
    public final static char PLAYER_O = 'O';
    public final static char EMPTY = '.';

    private LambdaLogger logger;

    private int size;
    private char[][] grid;


    public GoMokuBoard(int size, LambdaLogger logger) {
        this.logger = logger;
        this.size = size;
        grid = new char[size][size];
        initializeBlank();
    }

    private void initializeBlank() {
        for (int r = 0; r < size; ++r) {
            for (int c = 0; c < size; ++c) {
                grid[r][c] = EMPTY;
            }
        }
    }

    public String serialize() {
        return Arrays.stream(grid)
                .map(String::new)
                .collect(Collectors.joining("|"));
    }

    public void deserialize(String serializedData) {
        String[] rows = serializedData.split("\\|");
        for (int r = 0; r < size; r++) {
            grid[r] = rows[r].toCharArray();
        }
    }

    /**
     *  Validate a move for the symbol.  Returns an error message string if there is
     *  an error, or null if the move is valid.
     */
    public String validateMove(Location square) {
        if (!isOnBoard(square)) {
            return "Chosen Location " + square.toString() + " is not on the board.";
        }
        if (grid[square.row()][square.col()] != EMPTY) {
            return "Chosen Location " + square.toString() + " is not empty!";
        }

        return null;
    }

    public boolean isBoardFull() {
        for (int r = 0; r < size; ++r) {
            for (int c = 0; c < size; ++c) {
                if (grid[r][c] == EMPTY) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isVictoryCondition(char symbol) {
        // Check every cell on the board for the starting point of a five-in-a-row
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (grid[r][c] == symbol) {
                    if (checkDirection(r, c, 1, 0, symbol) || // Horizontal
                            checkDirection(r, c, 0, 1, symbol) || // Vertical
                            checkDirection(r, c, 1, 1, symbol) || // Diagonal (\)
                            checkDirection(r, c, 1, -1, symbol)) { // Diagonal (/)
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Helper method to check in a specific direction for a five-in-a-row
    private boolean checkDirection(int row, int col, int dRow, int dCol, char symbol) {
        int count = 1; // Current stone

        // Check forward direction
        for (int i = 1; i < 5; i++) {
            int newRow = row + i * dRow;
            int newCol = col + i * dCol;
            if (!isOnBoard(new Location(newRow, newCol)) || grid[newRow][newCol] != symbol) {
                break;
            }
            count++;
        }

        return count >= 5; // Win condition
    }

    /**
     *  Execute the move - assumed validated already.
     *  Returns true if a piece was captured.
     */
    public void makeMove(char symbol, Location move) {
        grid[move.row()][move.col()] = symbol;
    }

    public Location getRandomMove() {
        List<Location> emptyLocs = new ArrayList<>();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (grid[r][c] == EMPTY) {
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
     *  Generates the board text.  Large board, put grid coords on all 4 sides.
     */
    public String getBoardTextBody() {
        StringBuilder sb = new StringBuilder("    ");
        for (int i = 0; i < size; i++) {
            sb.append((char) ('A' + i)).append(" ");
        }
        sb.append("\n");

        for (int r = 0; r < size; r++) {
            sb.append(String.format("%2d  ", (r + 1)));
            for (int c = 0; c < size; c++) {
                sb.append(grid[r][c]).append(" ");
            }
            sb.append(" ").append(r + 1).append("\n");
        }

        // bottom column marks
        sb.append("    ");
        for (int i = 0; i < size; i++) {
            sb.append((char) ('A' + i)).append(" ");
        }
        sb.append("\n");

        return sb.toString();
    }

    private boolean isOnBoard(Location loc) {
        return (loc.row() >= 0 && loc.row() < size && loc.col() >= 0 && loc.col() < size);
    }

    // Unit test support
    @VisibleForTesting
    public char getGrid(int r, int c) {
        return grid[r][c];
    }

}