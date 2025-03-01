package com.pbemgs.game.loa;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.VisibleForTesting;
import com.pbemgs.model.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class LoaBoard {
    public final static char PLAYER_X = 'X';
    public final static char PLAYER_O = 'O';
    public final static char EMPTY = '.';
    private final static int BOARD_SIZE = 8;

    // separate chars for white and black empty squares because diags are hard to count.
    private final static char WHITE_SQUARE = '.';
    private final static char BLACK_SQUARE = ':';

    private LambdaLogger logger;

    private char[][] grid;


    public LoaBoard(LambdaLogger logger) {
        this.logger = logger;
        grid = new char[BOARD_SIZE][BOARD_SIZE];
        initializeBlank();
    }

    private void initializeBlank() {
        for (int r = 0; r < BOARD_SIZE; ++r) {
            for (int c = 0; c < BOARD_SIZE; ++c) {
                grid[r][c] = EMPTY;
            }
        }
    }

    /**
     * Creates a new game - LOA initial board has a set state.
     */
    public void createNewGame() {
        for (int x = 1; x <= 6 ; ++x) {
            grid[0][x] = PLAYER_O;
            grid[7][x] = PLAYER_O;
            grid[x][0] = PLAYER_X;
            grid[x][7] = PLAYER_X;
        }
    }

    public String serialize() {
        return Arrays.stream(grid)
                .map(String::new)
                .collect(Collectors.joining("|"));
    }

    public void deserialize(String serializedData) {
        String[] rows = serializedData.split("\\|");
        for (int r = 0; r < BOARD_SIZE; r++) {
            grid[r] = rows[r].toCharArray();
        }
    }

    /**
     *  Validate a move for the symbol.  Returns an error message string if there is
     *  an error, or null if the move is valid.
     */
    public String validateMove(Location from, Location to, char symbol) {
        if (!isOnBoard(from)) {
            return "From Location " + from.toString() + " is not on the board.";
        }
        if (!isOnBoard(to)) {
            return "To Location " + to.toString() + " is not on the board.";
        }
        if (grid[from.row()][from.col()] != symbol) {
            return "From Location " + from.toString() + " does not contain a player's piece!";
        }
        if (grid[to.row()][to.col()] == symbol) {
            return "To Location " + to.toString() + " contains your own piece - this is not allowed!";
        }
        int dr = to.row() - from.row();
        int dc = to.col() - from.col();
        if (dr != 0 && dc != 0 && Math.abs(dr) != Math.abs(dc)) {
            return "Move must be in a straight line (horizontal, vertical, diagonal)!";
        }
        int moveDist = (dr != 0) ? Math.abs(dr) : Math.abs(dc);  // if diag, |dr| == |dc|

        int stepR = Integer.signum(dr);
        int stepC = Integer.signum(dc);

        // count pieces in line
        int count = 1;  // self
        for (int offset = 1; offset < BOARD_SIZE; ++offset) {
            Location offFwd = new Location(from.row() + stepR * offset, from.col() + stepC * offset);
            Location offBck = new Location(from.row() - stepR * offset, from.col() - stepC * offset);
            if (isOnBoard(offFwd) && grid[offFwd.row()][offFwd.col()] != EMPTY) {
                ++count;
            }
            if (isOnBoard(offBck) && grid[offBck.row()][offBck.col()] != EMPTY) {
                ++count;
            }
        }
        if (count != moveDist) {
            return ("Invalid move distance.  There are " + count + " pieces along the line, but move distance was " + moveDist + ".");
        }

        // Check for jumping enemy piece
        for (int step = 1; step < moveDist; ++step) {
            int checkR = from.row() + stepR * step;
            int checkC = from.col() + stepC * step;
            if (grid[checkR][checkC] != EMPTY && grid[checkR][checkC] != symbol) {
                return ("Invalid move, jumping over an enemy piece at " + new Location(checkR, checkC).toString() + ".");
            }
        }

        return null;
    }

    public boolean isVictoryCondition(char symbol) {
        Location loc = findAPiece(symbol);
        return getPieceCount(symbol) == countConnectedPieces(symbol, loc);
    }

    public int getPieceCount(char symbol) {
        int count = 0;
        for (int r = 0; r < BOARD_SIZE; ++r) {
            for (int c = 0; c < BOARD_SIZE; ++c) {
                if (grid[r][c] == symbol) {
                    ++count;
                }
            }
        }
        return count;
    }

    /**
     *  Execute the move - assumed validated already.
     *  Returns true if a piece was captured.
     */
    public boolean makeMove(char symbol, LinesOfAction.LoaMove move) {
        boolean capture = grid[move.to().row()][move.to().col()] != EMPTY;
        grid[move.from().row()][move.from().col()] = EMPTY;
        grid[move.to().row()][move.to().col()] = symbol;
        return capture;
    }

    public LinesOfAction.LoaMove getRandomMove(char symbol) {
        List<Location> pieces = new ArrayList<>();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (grid[r][c] == symbol) {
                    pieces.add(new Location(r, c));
                }
            }
        }
        if (pieces.isEmpty()) {
            throw new IllegalStateException("No pieces for symbol " + symbol);
        }
        Collections.shuffle(pieces); // Randomize piece order
        Random rng = new Random();
        int[] deltas = {-1, 0, 1}; // For 8 directions
        for (Location from : pieces) {
            List<Location> possibleTos = new ArrayList<>();
            for (int dr : deltas) {
                for (int dc : deltas) {
                    if (dr == 0 && dc == 0) continue; // Skip no-move
                    int count = countPiecesInLine(from, dr, dc);
                    int toR = from.row() + dr * count;
                    int toC = from.col() + dc * count;
                    Location to = new Location(toR, toC);
                    if (validateMove(from, to, symbol) == null) {
                        possibleTos.add(to);
                    }
                }
            }
            if (!possibleTos.isEmpty()) {
                return new LinesOfAction.LoaMove(from, possibleTos.get(rng.nextInt(possibleTos.size())));
            }
        }
        return null; // No valid moves—pass (rare, LOA’s open early)
    }

    private int countPiecesInLine(Location from, int dr, int dc) {
        int count = 1; // Self
        for (int offset = 1; offset < BOARD_SIZE; offset++) {
            Location fwd = new Location(from.row() + dr * offset, from.col() + dc * offset);
            Location bck = new Location(from.row() - dr * offset, from.col() - dc * offset);
            if (isOnBoard(fwd) && grid[fwd.row()][fwd.col()] != EMPTY) count++;
            if (isOnBoard(bck) && grid[bck.row()][bck.col()] != EMPTY) count++;
        }
        return count;
    }
    /**
     *  Generates the board text.
     */
    public String getBoardTextBody() {
        StringBuilder sb = new StringBuilder("    ");
        for (int i = 0; i < BOARD_SIZE; i++) {
            sb.append((char) ('A' + i)).append(" ");
        }
        sb.append("\n");

        for (int r = 0; r < BOARD_SIZE; r++) {
            sb.append(String.format("%2d  ", (r + 1)));
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (grid[r][c] == EMPTY) {
                    sb.append((r + c) % 2 == 0 ? WHITE_SQUARE : BLACK_SQUARE);
                } else {
                    sb.append(grid[r][c]);
                }
                sb.append(" ");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private Location findAPiece(char symbol) {
        for (int r = 0; r < BOARD_SIZE; ++r) {
            for (int c = 0; c < BOARD_SIZE; ++c) {
                if (grid[r][c] == symbol) {
                    return new Location(r, c);
                }
            }
        }
        throw new IllegalStateException("LOA board has no pieces of symbol " + symbol);
    }

    /**
     *  Return a count of the number of pieces connected (8 directions) to this one.
     *  This is the victory condition check (connected == total)
     */
    private int countConnectedPieces(char symbol, Location loc) {
        Set<Location> group = new HashSet<>();  // grouped pieces with this one
        group.add(loc);
        createGroup(symbol, loc, group);
        return group.size();
    }

    // DFS to create the group
    private void createGroup(char symbol, Location loc, Set<Location> group) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = loc.row() + dr;
                int nc = loc.col() + dc;
                Location newLoc = new Location(nr, nc);
                if (isOnBoard(newLoc) && grid[nr][nc] == symbol) {
                    if (!group.contains(newLoc)) {
                        group.add(newLoc);
                        createGroup(symbol, newLoc, group);
                    }
                }
            }
        }
    }

    private boolean isOnBoard(Location loc) {
        return (loc.row() >= 0 && loc.row() < BOARD_SIZE && loc.col() >= 0 && loc.col() < BOARD_SIZE);
    }

    // Unit test support
    @VisibleForTesting
    public char getGrid(int r, int c) {
        return grid[r][c];
    }
}