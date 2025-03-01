package com.pbemgs.dko;

import com.pbemgs.generated.enums.SurgeGamesGameState;
import com.pbemgs.generated.enums.SurgeGamesGameTimezone;
import com.pbemgs.generated.tables.records.SurgeGamesRecord;
import org.jooq.DSLContext;

import java.time.LocalDateTime;
import java.util.List;

import static com.pbemgs.generated.tables.SurgeGames.SURGE_GAMES;
import static com.pbemgs.generated.tables.SurgePlayers.SURGE_PLAYERS;

public class SurgeGamesDKO {
    private final DSLContext dslContext;

    public SurgeGamesDKO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    /**
     * Fetch a game by game ID.
     */
    public SurgeGamesRecord getGameById(Long gameId) {
        return dslContext.selectFrom(SURGE_GAMES)
                .where(SURGE_GAMES.GAME_ID.eq(gameId))
                .fetchOne();
    }

    /**
     * Fetch all open games.
     */
    public List<SurgeGamesRecord> getOpenGames() {
        return dslContext.selectFrom(SURGE_GAMES)
                .where(SURGE_GAMES.GAME_STATE.eq(SurgeGamesGameState.OPEN))
                .fetchInto(SurgeGamesRecord.class);
    }

    /**
     * Fetch all in-progress games.
     */
    public List<SurgeGamesRecord> getActiveGames() {
        return dslContext.selectFrom(SURGE_GAMES)
                .where(SURGE_GAMES.GAME_STATE.eq(SurgeGamesGameState.IN_PROGRESS))
                .fetchInto(SurgeGamesRecord.class);
    }


    /**
     * Fetch all active games for a given user.
     * This requires a JOIN with surge_players to find which games a user is in.
     */
    public List<SurgeGamesRecord> getActiveGamesForUser(Long userId) {
        return dslContext.select(SURGE_GAMES.fields())
                .from(SURGE_GAMES)
                .join(SURGE_PLAYERS).on(SURGE_GAMES.GAME_ID.eq(SURGE_PLAYERS.GAME_ID))
                .where(SURGE_PLAYERS.USER_ID.eq(userId))
                .and(SURGE_GAMES.GAME_STATE.in(SurgeGamesGameState.OPEN, SurgeGamesGameState.IN_PROGRESS))
                .fetchInto(SurgeGamesRecord.class);
    }

    /**
     * Create a new Surge game.
     */
    public Long createNewGame(int numPlayers, int climit, SurgeGamesGameTimezone timeZone, int ticksPerDay,
                              int rows, int cols,
                              String boardState, String geyserState, String pressureState, String momentumState) {
        SurgeGamesRecord record = dslContext.newRecord(SURGE_GAMES);
        record.setGameState(SurgeGamesGameState.OPEN);
        record.setNumPlayers(numPlayers);
        record.setCommandLimit(climit);
        record.setGameTimezone(timeZone);
        record.setTicksPerDay(ticksPerDay);
        record.setBoardRows(rows);
        record.setBoardCols(cols);
        record.setBoardState(boardState);
        record.setGeyserState(geyserState);
        record.setPressureState(pressureState);
        record.setMomentumState(momentumState);
        record.setCreatedAt(LocalDateTime.now());
        record.setLastTimeStep(LocalDateTime.now());
        record.store();
        return record.getGameId();
    }

    /**
     * Update game state.
     */
    public void updateGame(SurgeGamesRecord gameRecord) {
        if (gameRecord.getGameId() == null) {
            throw new IllegalArgumentException("Cannot update game: GameID is null.");
        }

        dslContext.update(SURGE_GAMES)
                .set(SURGE_GAMES.GAME_STATE, gameRecord.getGameState())
                .set(SURGE_GAMES.BOARD_STATE, gameRecord.getBoardState())
                .set(SURGE_GAMES.PRESSURE_STATE, gameRecord.getPressureState())
                .set(SURGE_GAMES.MOMENTUM_STATE, gameRecord.getMomentumState())
                .set(SURGE_GAMES.LAST_TIME_STEP, gameRecord.getLastTimeStep())
                .where(SURGE_GAMES.GAME_ID.eq(gameRecord.getGameId()))
                .execute();
    }

}
