package com.pbemgs.game.collapsi.dko;

import com.pbemgs.generated.enums.CollapsiGamesGameState;
import com.pbemgs.generated.tables.records.CollapsiGamesRecord;
import org.jooq.DSLContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.pbemgs.generated.tables.CollapsiGames.COLLAPSI_GAMES;
import static com.pbemgs.generated.tables.CollapsiPlayers.COLLAPSI_PLAYERS;

public class CollapsiGamesDKO {
    private final DSLContext dslContext;

    public CollapsiGamesDKO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    /**
     * Fetch a game by game ID.
     */
    public CollapsiGamesRecord getGameById(Long gameId) {
        return dslContext.selectFrom(COLLAPSI_GAMES)
                .where(COLLAPSI_GAMES.GAME_ID.eq(gameId))
                .fetchOne();
    }

    /**
     * Fetch all open games.
     */
    public List<CollapsiGamesRecord> getOpenGames() {
        return dslContext.selectFrom(COLLAPSI_GAMES)
                .where(COLLAPSI_GAMES.GAME_STATE.eq(CollapsiGamesGameState.OPEN))
                .fetchInto(CollapsiGamesRecord.class);
    }

    /**
     * Fetch all in-progress games.
     */
    public List<CollapsiGamesRecord> getActiveGames() {
        return dslContext.selectFrom(COLLAPSI_GAMES)
                .where(COLLAPSI_GAMES.GAME_STATE.eq(CollapsiGamesGameState.IN_PROGRESS))
                .fetchInto(CollapsiGamesRecord.class);
    }


    /**
     * Fetch all active games for a given user.
     * This requires a JOIN with COLLAPSI_PLAYERS to find which games a user is in.
     */
    public List<CollapsiGamesRecord> getActiveGamesForUser(Long userId) {
        return dslContext.select(COLLAPSI_GAMES.fields())
                .from(COLLAPSI_GAMES)
                .join(COLLAPSI_PLAYERS).on(COLLAPSI_GAMES.GAME_ID.eq(COLLAPSI_PLAYERS.GAME_ID))
                .where(COLLAPSI_PLAYERS.USER_ID.eq(userId))
                .and(COLLAPSI_GAMES.GAME_STATE.in(CollapsiGamesGameState.OPEN, CollapsiGamesGameState.IN_PROGRESS))
                .fetchInto(CollapsiGamesRecord.class);
    }

    /**
     * Create a new Collapsi game.  Creating player data will be in the players table.
     */
    public Long createNewGame(Long userId) {
        CollapsiGamesRecord record = dslContext.newRecord(COLLAPSI_GAMES);
        record.setGameState(CollapsiGamesGameState.OPEN);
        record.setFirstTurnUserId(userId);
        record.setCreatedAt(LocalDateTime.now());
        record.store();
        return record.getGameId();
    }

    public void completeGame(CollapsiGamesRecord gameRecord, Long userIdSeat1, Long userIdToStart) {
        dslContext.update(COLLAPSI_GAMES)
                .set(COLLAPSI_GAMES.GAME_STATE, CollapsiGamesGameState.IN_PROGRESS)
                .set(COLLAPSI_GAMES.CURRENT_ACTION_USERID, gameRecord.getFirstTurnUserId())
                .set(COLLAPSI_GAMES.FIRST_TURN_USER_ID, gameRecord.getFirstTurnUserId())
                .set(COLLAPSI_GAMES.BOARD_STATE, gameRecord.getBoardState())
                .set(COLLAPSI_GAMES.LAST_MOVE_TIMESTAMP, gameRecord.getLastMoveTimestamp())
                .set(COLLAPSI_GAMES.LAST_REMINDER_TIMESTAMP, gameRecord.getLastReminderTimestamp())
                .where(COLLAPSI_GAMES.GAME_ID.eq(gameRecord.getGameId()))
                .execute();
    }

    /**
     * Update game state.
     */
    public void updateGame(CollapsiGamesRecord gameRecord) {
        if (gameRecord.getGameId() == null) {
            throw new IllegalArgumentException("Cannot update game: GameID is null.");
        }

        dslContext.update(COLLAPSI_GAMES)
                .set(COLLAPSI_GAMES.GAME_STATE, gameRecord.getGameState())
                .set(COLLAPSI_GAMES.CURRENT_ACTION_USERID, gameRecord.getCurrentActionUserid())
                .set(COLLAPSI_GAMES.BOARD_STATE, gameRecord.getBoardState())
                .set(COLLAPSI_GAMES.LAST_MOVE_TIMESTAMP, gameRecord.getLastMoveTimestamp())
                .set(COLLAPSI_GAMES.LAST_REMINDER_TIMESTAMP, gameRecord.getLastReminderTimestamp())
                .where(COLLAPSI_GAMES.GAME_ID.eq(gameRecord.getGameId()))
                .execute();
    }

    /**
     * Update the last reminder timestamps for all games listed.
     */
    public void updateReminderTimestamps(Set<Long> gameIds, LocalDateTime updateTo) {
        if (gameIds.isEmpty()) {
            return;
        }
        dslContext.update(COLLAPSI_GAMES)
                .set(COLLAPSI_GAMES.LAST_REMINDER_TIMESTAMP, updateTo)
                .where(COLLAPSI_GAMES.GAME_ID.in(gameIds))
                .execute();
    }
}
