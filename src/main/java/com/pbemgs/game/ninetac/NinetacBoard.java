package com.pbemgs.game.ninetac;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class NinetacBoard {
    public static final int PLAYER_X = -1;  // marker for "X" squares
    public static final int PLAYER_O = -2;  // marker for "O" squares

    private LambdaLogger logger;

    private List<SingleBoard> boards;
    private Set<Integer> availableNumbers;

    public NinetacBoard(LambdaLogger logger) {
        this.logger = logger;
        availableNumbers = new HashSet<>();
        boards = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            boards.add(new SingleBoard());
        }
    }

    // Creates a new board with 27 numbers (3 each)
    // This shuffles the entries, creates a serialized representation.
    // It validates board constraints along the way, retrying with a new ordering
    // if it gets into an "impossible to finish" situation.
    // At the end, it deserializes the generated representation into the actual board
    // data structure.
    // Constraints:
    // - No duplicated center numbers
    // - No duplicate numbers on the same board
    public void createRandomizedBoard27() {
        boolean validBoard = false;
        int retryCount = 0;
        StringBuilder sb = new StringBuilder();
        while (!validBoard) {
            validBoard = true;
            // Step 1: Generate shuffled numbers
            List<Integer> numbers = new ArrayList<>();
            for (int i = 1; i <= 27; i++) {
                numbers.add(i);
                numbers.add(i);
                numbers.add(i);
            }
            Collections.shuffle(numbers);

            // Step 2: Create serialized representation of the shuffled ordering.
            //         Validate candidates against creation rules along the way.
            Set<Integer> centers = new HashSet<>();
            sb = new StringBuilder();
            for (int boardIdx = 0; validBoard && boardIdx < 9; boardIdx++) {
                Set<Integer> thisBoard = new HashSet<>();
                List<Integer> rejects = new ArrayList<>();
                for (int i = 0; validBoard && i < 9; i++) {
                    boolean valid = false;
                    while (!valid) {
                        if (numbers.isEmpty()) {
                            validBoard = false;
                            ++retryCount;
                            logger.log("Board creation failed - boardIdx: " + boardIdx + ", i: " + i);
                            break;
                        }
                        int candidate = numbers.remove(0);
                        if (thisBoard.contains(candidate) ||
                                (i == 4 && centers.contains(candidate))) {
                            rejects.add(candidate);
                        } else {
                            valid = true;
                            sb.append(candidate).append(i < 8 ? "," : "|");
                            thisBoard.add(candidate);
                            if (i == 4) {
                                centers.add(candidate);
                            }
                        }
                    }  // end while (current candidate is not valid)
                }  // end for (cells on current single board)
                numbers.addAll(rejects);
            }  // end for (generate single board)
        }  // end while (valid board)
        sb.deleteCharAt(sb.length() - 1);  // remove trailing pipe
        String serializedBoard = sb.toString();

        // Step 3: Deserialize board
        deserialize(serializedBoard);
        logger.log("Generated board: " + sb.toString() + " - # of retries: " + retryCount);
    }

    public String serialize() {
        return boards.stream().map(SingleBoard::serialize).collect(Collectors.joining("|"));
    }

    public void deserialize(String serializedData) {
        String[] boardStrings = serializedData.split("\\|");
        boards.clear();
        availableNumbers.clear();
        for (String boardString : boardStrings) {
            SingleBoard board = new SingleBoard();
            board.deserialize(boardString);
            boards.add(board);
            availableNumbers.addAll(board.getAvailableNumbers());
        }
    }

    public boolean isMoveValid(int number) {
        return availableNumbers.contains(number);
    }

    public boolean isBoardFull() {
        return availableNumbers.isEmpty();
    }

    public int getRandomMove() {
        int size = availableNumbers.size();
        int itemIndex = new Random().nextInt(size); // Pick a random index
        Iterator<Integer> iterator = availableNumbers.iterator();

        for (int i = 0; i < itemIndex; i++) {
            iterator.next();
        }

        return iterator.next();
    }

    public int getClaimedCount(int player) {
        int count = 0;
        for (SingleBoard board : boards) {
            if (board.getWinner() == player) {
                ++count;
            }
        }
        return count;
    }

    public void makeMove(int player, int number) {
        if (!availableNumbers.contains(number)) {
            throw new IllegalArgumentException("Unavailable number - check isMoveValid() first plz.");
        }

        for (SingleBoard board : boards) {
            board.captureNumber(player, number);
            board.checkWin();
        }

        // Re-calculate available numbers, as winning a board may clear additional options.
        availableNumbers.clear();
        for (SingleBoard board : boards) {
            availableNumbers.addAll(board.getAvailableNumbers());
        }
    }

    // Generate the email body text for the game board (only)
    // The output format is 3 rows of 3 boards, so the first row of text
    // has row 1 of board 0, then row 1 of board 1, then row 1 of board 2.
    public String getBoardTextBody() {
        String boardSpacer = "   ";
        String rowDivider = "---+----+---";

        StringBuilder sb = new StringBuilder();
        // 3 groups of 3 boards
        int firstBoard = 0;  // SingleBoard index on the left side
        for (int boardGroup = 0; boardGroup < 3; ++boardGroup) {
            for (int row = 0; row < 3; ++row) {
                sb.append(boards.get(firstBoard).getRowTextString(row)).append(boardSpacer)
                    .append(boards.get(firstBoard + 1).getRowTextString(row)).append(boardSpacer)
                        .append(boards.get(firstBoard + 2).getRowTextString(row));
                sb.append("\n");
                if (row <= 1) {
                    sb.append(rowDivider).append(boardSpacer)
                            .append(rowDivider).append(boardSpacer)
                            .append(rowDivider).append("\n");
                }
            }
            firstBoard += 3;
            sb.append("\n\n");
        }

        return sb.toString();
    }

    // Unit test support
    @VisibleForTesting
    /* package-private */
    SingleBoard getBoard(int index) {
        return boards.get(index);
    }
}