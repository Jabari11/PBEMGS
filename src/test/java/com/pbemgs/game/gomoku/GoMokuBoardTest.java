package com.pbemgs.game.gomoku;

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

public class GoMokuBoardTest {

    private static final LambdaLogger mockLogger = mock(LambdaLogger.class);
    private GoMokuBoard board;

    @BeforeEach
    public void setUp() {
        board = new GoMokuBoard(15, mockLogger); // 15x15 default
    }

    private String testBoardString() {
        // 15x15 snippet with some stones
        return "...............|" +
                "...............|" +
                "...X...........|" +
                "....O......O...|" +
                ".....X.........|" +
                "......X........|" +
                ".......O.......|" +
                "........X......|" +
                "...............|" +
                "...............|" +
                "...............|" +
                "...............|" +
                "...............|" +
                "...............|" +
                "...............";
    }

    @Test
    public void testCreateNewGame_initialSetup() {
        // Gomoku starts empty—no initial pieces
        for (int r = 0; r < 15; r++) {
            for (int c = 0; c < 15; c++) {
                assertEquals(GoMokuBoard.EMPTY, board.getGrid(r, c), "Empty at " + r + "," + c);
            }
        }
    }

    @Test
    public void testValidateMove_legalMoves() {
        board.deserialize(testBoardString());
        // Empty spot—legal
        Location h8 = Location.fromString("H8");
        assertNull(board.validateMove(h8), "H8 legal");

        // Another empty spot
        Location a1 = Location.fromString("A1");
        assertNull(board.validateMove(a1), "A1 legal");
    }

    @Test
    public void testValidateMove_illegalMoves() {
        board.deserialize(testBoardString());
        // Occupied spot
        Location d3 = Location.fromString("D3");
        assertNotNull(board.validateMove(d3), "D3 occupied");

        // Off-board
        Location off = Location.fromString("P16");
        assertNotNull(board.validateMove(off), "P16 off-board");
    }

    @Test
    public void testMakeMove() {
        board.deserialize(testBoardString());
        Location h8 = Location.fromString("H8");
        assertNull(board.validateMove(h8));
        board.makeMove(GoMokuBoard.PLAYER_X, h8);
        assertEquals(GoMokuBoard.PLAYER_X, board.getGrid(7, 7), "H8 has X");
    }

    @Test
    public void testIsVictoryCondition_noWinAtStart() {
        // Empty board—no win
        assertFalse(board.isVictoryCondition(GoMokuBoard.PLAYER_X), "X no win at start");
        assertFalse(board.isVictoryCondition(GoMokuBoard.PLAYER_O), "O no win at start");
    }

    @Test
    public void testIsVictoryCondition_fiveInRowWins() {
        // X wins horizontally: H8-L8
        String winState = "...............|" +
                "...............|" +
                "...............|" +
                "...............|" +
                "...............|" +
                "...............|" +
                "...............|" +
                ".......XXXXX...|" +
                "..............O|" +
                "...............|" +
                "...............|" +
                "...............|" +
                "...............|" +
                "...............|" +
                "...............";
        board.deserialize(winState);
        assertTrue(board.isVictoryCondition(GoMokuBoard.PLAYER_X), "X wins with five in a row");
        assertFalse(board.isVictoryCondition(GoMokuBoard.PLAYER_O), "O does not win");
    }

    @Test
    public void testIsBoardFull() {
        assertFalse(board.isBoardFull(), "Empty board not full");
        // Fill board manually (simplified test)
        for (int r = 0; r < 15; r++) {
            for (int c = 0; c < 15; c++) {
                board.makeMove(GoMokuBoard.PLAYER_X, new Location(r, c));
            }
        }
        assertTrue(board.isBoardFull(), "Full board detected");
    }

    @Test
    public void testGetRandomMove_returnsValidMove() {
        board.deserialize(testBoardString());
        Location move = board.getRandomMove();
        assertNotNull(move, "Random move exists");
        assertNull(board.validateMove(move), "Random move is valid");
        assertEquals(GoMokuBoard.EMPTY, board.getGrid(move.row(), move.col()), "Move targets empty spot");
    }

    @Test
    public void testSerializeDeserialize_roundTrip() {
        board.deserialize(testBoardString());
        String serialized = board.serialize();
        GoMokuBoard newBoard = new GoMokuBoard(15, mockLogger);
        newBoard.deserialize(serialized);
        String reserialized = newBoard.serialize();
        assertEquals(serialized, reserialized, "Serialization round-trip");
    }
}
