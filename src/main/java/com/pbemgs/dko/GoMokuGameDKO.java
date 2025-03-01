package com.pbemgs.dko;

import com.pbemgs.generated.enums.GomokuGamesGameState;
import com.pbemgs.generated.enums.GomokuGamesSwap2State;
import com.pbemgs.generated.tables.records.GomokuGamesRecord;
import org.jooq.DSLContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.pbemgs.generated.tables.GomokuGames.GOMOKU_GAMES;

public class GoMokuGameDKO {

    private final DSLContext dslContext;

    public GoMokuGameDKO(DSLContext jooqContext) {
        dslContext = jooqContext;
    }

    // Get game by game ID
    public GomokuGamesRecord getGameById(Long gameId) {
        return dslContext.selectFrom(GOMOKU_GAMES)
                .where(GOMOKU_GAMES.GAME_ID.eq(gameId))
                .fetchOne();
    }

    // Get all games in the OPEN state
    public List<GomokuGamesRecord> getOpenGames() {
        return dslContext.selectFrom(GOMOKU_GAMES)
                .where(GOMOKU_GAMES.GAME_STATE.eq(GomokuGamesGameState.OPEN))
                .fetchInto(GomokuGamesRecord.class);
    }

    // Get all active games for a user (OPEN or IN_PROGRESS)
    public List<GomokuGamesRecord> getActiveGamesForUser(Long playerId) {
        return dslContext.selectFrom(GOMOKU_GAMES)
                .where(GOMOKU_GAMES.GAME_STATE.in(GomokuGamesGameState.OPEN, GomokuGamesGameState.IN_PROGRESS)
                        .and(GOMOKU_GAMES.X_PLAYER_ID.eq(playerId).or(GOMOKU_GAMES.O_PLAYER_ID.eq(playerId))))
                .fetchInto(GomokuGamesRecord.class);
    }

    // Create a new game
    public Long createNewGame(Long xPlayerId, String boardState, int boardSize) {
        GomokuGamesRecord record = dslContext.newRecord(GOMOKU_GAMES);
        record.setGameState(GomokuGamesGameState.OPEN);
        record.setXPlayerId(xPlayerId);
        record.setBoardState(boardState);
        record.setBoardSize(boardSize);
        record.setLastMoveTimestamp(LocalDateTime.now());

        record.setLastReminderTimestamp(null);
        record.store();
        return record.getGameId();
    }

    // Complete game creation by setting both player IDs (could swap from creator), and the swap2 state
    public void completeGameCreation(Long gameId, Long xPlayerId, Long oPlayerId) {
        dslContext.update(GOMOKU_GAMES)
                .set(GOMOKU_GAMES.GAME_STATE, GomokuGamesGameState.IN_PROGRESS)
                .set(GOMOKU_GAMES.X_PLAYER_ID, xPlayerId)
                .set(GOMOKU_GAMES.O_PLAYER_ID, oPlayerId)
                .set(GOMOKU_GAMES.PLAYER_ID_TO_MOVE, xPlayerId)
                .set(GOMOKU_GAMES.SWAP2_STATE, GomokuGamesSwap2State.AWAITING_INITIAL_PLACEMENT)
                .where(GOMOKU_GAMES.GAME_ID.eq(gameId))
                .execute();
    }

    // Update state in an existing game
    public void updateGame(GomokuGamesRecord gameRecord) {
        // Ensure the gameRecord has a valid primary key (GameID) to update
        if (gameRecord.getGameId() == null) {
            throw new IllegalArgumentException("Cannot update game: GameID is null.");
        }

        int rowsUpdated = dslContext.update(GOMOKU_GAMES)
                .set(GOMOKU_GAMES.GAME_STATE, gameRecord.getGameState())
                .set(GOMOKU_GAMES.BOARD_STATE, gameRecord.getBoardState())
                .set(GOMOKU_GAMES.SWAP2_STATE, gameRecord.getSwap2State())
                .set(GOMOKU_GAMES.PLAYER_ID_TO_MOVE, gameRecord.getPlayerIdToMove())
                .set(GOMOKU_GAMES.LAST_MOVE_TIMESTAMP, gameRecord.getLastMoveTimestamp())
                .set(GOMOKU_GAMES.VICTOR_PLAYER_ID, gameRecord.getVictorPlayerId())
                .set(GOMOKU_GAMES.LAST_REMINDER_TIMESTAMP, gameRecord.getLastMoveTimestamp())
                .where(GOMOKU_GAMES.GAME_ID.eq(gameRecord.getGameId()))
                .execute();

        if (rowsUpdated == 0) {
            throw new IllegalArgumentException("Cannot update game - game doesn't exist!");
        }
    }

    public void updateReminderTimestamps(Set<Long> userIds, LocalDateTime updateTo) {
        dslContext.update(GOMOKU_GAMES)
                .set(GOMOKU_GAMES.LAST_REMINDER_TIMESTAMP, updateTo)
                .where(GOMOKU_GAMES.PLAYER_ID_TO_MOVE.in(userIds))
                .execute();
    }

    public List<GomokuGamesRecord> getActiveGames() {
        return dslContext.selectFrom(GOMOKU_GAMES)
                .where(GOMOKU_GAMES.GAME_STATE.eq(GomokuGamesGameState.IN_PROGRESS))
                .fetchInto(GomokuGamesRecord.class);
    }
}
