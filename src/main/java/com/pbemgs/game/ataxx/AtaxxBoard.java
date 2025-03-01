package com.pbemgs.game.ataxx;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.generated.enums.AtaxxGamesBoardOption;
import com.pbemgs.model.Location;
import software.amazon.awssdk.utils.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class AtaxxBoard {

    public record MoveResult(boolean success, String errorMsg) {}

    private static final char EMPTY = '.';
    private static final char OBSTACLE = '#';
    private static final Set<Character> VALID_SYMBOLS = Set.of('x', 'o', '+', '*', '#', '.');
    private static final Map<Integer, Character> PLAYER_SYMBOLS = Map.of(
            0, 'x', 1, 'o', 2, '+', 3, '*'
    );

    private final int size;
    private char[][] board;
    private final Random rng;
    private final LambdaLogger logger;

    public AtaxxBoard(int size, LambdaLogger logger) {
        this.size = size;
        this.board = new char[size][size];
        for (char[] row : board) {
            Arrays.fill(row, EMPTY);
        }
        rng = new Random();
        this.logger = logger;
    }

    public void deserialize(String serialized) {
        String[] rows = serialized.split("\\|");

        for (int r = 0; r < size; r++) {
            if (rows[r].length() != size) {
                throw new IllegalArgumentException("Invalid row length in board serialization.");
            }
            for (int c = 0; c < size; c++) {
                char ch = rows[r].charAt(c);
                if (!VALID_SYMBOLS.contains(ch)) {
                    throw new IllegalArgumentException("Invalid board symbol: " + ch);
                }
                board[r][c] = ch;
            }
        }
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < size; r++) {
            if (r > 0) sb.append("|");
            sb.append(board[r]);
        }
        return sb.toString();
    }

    public char getPlayerSymbol(int playerSlot) {
        return PLAYER_SYMBOLS.get(playerSlot);
    }

    public int getPieceCount(int playerSlot) {
        char symbol = getPlayerSymbol(playerSlot);
        return (int) Arrays.stream(board)
                .flatMapToInt(row -> new String(row).chars())
                .filter(ch -> ch == symbol)
                .count();
    }

    public boolean isBoardFull() {
        for (int r = 0; r < size; ++r) {
            for (int c = 0; c < size; ++c) {
                if (board[r][c] == EMPTY) {
                    return false;
                }
            }
        }
        return true;
    }

    // Process a 2-coordinate move.  Returns true if the move is valid, false (and an error message) if not.
    // Board state is updated (jump and capture) on valid move.
    public MoveResult processMove(int playerSlot, Location from, Location to) {
        char symbol = getPlayerSymbol(playerSlot);
        logger.log("-- processing move - symbol: " + symbol + ", from: " + from.toString() + ", to: " + to.toString());
        if (from.row() >= size || from.col() >= size) {
            return new MoveResult(false, "Invalid move: From square is outside the board.");
        }
        if (to.row() >= size || to.col() >= size) {
            return new MoveResult(false, "Invalid move: To square is outside the board.");
        }
        if (board[from.row()][from.col()] != symbol) {
            return new MoveResult(false, "Invalid move: From square does not have player's piece.");
        }
        if (board[to.row()][to.col()] != EMPTY) {
            return new MoveResult(false, "Invalid move: To square is occupied.");
        }
        int range = getMaxDelta(from, to);
        if (range == 0) {
            return new MoveResult(false, "Invalid move: From and To squares are the same.");
        }
        if (range > 2) {
            return new MoveResult(false, "Invalid move: To is further than 2 squares from the From.");
        }

        createPieceOnMove(symbol, to);
        if (range == 2) {
            board[from.row()][from.col()] = EMPTY;
        }

        logger.log("Move successful: Player: " + symbol + ": From " + from.toString() + " -> " + to.toString() + " - range: " + range);
        return new MoveResult(true, null);
    }

    // Same thing for a to-only clone move
    public MoveResult processMove(int playerSlot, Location to) {
        char symbol = getPlayerSymbol(playerSlot);
        logger.log("-- processing to-only move - symbol: " + symbol + ", to: " + to.toString());
        if (to.row() >= size || to.col() >= size) {
            return new MoveResult(false, "Invalid move: To square is outside the board.");
        }
        if (board[to.row()][to.col()] != EMPTY) {
            return new MoveResult(false, "Invalid move: To square is occupied");
        }
        // Valid if a player's piece is found within 1 of the to-square
        if (findInRange(symbol, to, 1)) {
            createPieceOnMove(symbol, to);
            logger.log("To-Only clone successful: Player: " + symbol + ": To: " + to.toString());
            return new MoveResult(true, null);
        }
        return new MoveResult(false, "Invalid move: No piece within one square of To-Only move.");
    }

    // place-and-capture
    private void createPieceOnMove(char symbol, Location to) {
        board[to.row()][to.col()] = symbol;
        for (int row = Math.max(0, to.row() - 1); row <= Math.min(size - 1, to.row() + 1); ++row) {
            for (int col = Math.max(0, to.col() - 1); col <= Math.min(size - 1, to.col() + 1); ++col) {
                if (board[row][col] != EMPTY && board[row][col] != OBSTACLE) {
                    board[row][col] = symbol;
                }
            }
        }
    }

    // coordinate delta in the largest direction.  (i.e. 0 = same square, 1 = clone, 2 = jump, >2 is invalid
    private int getMaxDelta(Location from, Location to) {
        int dx = to.col() - from.col();
        int dy = to.row() - from.row();
        return Math.max(Math.abs(dx), Math.abs(dy));
    }

    // Check for if a player has a legal move
    public boolean hasLegalMove(int playerSlot) {
        char playerSymbol = getPlayerSymbol(playerSlot);

        // Iterate over all board positions
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (board[row][col] == playerSymbol) {
                    // Check if the player can move from this square (blank within 2)
                    if (findInRange(EMPTY, new Location(row, col), 2)) {
                        return true; // Found at least one valid move
                    }
                }
            }
        }
        return false; // No legal moves found
    }

    // Check if a specific symbol is found within 'range' squares of a location.
    // Note that this will check the location itself!
    private boolean findInRange(char symbol, Location loc, int range) {
        int rowStart = Math.max(0, loc.row() - range);
        int rowEnd = Math.min(size - 1, loc.row() + range);
        int colStart = Math.max(0, loc.col() - range);
        int colEnd = Math.min(size - 1, loc.col() + range);

        for (int row = rowStart; row <= rowEnd; ++row) {
            for (int col = colStart; col <= colEnd; ++col) {
                if (board[row][col] == symbol) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     *  Get a random move for a timeout - this will pick a random clone move if one is available,
     *  otherwise a random jump move.
     *  Pair types used here are from-loc/to-loc.
     */
    public Pair<Location, Location> generateRandomMove(int playerSlot) {
        List<Pair<Location, Location>> cloneMoves = getValidMovesByRange(playerSlot, 1);
        if (cloneMoves.isEmpty()) {
            List<Pair<Location, Location>> jumpMoves = getValidMovesByRange(playerSlot, 2);
            return jumpMoves.get(rng.nextInt(jumpMoves.size()));
        }
        return cloneMoves.get(rng.nextInt(cloneMoves.size()));
    }

    private List<Pair<Location, Location>> getValidMovesByRange(int playerSlot, int range) {
        char playerSymbol = getPlayerSymbol(playerSlot);
        List<Pair<Location, Location>> validMoves = new ArrayList<>();

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (board[row][col] == playerSymbol) {
                    Location from = new Location(row, col);

                    for (int dr = -range; dr <= range; dr++) {
                        for (int dc = -range; dc <= range; dc++) {
                            int toRow = row + dr;
                            int toCol = col + dc;
                            if (toRow >= 0 && toRow < size && toCol >= 0 && toCol < size &&
                                !(dr == 0 && dc == 0) && board[toRow][toCol] == EMPTY) {

                                Location to = new Location(toRow, toCol);
                                validMoves.add(Pair.of(from, to));
                            }
                        }
                    }
                }
            }
        }

        return validMoves;
    }

    public String getBoardTextBody() {
        StringBuilder sb = new StringBuilder("   ");
        for (int i = 0; i < size; i++) {
            sb.append((char) ('A' + i)).append(" ");
        }
        sb.append("\n");

        for (int r = 0; r < size; r++) {
            sb.append(String.format("%2d ", (r + 1)));
            for (int c = 0; c < size; c++) {
                sb.append(board[r][c]).append(" ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public int getSize() {
        return size;
    }

    // Board Creation Logic
    // - Pieces in corners, randomized order for 4P
    // - Obstacles are generated on a quadrant of the board and mirrored symmetrically both directions.
    // - For random placement, number is in a range based on size.
    // - For "standardized" placement, choose a board configuration from preset lists randomly based on the
    //   size of the board.

    /** Entry point, takes player count and obstacle option */
    public void createInitialBoard(int numPlayers, AtaxxGamesBoardOption boardOption) {
        logger.log("ATAXX: Creating initial board with options - numPlayers: " + numPlayers + ", boardType: " + boardOption.getName());
        placeStartingPieces(numPlayers);
        placeObstacles(boardOption);
    }

    /** Places the starting pieces for a 2P or 4P game */
    private void placeStartingPieces(int numPlayers) {
        if (numPlayers == 2) {
            board[0][0] = getPlayerSymbol(0);              // Top-left
            board[size - 1][size - 1] = getPlayerSymbol(0); // Bottom-right
            board[size - 1][0] = getPlayerSymbol(1);       // Bottom-left
            board[0][size - 1] = getPlayerSymbol(1);       // Top-right
        } else if (numPlayers == 4) {
            List<Character> players = new ArrayList<>(
                    List.of(getPlayerSymbol(0), getPlayerSymbol(1), getPlayerSymbol(2), getPlayerSymbol(3)));
            Collections.shuffle(players, rng);
            board[0][0] = players.get(0);
            board[0][size - 1] = players.get(1);
            board[size - 1][0] = players.get(2);
            board[size - 1][size - 1] = players.get(3);
        } else {
            logger.log("-- Invalid number of players: " + numPlayers);
            throw new IllegalArgumentException("Invalid number of players: " + numPlayers);
        }
    }

    /** Places obstacles based on the board option */
    private void placeObstacles(AtaxxGamesBoardOption boardOption) {
        if (boardOption == AtaxxGamesBoardOption.BLANK) {
            return; // No obstacles, return early
        } else if (boardOption == AtaxxGamesBoardOption.STANDARD) {
            applyPresetObstacles();
        } else if (boardOption == AtaxxGamesBoardOption.RANDOM) {
            generateRandomObstacles();
        } else {
            logger.log("-- unknown board option: " + boardOption.getName());
            throw new IllegalArgumentException("Unknown board creation option: " + boardOption.getName());
        }
    }

    /** Generates symmetrical obstacles randomly.  7/8 size boards get 2..3, 9/10 size boards get 3..5, etc. */
    private void generateRandomObstacles() {
        int quadrantSize = (size + 1) / 2;
        int minObstacles = (size - 3) / 2;
        int maxObstacles = minObstacles + ((size - 5) / 2);
        int numObstacles = rng.nextInt(minObstacles, maxObstacles + 1);  // [..)

        int placedCount = 0;
        while (placedCount < numObstacles) {
            int r = rng.nextInt(quadrantSize);
            int c = rng.nextInt(quadrantSize);
            if (board[r][c] == EMPTY) {
                mirrorObstacle(r, c);
                ++placedCount;
            }
        }
    }

    /** Mirrors a generated obstacle across all quadrants */
    private void mirrorObstacle(int r, int c) {
        int mirroredR = size - 1 - r;
        int mirroredC = size - 1 - c;
        board[r][c] = OBSTACLE;
        board[mirroredR][c] = OBSTACLE;
        board[r][mirroredC] = OBSTACLE;
        board[mirroredR][mirroredC] = OBSTACLE;
    }

    /** Applies predefined obstacle layouts */
    private void applyPresetObstacles() {
        List<List<Location>> presets = getPresetObstacles();
        List<Location> chosenPreset = presets.get(rng.nextInt(presets.size()));

        for (Location pos : chosenPreset) {
            mirrorObstacle(pos.row(), pos.col());
        }
    }

    private List<List<Location>> getPresetObstacles() {
        List<List<Location>> basePresets = List.of(
                List.of(new Location(1, 1)),
                List.of(new Location(2, 2)),
                List.of(new Location(3, 3)),
                List.of(new Location(3,2), new Location(2, 3)),
                List.of(new Location(3, 0), new Location(3, 2)),
                List.of(new Location(0, 3), new Location(3, 0)),
                List.of(new Location(3, 1), new Location(1, 2)),
                List.of(new Location(0, 2), new Location(2, 0)),
                List.of(new Location(0, 3), new Location(3, 0), new Location(3, 3)),
                List.of(new Location(1, 1), new Location(2, 2)),
                List.of(new Location(1, 1), new Location(1, 3), new Location(3, 1))
        );

        List<List<Location>> extraPresets = new ArrayList<>(basePresets);

        if (size >= 9) {
            extraPresets.add(List.of(new Location(4, 4)));
            extraPresets.add(List.of(new Location(3, 3), new Location(4, 4)));
            extraPresets.add(List.of(new Location(1, 1), new Location(4, 4)));
            extraPresets.add(List.of(new Location(2, 2), new Location(4, 4)));
            extraPresets.add(List.of(new Location(4, 2), new Location(2, 4)));
            extraPresets.add(List.of(new Location(4, 0), new Location(4, 2)));
            extraPresets.add(List.of(new Location(0, 4), new Location(4, 0)));
            extraPresets.add(List.of(new Location(4, 1), new Location(1, 2)));
            extraPresets.add(List.of(new Location(0, 2), new Location(2, 0)));
            extraPresets.add(List.of(new Location(0, 4), new Location(4, 0), new Location(4, 4)));
            extraPresets.add(List.of(new Location(1, 1), new Location(2, 2), new Location(4, 4)));
            extraPresets.add(List.of(new Location(1, 1), new Location(1, 4), new Location(4, 1)));
            extraPresets.add(List.of(new Location(2, 2), new Location(3, 3), new Location(4, 4)));
            extraPresets.add(List.of(new Location(2, 2), new Location(3, 4), new Location(4, 3)));
        }

        if (size >= 11) {
            extraPresets.add(List.of(new Location(3, 3), new Location(5, 5)));
            extraPresets.add(List.of(new Location(1, 1), new Location(5, 5)));
            extraPresets.add(List.of(new Location(2, 2), new Location(5, 5)));
            extraPresets.add(List.of(new Location(5, 2), new Location(2, 5)));
            extraPresets.add(List.of(new Location(5, 0), new Location(5, 2)));
            extraPresets.add(List.of(new Location(0, 5), new Location(5, 0), new Location(4, 4)));
            extraPresets.add(List.of(new Location(5, 1), new Location(1, 2), new Location(2, 5), new Location(5, 2)));
            extraPresets.add(List.of(new Location(0, 2), new Location(2, 0), new Location(4, 5), new Location(5,4)));
            extraPresets.add(List.of(new Location(0, 5), new Location(5, 0), new Location(5, 5), new Location(3, 3)));
            extraPresets.add(List.of(new Location(1, 1), new Location(2, 2), new Location(5, 5), new Location (3, 3)));
            extraPresets.add(List.of(new Location(1, 1), new Location(1, 5), new Location(5, 1), new Location(4, 4)));
            extraPresets.add(List.of(new Location(3, 3), new Location(5, 5), new Location (4, 4)));
            extraPresets.add(List.of(new Location(1, 1), new Location(5, 5), new Location(3, 3)));
            extraPresets.add(List.of(new Location(2, 2), new Location(5, 5), new Location(0, 5), new Location(5, 0)));
            extraPresets.add(List.of(new Location(5, 2), new Location(2, 5), new Location(2, 1), new Location(1, 2)));
            extraPresets.add(List.of(new Location(5, 0), new Location(5, 2), new Location(5, 4)));
            extraPresets.add(List.of(new Location(0, 5), new Location(5, 0), new Location(1, 1), new Location(2, 2)));
            extraPresets.add(List.of(new Location(5, 1), new Location(1, 2), new Location(4, 5)));
            extraPresets.add(List.of(new Location(0, 5), new Location(5, 0), new Location(5, 5), new Location(0, 1)));
            extraPresets.add(List.of(new Location(1, 1), new Location(2, 2), new Location(3, 3), new Location(5, 5)));
            extraPresets.add(List.of(new Location(1, 1), new Location(1, 5), new Location(5, 1), new Location(2, 4), new Location(4, 2)));

            // remove 1-blot setups on this big a board
            extraPresets.removeIf(r -> r.size() <= 1);
        }

        return extraPresets;
    }
}
