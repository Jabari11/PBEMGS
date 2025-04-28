package com.pbemgs.game.triad.dko;

import com.pbemgs.generated.tables.records.TriadPlayersRecord;
import org.jooq.DSLContext;

import java.util.List;

import static com.pbemgs.generated.tables.TriadPlayers.TRIAD_PLAYERS;

public class TriadPlayersDKO {
    private final DSLContext dslContext;

    public TriadPlayersDKO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    /**
     * Fetch all players in a given game, sorted by their seat 2number
     */
    public List<TriadPlayersRecord> getPlayersForGame(Long gameId) {
        return dslContext.selectFrom(TRIAD_PLAYERS)
                .where(TRIAD_PLAYERS.GAME_ID.eq(gameId))
                .orderBy(TRIAD_PLAYERS.PLAYER_SEAT)
                .fetchInto(TriadPlayersRecord.class);
    }

    /**
     * Fetch a specific player entry.
     */
    public TriadPlayersRecord getPlayerByPlayerSeat(long gameId, int playerNum) {
        return dslContext.selectFrom(TRIAD_PLAYERS)
                .where(TRIAD_PLAYERS.GAME_ID.eq(gameId))
                .and(TRIAD_PLAYERS.PLAYER_SEAT.eq(playerNum))
                .fetchOne();
    }

    public TriadPlayersRecord getPlayerByUserId(Long gameId, long userId) {
        return dslContext.selectFrom(TRIAD_PLAYERS)
                .where(TRIAD_PLAYERS.GAME_ID.eq(gameId))
                .and(TRIAD_PLAYERS.USER_ID.eq(userId))
                .fetchOne();
    }

    /**
     * Insert a new player record.
     */
    public void addPlayer(Long gameId, Long userId, int playerSeat, String undraftedCards) {
        TriadPlayersRecord record = dslContext.newRecord(TRIAD_PLAYERS);
        record.setUserId(userId);
        record.setGameId(gameId);
        record.setPlayerSeat(playerSeat);
        record.setSubgameCount(0);
        record.setCardsInHand("");
        record.setUndraftedCards(undraftedCards);
        record.store();
    }

    /**
     * Update a player's cards in hand.
     */
    public void updatePlayerCardsInHand(Long userId, Long gameId, String cardsInHand) {
        dslContext.update(TRIAD_PLAYERS)
                .set(TRIAD_PLAYERS.CARDS_IN_HAND, cardsInHand)
                .where(TRIAD_PLAYERS.USER_ID.eq(userId))
                .and(TRIAD_PLAYERS.GAME_ID.eq(gameId))
                .execute();
    }

    /**
     * Update a player's undrafted cards
     */
    public void updatePlayerUndrafted(Long userId, Long gameId, String undrafted) {
        dslContext.update(TRIAD_PLAYERS)
                .set(TRIAD_PLAYERS.UNDRAFTED_CARDS, undrafted)
                .where(TRIAD_PLAYERS.USER_ID.eq(userId))
                .and(TRIAD_PLAYERS.GAME_ID.eq(gameId))
                .execute();
    }

    /**
     * Update a player's game count won
     */
    public void updatePlayerScore(Long userId, Long gameId, int subgameCount) {
        dslContext.update(TRIAD_PLAYERS)
                .set(TRIAD_PLAYERS.SUBGAME_COUNT, subgameCount)
                .where(TRIAD_PLAYERS.USER_ID.eq(userId))
                .and(TRIAD_PLAYERS.GAME_ID.eq(gameId))
                .execute();
    }

    public void updatePlayerRecord(TriadPlayersRecord triadPlayersRecord) {
        dslContext.update(TRIAD_PLAYERS)
                .set(TRIAD_PLAYERS.SUBGAME_COUNT, triadPlayersRecord.getSubgameCount())
                .set(TRIAD_PLAYERS.CARDS_IN_HAND, triadPlayersRecord.getCardsInHand())
                .set(TRIAD_PLAYERS.UNDRAFTED_CARDS, triadPlayersRecord.getUndraftedCards())
                .where(TRIAD_PLAYERS.USER_ID.eq(triadPlayersRecord.getUserId()))
                .and(TRIAD_PLAYERS.GAME_ID.eq(triadPlayersRecord.getGameId()))
                .execute();
    }
}
