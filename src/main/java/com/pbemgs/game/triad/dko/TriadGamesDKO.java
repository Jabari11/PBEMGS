package com.pbemgs.game.triad.dko;

import com.pbemgs.generated.enums.TriadGamesGamePhase;
import com.pbemgs.generated.enums.TriadGamesGameState;
import com.pbemgs.generated.tables.records.TriadGamesRecord;
import org.jooq.DSLContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.pbemgs.generated.tables.TriadGames.TRIAD_GAMES;
import static com.pbemgs.generated.tables.TriadPlayers.TRIAD_PLAYERS;

public class TriadGamesDKO {
    private final DSLContext dslContext;

    public TriadGamesDKO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    /**
     * Fetch a game by game ID.
     */
    public TriadGamesRecord getGameById(Long gameId) {
        return dslContext.selectFrom(TRIAD_GAMES)
                .where(TRIAD_GAMES.GAME_ID.eq(gameId))
                .fetchOne();
    }

    /**
     * Fetch all open games.
     */
    public List<TriadGamesRecord> getOpenGames() {
        return dslContext.selectFrom(TRIAD_GAMES)
                .where(TRIAD_GAMES.GAME_STATE.eq(TriadGamesGameState.OPEN))
                .fetchInto(TriadGamesRecord.class);
    }

    /**
     * Fetch all in-progress games.
     */
    public List<TriadGamesRecord> getActiveGames() {
        return dslContext.selectFrom(TRIAD_GAMES)
                .where(TRIAD_GAMES.GAME_STATE.eq(TriadGamesGameState.IN_PROGRESS))
                .fetchInto(TriadGamesRecord.class);
    }


    /**
     * Fetch all active games for a given user.
     * This requires a JOIN with TRIAD_PLAYERS to find which games a user is in.
     */
    public List<TriadGamesRecord> getActiveGamesForUser(Long userId) {
        return dslContext.select(TRIAD_GAMES.fields())
                .from(TRIAD_GAMES)
                .join(TRIAD_PLAYERS).on(TRIAD_GAMES.GAME_ID.eq(TRIAD_PLAYERS.GAME_ID))
                .where(TRIAD_PLAYERS.USER_ID.eq(userId))
                .and(TRIAD_GAMES.GAME_STATE.in(TriadGamesGameState.OPEN, TriadGamesGameState.IN_PROGRESS))
                .fetchInto(TriadGamesRecord.class);
    }

    /**
     * Create a new TriadCubed game.  Creating player data will be in the players table.
     */
    public Long createNewGame(Long userId, boolean openHand, boolean elementalOn) {
        TriadGamesRecord record = dslContext.newRecord(TRIAD_GAMES);
        record.setGameState(TriadGamesGameState.OPEN);
        record.setGamePhase(TriadGamesGamePhase.WAITING);
        record.setOptionFaceup(openHand);
        record.setOptionElemental(elementalOn);
        record.setOptionSamerule(false);
        record.setOptionPlusrule(false);
        record.setFirstTurnUserId(userId);
        record.setCreatedAt(LocalDateTime.now());
        record.store();
        return record.getGameId();
    }

    /**
     * Update game state.
     */
    public void updateGame(TriadGamesRecord gameRecord) {
        if (gameRecord.getGameId() == null) {
            throw new IllegalArgumentException("Cannot update game: GameID is null.");
        }

        dslContext.update(TRIAD_GAMES)
                .set(TRIAD_GAMES.GAME_STATE, gameRecord.getGameState())
                .set(TRIAD_GAMES.GAME_PHASE, gameRecord.getGamePhase())
                .set(TRIAD_GAMES.CURRENT_ACTION_USERID, gameRecord.getCurrentActionUserid())
                .set(TRIAD_GAMES.BOARD_STATE, gameRecord.getBoardState())
                .set(TRIAD_GAMES.CURRENT_SUBGAME, gameRecord.getCurrentSubgame())
                .set(TRIAD_GAMES.LAST_MOVE_TIMESTAMP, gameRecord.getLastMoveTimestamp())
                .set(TRIAD_GAMES.LAST_REMINDER_TIMESTAMP, gameRecord.getLastReminderTimestamp())
                .where(TRIAD_GAMES.GAME_ID.eq(gameRecord.getGameId()))
                .execute();
    }

    /**
     * Update the last reminder timestamps for all games listed.
     */
    public void updateReminderTimestamps(Set<Long> gameIds, LocalDateTime updateTo) {
        if (gameIds.isEmpty()) {
            return;
        }
        dslContext.update(TRIAD_GAMES)
                .set(TRIAD_GAMES.LAST_REMINDER_TIMESTAMP, updateTo)
                .where(TRIAD_GAMES.GAME_ID.in(gameIds))
                .execute();
    }
}
