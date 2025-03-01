package com.pbemgs.game.tac;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TacBoard {
    private static final int SIZE = 3;

    private final char[][] board;

    public TacBoard() {
        this.board = new char[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                board[r][c] = '-';
            }
        }
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                sb.append(board[r][c]);
            }
        }
        return sb.toString();
    }

    public void deserialize(String serializedState) {
        for (int i = 0; i < serializedState.length(); i++) {
            board[i / SIZE][i % SIZE] = serializedState.charAt(i);
        }
    }

    public boolean isValidMove(int square) {
        if (square < 1 || square > 9) {
            return false;
        }
        int index = square - 1;
        int row = index / SIZE;
        int col = index % SIZE;
        return board[row][col] == '-';
    }

    public void makeMove(int square, char player) {
        int index = square - 1;
        int row = index / SIZE;
        int col = index % SIZE;
        board[row][col] = player;
    }

    public boolean hasEmptyCells() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] == '-') return true;
            }
        }
        return false;
    }

    public boolean isWin(char player) {
        for (int i = 0; i < SIZE; i++) {
            if (board[i][0] == player && board[i][1] == player && board[i][2] == player) return true;
            if (board[0][i] == player && board[1][i] == player && board[2][i] == player) return true;
        }
        return (board[0][0] == player && board[1][1] == player && board[2][2] == player) ||
                (board[0][2] == player && board[1][1] == player && board[2][0] == player);
    }

    public int getRandomAvailableMove(Random rng) {
        List<Integer> availableMoves = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] == '-') availableMoves.add(r * SIZE + c + 1);
            }
        }
        return availableMoves.isEmpty() ? -1 : availableMoves.get(rng.nextInt(availableMoves.size()));
    }

    public String getBoardTextBody() {
        String rowDivider = "---+---+---";

        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < 3; ++row) {
            sb.append(String.format(" %1s | %1s | %1s",
                    renderCell(row, 0),
                    renderCell(row, 1),
                    renderCell(row, 2)));
            sb.append("\n");
            if (row <= 1) {
                sb.append(rowDivider).append("\n");
            }
        }
        return sb.toString();
    }

    private String renderCell(int row, int col) {
        if (board[row][col] == '-') {
            return Integer.toString((row * 3) + col + 1);
        }
        return (String.valueOf(board[row][col]));
    }

}
