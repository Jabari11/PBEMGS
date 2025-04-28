package com.pbemgs.dko;

import com.pbemgs.generated.enums.LoaGamesGameState;
import com.pbemgs.generated.tables.records.LoaGamesRecord;
import org.jooq.DSLContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.pbemgs.generated.tables.LoaGames.LOA_GAMES;

public class LoaGameDKO {

    private final DSLContext dslContext;

    public LoaGameDKO(DSLContext jooqContext) {
        dslContext = jooqContext;
    }

    // Get game by game ID
    public LoaGamesRecord getGameById(Long gameId) {
        return dslContext.selectFrom(LOA_GAMES)
                .where(LOA_GAMES.GAME_ID.eq(gameId))
                .fetchOne();
    }

    // Get all games in the OPEN state
    public List<LoaGamesRecord> getOpenGames() {
        return dslContext.selectFrom(LOA_GAMES)
                .where(LOA_GAMES.GAME_STATE.eq(LoaGamesGameState.OPEN))
                .fetchInto(LoaGamesRecord.class);
    }

    // Get all active games for a user (OPEN or IN_PROGRESS)
    public List<LoaGamesRecord> getActiveGamesForUser(Long userId) {
        return dslContext.selectFrom(LOA_GAMES)
                .where(LOA_GAMES.GAME_STATE.in(LoaGamesGameState.OPEN, LoaGamesGameState.IN_PROGRESS)
                        .and(LOA_GAMES.X_USER_ID.eq(userId).or(LOA_GAMES.O_USER_ID.eq(userId))))
                .fetchInto(LoaGamesRecord.class);
    }

    // Create a new game
    public Long createNewGame(Long xUserId, String boardState) {
        LoaGamesRecord record = dslContext.newRecord(LOA_GAMES);
        record.setGameState(LoaGamesGameState.OPEN);
        record.setXUserId(xUserId);
        record.setBoardState(boardState);
        record.setLastMoveTimestamp(LocalDateTime.now());
        record.setLastReminderTimestamp(null);
        record.store();
        return record.getGameId();

    }

    // Complete game creation by setting the O player
    public void completeGameCreation(Long gameId, Long oUserId, Long firstUserId) {
        dslContext.update(LOA_GAMES)
                .set(LOA_GAMES.GAME_STATE, LoaGamesGameState.IN_PROGRESS)
                .set(LOA_GAMES.O_USER_ID, oUserId)
                .set(LOA_GAMES.USER_ID_TO_MOVE, firstUserId)
                .set(LOA_GAMES.STARTING_USER_ID, firstUserId)
                .where(LOA_GAMES.GAME_ID.eq(gameId))
                .execute();
    }

    // Update state in an existing game
    public void updateGame(LoaGamesRecord gameRecord) {
        // Ensure the gameRecord has a valid primary key (GameID) to update
        if (gameRecord.getGameId() == null) {
            throw new IllegalArgumentException("Cannot update game: GameID is null.");
        }

        int rowsUpdated = dslContext.update(LOA_GAMES)
                .set(LOA_GAMES.GAME_STATE, gameRecord.getGameState())
                .set(LOA_GAMES.BOARD_STATE, gameRecord.getBoardState())
                .set(LOA_GAMES.USER_ID_TO_MOVE, gameRecord.getUserIdToMove())
                .set(LOA_GAMES.LAST_MOVE_TIMESTAMP, gameRecord.getLastMoveTimestamp())
                .set(LOA_GAMES.LAST_REMINDER_TIMESTAMP, gameRecord.getLastMoveTimestamp())
                .where(LOA_GAMES.GAME_ID.eq(gameRecord.getGameId()))
                .execute();

        if (rowsUpdated == 0) {
            throw new IllegalArgumentException("Cannot update game - game doesn't exist!");
        }
    }

    public void updateReminderTimestamps(Set<Long> userIds, LocalDateTime updateTo) {
        dslContext.update(LOA_GAMES)
                .set(LOA_GAMES.LAST_REMINDER_TIMESTAMP, updateTo)
                .where(LOA_GAMES.USER_ID_TO_MOVE.in(userIds))
                .execute();
    }

    public List<LoaGamesRecord> getActiveGames() {
        return dslContext.selectFrom(LOA_GAMES)
                .where(LOA_GAMES.GAME_STATE.eq(LoaGamesGameState.IN_PROGRESS))
                .fetchInto(LoaGamesRecord.class);
    }
}
