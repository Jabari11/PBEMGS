package com.pbemgs.dko;

import org.jooq.DSLContext;
import org.jooq.Record1;

import java.util.List;
import java.util.stream.Collectors;

import static com.pbemgs.generated.tables.AtaxxVictors.ATAXX_VICTORS;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;

public class AtaxxVictorsDKO {
    private final DSLContext dsl;

    public AtaxxVictorsDKO(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Inserts a winner into the Ataxx victors table.
     *
     * @param gameId   The game ID.
     * @param playerId The player ID who won the game.
     */
    public void addVictor(long gameId, long playerId) {
        dsl.insertInto(ATAXX_VICTORS)
                .set(ATAXX_VICTORS.GAME_ID, gameId)
                .set(ATAXX_VICTORS.PLAYER_ID, playerId)
                .execute();
    }

    /**
     * Retrieves all winners for a specific game.
     *
     * @param gameId The game ID.
     * @return List of player IDs who won the game.
     */
    public List<Long> getVictorsForGame(long gameId) {
        return dsl.select(ATAXX_VICTORS.PLAYER_ID)
                .from(ATAXX_VICTORS)
                .where(ATAXX_VICTORS.GAME_ID.eq(gameId))
                .fetchInto(Long.class);
    }

    /**
     * Gets the total number of games a player has won.
     *
     * @param playerId The player ID.
     * @return Number of games the player has won.
     */
    public int getTotalWinsForPlayer(long playerId) {
        return dsl.fetchCount(
                dsl.selectFrom(ATAXX_VICTORS)
                        .where(ATAXX_VICTORS.PLAYER_ID.eq(playerId))
        );
    }

    /**
     * Retrieves a leaderboard-style list of top players sorted by most wins.
     *
     * @param limit The number of top players to retrieve.
     * @return List of player ID and their win count.
     */
    public List<PlayerWinCount> getTopPlayers(int limit) {
        return dsl.select(ATAXX_VICTORS.PLAYER_ID, count().as("win_count"))
                .from(ATAXX_VICTORS)
                .groupBy(ATAXX_VICTORS.PLAYER_ID)
                .orderBy(field("win_count").desc())
                .limit(limit)
                .fetch()
                .map(r -> new PlayerWinCount(r.get(ATAXX_VICTORS.PLAYER_ID), r.get("win_count", Integer.class)));
    }

    /**
     * Retrieves games where there was a tie (multiple winners).
     *
     * @return List of game IDs that had multiple winners.
     */
    public List<Long> getTiedGames() {
        return dsl.select(ATAXX_VICTORS.GAME_ID)
                .from(ATAXX_VICTORS)
                .groupBy(ATAXX_VICTORS.GAME_ID)
                .having(count().gt(1))
                .fetch()
                .stream()
                .map(Record1::value1)
                .collect(Collectors.toList());
    }

    // Helper record class for leaderboard results
    public static class PlayerWinCount {
        public final long playerId;
        public final int winCount;

        public PlayerWinCount(long playerId, int winCount) {
            this.playerId = playerId;
            this.winCount = winCount;
        }

        @Override
        public String toString() {
            return "Player " + playerId + " - Wins: " + winCount;
        }
    }
}
