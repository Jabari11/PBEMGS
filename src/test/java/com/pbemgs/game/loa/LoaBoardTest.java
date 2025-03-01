package com.pbemgs.game.loa;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.model.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class LoaBoardTest {
    private static LambdaLogger mockLogger = mock(LambdaLogger.class);

    private LoaBoard board;

    @BeforeEach
    public void setUp() {
        board = new LoaBoard(mockLogger);
    }

    private String testBoardString() {
        return ".XX...X.|" +
               "........|" +
               "...OXX.O|" +
               "..OOX..O|" +
               "........|" +
               "O..XO.O.|" +
               "..O....X|" +
               ".X..X...";
    }
    @Test
    public void testCreateNewGame_initialSetup() {
        board.createNewGame();
        // Check X: cols A, H, rows 1-6
        assertEquals(LoaBoard.PLAYER_X, board.getGrid(1, 0), "X at A2");
        assertEquals(LoaBoard.PLAYER_X, board.getGrid(5, 7), "X at H6");
        assertEquals(LoaBoard.EMPTY, board.getGrid(0, 0), "No X at A1"); // Edge empty
        // Check O: rows 1, 8, cols B-G
        assertEquals(LoaBoard.PLAYER_O, board.getGrid(0, 1), "O at B1");
        assertEquals(LoaBoard.PLAYER_O, board.getGrid(7, 5), "O at F8");
        assertEquals(LoaBoard.EMPTY, board.getGrid(7, 7), "No O at H8");
        // Piece counts
        assertEquals(12, board.getPieceCount(LoaBoard.PLAYER_X), "12 Xs");
        assertEquals(12, board.getPieceCount(LoaBoard.PLAYER_O), "12 Os");
    }

    @Test
    public void testPieceCount() {
        board.deserialize(testBoardString());
        assertEquals(10, board.getPieceCount(LoaBoard.PLAYER_X), "10 Xs");
        assertEquals(9, board.getPieceCount(LoaBoard.PLAYER_O), "9 Os");
    }

    @Test
    public void testValidateMove_legalMoves() {
        board.deserialize(testBoardString());
        // C1-F1 - a basic horizontal move
        Location fromC1 = Location.fromString("C1");
        Location toF1 = Location.fromString("F1");
        assertNull(board.validateMove(fromC1, toF1, LoaBoard.PLAYER_X), "C1-F1 legal");

        // A6-A5 - a basic vertical move
        Location fromA6 = Location.fromString("A6");
        Location toA5 = Location.fromString("A5");
        assertNull(board.validateMove(fromA6, toA5, LoaBoard.PLAYER_O), "A6-A5 legal");

        // F3-H1 - diagonal NE
        Location fromF3 = Location.fromString("F3");
        Location toH1 = Location.fromString("H1");
        assertNull(board.validateMove(fromF3, toH1, LoaBoard.PLAYER_X), "F3-H1 legal");

        // E3-G5 - diagonal SE
        Location fromE3 = Location.fromString("E3");
        Location toG5 = Location.fromString("G5");
        assertNull(board.validateMove(fromE3, toG5, LoaBoard.PLAYER_X), "E3-G5 legal");

        // G1-D4 - diagonal SW, jump + capture
        Location fromG1 = Location.fromString("G1");
        Location toD4 = Location.fromString("D4");
        assertNull(board.validateMove(fromG1, toD4, LoaBoard.PLAYER_X), "G1-D4 legal");
    }

    @Test
    public void testValidateMove_illegalMoves() {
        board.deserialize(testBoardString());

        // wrong player
        Location fromB1 = Location.fromString("B1");
        Location toE1 = Location.fromString("E1");
        assertNotNull(board.validateMove(fromB1, toE1, LoaBoard.PLAYER_O), "B1-E1 wrong player");

        // not aligned  (B1-C2)
        Location toC2 = Location.fromString("C2");
        assertNotNull(board.validateMove(fromB1, toC2, LoaBoard.PLAYER_X), "B1-C2 not straight");

        // wrong dist
        Location toD1 = Location.fromString("D1");
        assertNotNull(board.validateMove(fromB1, toD1, LoaBoard.PLAYER_X), "B1-D1 wrong distance");

        // Jump enemy piece
        Location fromE6 = Location.fromString("E6");
        Location toE3 = Location.fromString("E3");
        assertNotNull(board.validateMove(fromE6, toE3, LoaBoard.PLAYER_O), "E6-E3 jumps enemy");

        // Jump enemy piece
        Location fromG6 = Location.fromString("G6");
        Location toB6 = Location.fromString("B6");
        assertNotNull(board.validateMove(fromG6, toB6, LoaBoard.PLAYER_O), "G6-B6 jumps enemy");

        // Off-board
        Location fromH7 = Location.fromString("H7");
        Location offBoard = Location.fromString("J7");
        assertNotNull(board.validateMove(fromH7, offBoard, LoaBoard.PLAYER_X), "Off-board move");
    }

    @Test
    public void testMakeMove() {
        board.deserialize(testBoardString());
        Location fromC1 = Location.fromString("C1");
        Location toF1 = Location.fromString("F1");
        LinesOfAction.LoaMove moveX = new LinesOfAction.LoaMove(fromC1, toF1);
        String errorX = board.validateMove(fromC1, toF1, LoaBoard.PLAYER_X);
        assertNull(errorX);
        boolean xCapt = board.makeMove(LoaBoard.PLAYER_X, moveX);
        assertFalse(xCapt, "X move doesn't capture");
        assertEquals(LoaBoard.EMPTY, board.getGrid(0, 2), "C1 empty");
        assertEquals(LoaBoard.PLAYER_X, board.getGrid(0, 5), "F1 has X");
        assertEquals(10, board.getPieceCount(LoaBoard.PLAYER_X), "still 10 Xs");

        Location fromH3 = Location.fromString("H3");
        LinesOfAction.LoaMove moveO = new LinesOfAction.LoaMove(fromH3, toF1);
        String errorO = board.validateMove(fromH3, toF1, LoaBoard.PLAYER_O);
        assertNull(errorO);
        boolean oCapt = board.makeMove(LoaBoard.PLAYER_O, moveO);
        assertTrue(oCapt, "O move captures");
        assertEquals(LoaBoard.EMPTY, board.getGrid(2, 7), "H3 empty");
        assertEquals(LoaBoard.PLAYER_O, board.getGrid(0, 5), "F1 has O");
        assertEquals(9, board.getPieceCount(LoaBoard.PLAYER_O), "still 9 Os");
        assertEquals(9, board.getPieceCount(LoaBoard.PLAYER_X), "now 9 Xs");
    }

    @Test
    public void testIsVictoryCondition_startIsFalse() {
        board.createNewGame();
        assertFalse(board.isVictoryCondition(LoaBoard.PLAYER_X), "X not connected at start");
        assertFalse(board.isVictoryCondition(LoaBoard.PLAYER_O), "O not connected at start");
    }

    @Test
    public void testIsVictoryCondition_connectedWins() {
        // Setup winning state (X connected, O split)
        String winState = "........|........|..XXO...|..XXX.O.|O.X.XO..|.OXXO...|O..O....|........";
        board.deserialize(winState);
        assertTrue(board.isVictoryCondition(LoaBoard.PLAYER_X), "X wins when connected");
        assertFalse(board.isVictoryCondition(LoaBoard.PLAYER_O), "O does not win when split");
    }

    @Test
    public void testGetRandomMove_returnsValidMove() {
        board.createNewGame();
        LinesOfAction.LoaMove move = board.getRandomMove(LoaBoard.PLAYER_X);
        assertNotNull(move, "Random move exists");
        assertNull(board.validateMove(move.from(), move.to(), LoaBoard.PLAYER_X), "Random move is valid");
        // Check it’s X’s piece
        assertEquals(LoaBoard.PLAYER_X, board.getGrid(move.from().row(), move.from().col()), "From has X");
    }

    @Test
    public void testSerializeDeserialize_roundTrip() {
        board.createNewGame();
        String serialized = board.serialize();
        LoaBoard newBoard = new LoaBoard(mockLogger);
        newBoard.deserialize(serialized);
        String reserialize = newBoard.serialize();
        assertEquals(reserialize, serialized, "Serialization round-trip");
    }
}
