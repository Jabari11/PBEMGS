package com.pbemgs.game.ninetac;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class NinetacBoardTest {

    private static LambdaLogger mockLogger = mock(LambdaLogger.class);

    // helper to validate valid board for the base-27 initialization
    private boolean isBoardValid27(NinetacBoard board) {
        // all boards have 9 unique numbers, centers are unique.
        boolean valid = true;
        Set<Integer> centers = new HashSet<>();
        for (int x = 0; x < 9; ++x) {
            SingleBoard sb = board.getBoard(x);
            if (centers.contains(sb.getCenter())) {
                valid = false;
            }
            centers.add(sb.getCenter());
            if (sb.getAvailableNumbers().size() < 9) {
                valid = false;
            }
        }
        return valid;
    }

    @Test
    void testSerializationRoundtrip() {
        String boardString = "1,2,3,4,5,6,7,8,9|10,11,12,13,14,15,16,17,18|19,20,21,22,23,24,25,26,27|" +
                "1,2,3,4,5,6,7,8,9|10,11,12,13,14,15,16,17,18|19,20,21,22,23,24,25,26,27|" +
                "1,2,3,4,5,6,7,8,9|10,11,12,13,14,15,16,17,18|19,20,21,22,23,24,25,26,27";

        NinetacBoard board = new NinetacBoard(mockLogger);
        board.deserialize(boardString);
        String reserialize = board.serialize();
        assertEquals(boardString, reserialize);
    }

    @Test
    void testBase27BoardGeneration() {
        for (int x = 0; x < 30; ++x) {
            NinetacBoard newBoard = new NinetacBoard(mockLogger);
            newBoard.createRandomizedBoard27();
            assertTrue(isBoardValid27(newBoard), "Invalid board: " + newBoard.serialize());
        }
    }


}
