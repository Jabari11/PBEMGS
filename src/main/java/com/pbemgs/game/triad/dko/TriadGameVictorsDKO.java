package com.pbemgs.game.triad.dko;

import org.jooq.DSLContext;
import org.jooq.Record1;

import java.util.List;
import java.util.stream.Collectors;

import static com.pbemgs.generated.tables.TriadGameVictors.TRIAD_GAME_VICTORS;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;

public class TriadGameVictorsDKO {
    private final DSLContext dsl;

    public TriadGameVictorsDKO(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Inserts a winner into the Ataxx victors table.
     *
     * @param gameId The game ID.
     * @param userId The player ID who won the game.
     */
    public void addVictor(long gameId, long userId, int subgame, long firstPlayerUserId) {
        dsl.insertInto(TRIAD_GAME_VICTORS)
                .set(TRIAD_GAME_VICTORS.GAME_ID, gameId)
                .set(TRIAD_GAME_VICTORS.USER_ID, userId)
                .set(TRIAD_GAME_VICTORS.SUBGAME_ID, subgame)
                .set(TRIAD_GAME_VICTORS.FIRST_PLAYER_WON, firstPlayerUserId == userId)
                .execute();
    }

    /**
     * Retrieves all winners for a specific game.
     *
     * @param gameId The game ID.
     * @return List of player IDs who won the game.
     */
    public List<Long> getVictorsForGame(long gameId) {
        return dsl.select(TRIAD_GAME_VICTORS.USER_ID)
                .from(TRIAD_GAME_VICTORS)
                .where(TRIAD_GAME_VICTORS.GAME_ID.eq(gameId))
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
                dsl.selectFrom(TRIAD_GAME_VICTORS)
                        .where(TRIAD_GAME_VICTORS.USER_ID.eq(playerId))
        );
    }

    /**
     * Retrieves a leaderboard-style list of top players sorted by most wins.
     *
     * @param limit The number of top players to retrieve.
     * @return List of player ID and their win count.
     */
    public List<PlayerWinCount> getTopPlayers(int limit) {
        return dsl.select(TRIAD_GAME_VICTORS.USER_ID, count().as("win_count"))
                .from(TRIAD_GAME_VICTORS)
                .groupBy(TRIAD_GAME_VICTORS.USER_ID)
                .orderBy(field("win_count").desc())
                .limit(limit)
                .fetch()
                .map(r -> new PlayerWinCount(r.get(TRIAD_GAME_VICTORS.USER_ID), r.get("win_count", Integer.class)));
    }

    /**
     * Retrieves games where there was a tie (multiple winners).
     *
     * @return List of game IDs that had multiple winners.
     */
    public List<Long> getTiedGames() {
        return dsl.select(TRIAD_GAME_VICTORS.GAME_ID)
                .from(TRIAD_GAME_VICTORS)
                .groupBy(TRIAD_GAME_VICTORS.GAME_ID)
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
