package com.pbemgs.dko;

import com.pbemgs.generated.enums.PlayerOutcomesOutcome;
import com.pbemgs.model.GameType;
import com.pbemgs.model.PlayerLeaderboardEntry;
import com.pbemgs.model.PlayerOutcome;
import com.pbemgs.model.PlayerRecord;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.List;

import static com.pbemgs.generated.tables.PlayerOutcomes.PLAYER_OUTCOMES;

public class PlayerOutcomesDKO {
    private final DSLContext dsl;

    public PlayerOutcomesDKO(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Inserts a new player outcome into the player_outcomes table.
     * The timestamp is set by the database (default CURRENT_TIMESTAMP).
     */
    public void insertOutcome(GameType gameType, long gameId, long userId, PlayerOutcomesOutcome outcome,
                              Integer place, Boolean wentFirst) {
        dsl.insertInto(PLAYER_OUTCOMES)
                .set(PLAYER_OUTCOMES.GAME_NAME, gameType.getGameName())
                .set(PLAYER_OUTCOMES.GAME_ID, gameId)
                .set(PLAYER_OUTCOMES.USER_ID, userId)
                .set(PLAYER_OUTCOMES.OUTCOME, outcome)
                .set(PLAYER_OUTCOMES.PLACE, place)
                .set(PLAYER_OUTCOMES.WENT_FIRST, wentFirst)
                .execute();
    }

    /**
     * Retrieves a player's overall record of wins, losses, and draws across all games.  Zeros across if none.
     */
    public PlayerRecord getPlayerOverallRecord(long userId) {
        PlayerRecord record = dsl.select(
                        DSL.count(DSL.when(PLAYER_OUTCOMES.OUTCOME.eq(PlayerOutcomesOutcome.WIN), PLAYER_OUTCOMES.ID)).as("wins"),
                        DSL.count(DSL.when(PLAYER_OUTCOMES.OUTCOME.eq(PlayerOutcomesOutcome.LOSS), PLAYER_OUTCOMES.ID)).as("losses"),
                        DSL.count(DSL.when(PLAYER_OUTCOMES.OUTCOME.eq(PlayerOutcomesOutcome.DRAW), PLAYER_OUTCOMES.ID)).as("draws")
                )
                .from(PLAYER_OUTCOMES)
                .where(PLAYER_OUTCOMES.USER_ID.eq(userId))
                .fetchOneInto(PlayerRecord.class);
        return record != null ? record : new PlayerRecord(0, 0, 0);
    }

    /**
     * Retrieves a player's record of wins, losses, and draws for a specific game.
     * Returns a PlayerRecord with zeros if the player has no outcomes for the game.
     */
    public PlayerRecord getPlayerRecordByGame(long userId, GameType gameType) {
        PlayerRecord record = dsl.select(
                        DSL.count(DSL.when(PLAYER_OUTCOMES.OUTCOME.eq(PlayerOutcomesOutcome.WIN), PLAYER_OUTCOMES.ID)).as("wins"),
                        DSL.count(DSL.when(PLAYER_OUTCOMES.OUTCOME.eq(PlayerOutcomesOutcome.LOSS), PLAYER_OUTCOMES.ID)).as("losses"),
                        DSL.count(DSL.when(PLAYER_OUTCOMES.OUTCOME.eq(PlayerOutcomesOutcome.DRAW), PLAYER_OUTCOMES.ID)).as("draws")
                )
                .from(PLAYER_OUTCOMES)
                .where(PLAYER_OUTCOMES.USER_ID.eq(userId)
                        .and(PLAYER_OUTCOMES.GAME_NAME.eq(gameType.getGameName())))
                .fetchOneInto(PlayerRecord.class);
        return record != null ? record : new PlayerRecord(0, 0, 0);
    }

    // Points calculation: 1.0 for a win, 0.5 for a draw, 0.0 for a loss
    private static final Field<Double> POINTS = DSL.sum(
            DSL.when(PLAYER_OUTCOMES.OUTCOME.eq(PlayerOutcomesOutcome.WIN), 1.0)
                    .when(PLAYER_OUTCOMES.OUTCOME.eq(PlayerOutcomesOutcome.DRAW), 0.5)
                    .otherwise(0.0d)
    ).cast(Double.class).as("points");

    /**
     * Retrieves the top players overall, ranked by total points.
     * Points are calculated as: win = 1.0, draw = 0.5, loss = 0.0.
     */
    public List<PlayerLeaderboardEntry> getTopPlayersOverall(int limit) {
        return dsl.select(PLAYER_OUTCOMES.USER_ID, POINTS)
                .from(PLAYER_OUTCOMES)
                .groupBy(PLAYER_OUTCOMES.USER_ID)
                .orderBy(POINTS.desc())
                .limit(limit)
                .fetchInto(PlayerLeaderboardEntry.class);
    }

    /**
     * Retrieves the top players for a specific game, ranked by total points.
     * Points are calculated as: win = 1.0, draw = 0.5, loss = 0.0.
     */
    public List<PlayerLeaderboardEntry> getTopPlayersByGame(GameType gameType, int limit) {
        return dsl.select(PLAYER_OUTCOMES.USER_ID, POINTS)
                .from(PLAYER_OUTCOMES)
                .where(PLAYER_OUTCOMES.GAME_NAME.eq(gameType.getGameName()))
                .groupBy(PLAYER_OUTCOMES.USER_ID)
                .orderBy(POINTS.desc())
                .limit(limit)
                .fetchInto(PlayerLeaderboardEntry.class);
    }

    /**
     * Retrieves all outcomes for a specific game instance.
     * Useful for displaying the results of a completed game.
     */
    public List<PlayerOutcome> getOutcomesForGame(String gameName, long gameId) {
        return dsl.selectFrom(PLAYER_OUTCOMES)
                .where(PLAYER_OUTCOMES.GAME_NAME.eq(gameName)
                        .and(PLAYER_OUTCOMES.GAME_ID.eq(gameId)))
                .fetchInto(PlayerOutcome.class);
    }

    /**
     * Retrieves all outcomes for a specific user
     * Ordered by game name then id.
     */
    public List<PlayerOutcome> getOutcomesForUserGames(long userId) {
        return dsl.selectFrom(PLAYER_OUTCOMES)
                .where(PLAYER_OUTCOMES.USER_ID.eq(userId))
                .orderBy(PLAYER_OUTCOMES.GAME_NAME, PLAYER_OUTCOMES.GAME_ID)
                .fetchInto(PlayerOutcome.class);
    }

    /**
     * Retrieves all outcomes for a specific user and game type.  Ordered by game ID.
     */
    public List<PlayerOutcome> getOutcomesForUserGames(long userId, GameType gameType) {
        return dsl.selectFrom(PLAYER_OUTCOMES)
                .where(PLAYER_OUTCOMES.USER_ID.eq(userId))
                .and(PLAYER_OUTCOMES.GAME_NAME.equalIgnoreCase(gameType.getGameName()))
                .orderBy(PLAYER_OUTCOMES.GAME_ID)
                .fetchInto(PlayerOutcome.class);
    }


}
