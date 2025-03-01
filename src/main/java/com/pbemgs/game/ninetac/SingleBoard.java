package com.pbemgs.game.ninetac;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A single 3x3 tic-tac-toe board for Ninetac.
 * Each cell contains the numeric id if it hasn't been captured yet, or
 * the player id of the capturing player.
 * There is a separate flag indicating if the board has been won.  If so, the individual cells
 * may not be populated.
 * Populating the board from an external source can only be done through deserializing the string
 * representation, which is a comma separated list of values (or "W:playerID" if already won).
 */
public class SingleBoard {
    private static String X_REP = "><";
    private static String O_REP = "()";
    private int[] cells = new int[9]; // 1D array to represent 3x3 board
    private boolean isWon = false;
    private int winner = 0; // 0 = no winner, -1 = X, -2 = O

    public SingleBoard() {
        Arrays.fill(cells, 0); // Initialize cells to 0
    }

    public String serialize() {
        if (isWon) return "W:" + (-winner);
        return Arrays.stream(cells).mapToObj(String::valueOf).collect(Collectors.joining(","));
    }

    public void deserialize(String serializedData) {
        if (serializedData.startsWith("W:")) {
            isWon = true;
            winner = Integer.parseInt(serializedData.split(":")[1]) * -1;
        } else {
            String[] parts = serializedData.split(",");
            for (int i = 0; i < parts.length; i++) {
                cells[i] = Integer.parseInt(parts[i]);
            }
        }
    }

    public boolean checkWin() {
        if (isWon) {
            return true;
        }
        // Check rows, columns, and diagonals for a win
        int[][] winConditions = {
                {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, // Rows
                {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, // Columns
                {0, 4, 8}, {2, 4, 6}             // Diagonals
        };

        for (int[] condition : winConditions) {
            if (cells[condition[0]] != 0 &&
                    cells[condition[0]] == cells[condition[1]] &&
                    cells[condition[1]] == cells[condition[2]]) {
                isWon = true;
                winner = (cells[condition[0]] == NinetacBoard.PLAYER_X) ? NinetacBoard.PLAYER_X : NinetacBoard.PLAYER_O;
                return true;
            }
        }
        return false;
    }

    public void captureNumber(int player, int number) {
        for (int x = 0; x < 9; ++x) {
            if (cells[x] == number) {
                cells[x] = player;
            }
        }
    }

    public Set<Integer> getAvailableNumbers() {
        if (isWon) {
            return new HashSet<>();
        }
        return Arrays.stream(cells).filter(n -> n > 0).boxed().collect(Collectors.toSet());
    }

    public int getCenter() {
        if (isWon) {
            return winner;
        }
        return cells[4];
    }

    public int getWinner() {
        return isWon ? winner : 0;
    }

    public String getRowTextString(int row) {
        if (isWon) {
            String fill = winner == NinetacBoard.PLAYER_X ? X_REP : O_REP;
            return fill + " | " + fill + " | " + fill;
        }
        return String.format("%2s | %2s | %2s",
                renderCell(cells[row * 3]),
                renderCell(cells[row * 3 + 1]),
                renderCell(cells[row * 3 + 2]));
    }

    private String renderCell(int cellVal) {
        return switch (cellVal) {
            case NinetacBoard.PLAYER_X -> X_REP;
            case NinetacBoard.PLAYER_O -> O_REP;
            default -> String.format("%02d", cellVal);
        };
    }
}