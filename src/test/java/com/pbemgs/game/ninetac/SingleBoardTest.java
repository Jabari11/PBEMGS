package com.pbemgs.game.ninetac;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SingleBoardTest {
    @Test
    void testInitialBoardState() {
        String initialBoard = "1,3,5,7,9,11,13,15,17";
        SingleBoard board = new SingleBoard();
        board.deserialize(initialBoard);
        assertEquals(Set.of(1, 3, 5, 7, 9, 11, 13, 15, 17), board.getAvailableNumbers());
        assertEquals(9, board.getCenter());
        assertEquals(0, board.getWinner());
        assertFalse(board.checkWin());
        String serialize = board.serialize();
        assertEquals(initialBoard, serialize);
    }

    @Test
    void testWonBoardState() {
        String initialBoard = "W:1";
        SingleBoard board = new SingleBoard();
        board.deserialize(initialBoard);
        assertEquals(Set.of(), board.getAvailableNumbers());
        assertEquals(-1, board.getCenter());
        assertEquals(NinetacBoard.PLAYER_X, board.getWinner());
        assertTrue(board.checkWin());
        String serialize = board.serialize();
        assertEquals(initialBoard, serialize);
    }

    @Test
    void testCaptureIntermediate() {
        String initialBoard = "1,-1,5,7,-1,-2,-2,15,17";
        SingleBoard board = new SingleBoard();
        board.deserialize(initialBoard);
        assertEquals(Set.of(1, 5, 7, 15, 17), board.getAvailableNumbers());
        assertEquals(-1, board.getCenter());
        assertEquals(0, board.getWinner());
        board.captureNumber(NinetacBoard.PLAYER_O, 15);
        assertFalse(board.checkWin());
        assertEquals(Set.of(1, 5, 7, 17), board.getAvailableNumbers());  // 15 captured
        String serialize = board.serialize();
        assertEquals("1,-1,5,7,-1,-2,-2,-2,17", serialize);
    }

    @Test
    void testCaptureNone() {
        // "Capturing" a number not on the board is legitimate, as the controller can
        // blindly just call capture() on all boards instead of checking first.
        String initialBoard = "1,-1,5,7,-1,-2,-2,15,17";
        SingleBoard board = new SingleBoard();
        board.deserialize(initialBoard);
        assertEquals(Set.of(1, 5, 7, 15, 17), board.getAvailableNumbers());
        assertEquals(-1, board.getCenter());
        assertEquals(0, board.getWinner());
        board.captureNumber(NinetacBoard.PLAYER_O, 2);
        assertFalse(board.checkWin());
        assertEquals(Set.of(1, 5, 7, 15, 17), board.getAvailableNumbers());
        String serialize = board.serialize();
        assertEquals(initialBoard, serialize);
    }

    @Test
    void testMoveDraw() {
        String initialBoard = "22,-1,-2,-1,-1,-2,-2,-2,-1";
        SingleBoard board = new SingleBoard();
        board.deserialize(initialBoard);
        assertEquals(Set.of(22), board.getAvailableNumbers());
        assertEquals(-1, board.getCenter());
        assertEquals(0, board.getWinner());
        board.captureNumber(NinetacBoard.PLAYER_O, 22);
        assertFalse(board.checkWin());
        assertEquals(Set.of(), board.getAvailableNumbers());  // none left
        String serialize = board.serialize();
        assertEquals("-2,-1,-2,-1,-1,-2,-2,-2,-1", serialize);
    }

    @Test
    void testLoadDraw() {
        String initialBoard = "-1,-2,-1,-1,-2,-2,-2,-1,-1";
        SingleBoard board = new SingleBoard();
        board.deserialize(initialBoard);
        assertEquals(Set.of(), board.getAvailableNumbers());
        assertEquals(-2, board.getCenter());
        assertEquals(0, board.getWinner());
        String serialize = board.serialize();
        assertEquals(initialBoard, serialize);
    }
}
