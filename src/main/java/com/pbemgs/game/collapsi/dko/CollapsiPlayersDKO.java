package com.pbemgs.game.collapsi.dko;

import com.pbemgs.generated.tables.records.CollapsiPlayersRecord;
import org.jooq.DSLContext;

import java.util.List;

import static com.pbemgs.generated.tables.CollapsiPlayers.COLLAPSI_PLAYERS;

public class CollapsiPlayersDKO {
    private final DSLContext dslContext;

    public CollapsiPlayersDKO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    /**
     * Fetch all players in a given game, sorted by their seat 2number
     */
    public List<CollapsiPlayersRecord> getPlayersForGame(Long gameId) {
        return dslContext.selectFrom(COLLAPSI_PLAYERS)
                .where(COLLAPSI_PLAYERS.GAME_ID.eq(gameId))
                .orderBy(COLLAPSI_PLAYERS.PLAYER_SEAT)
                .fetchInto(CollapsiPlayersRecord.class);
    }

    /**
     * Fetch a specific player entry.
     */
    public CollapsiPlayersRecord getPlayerByPlayerSeat(long gameId, int playerNum) {
        return dslContext.selectFrom(COLLAPSI_PLAYERS)
                .where(COLLAPSI_PLAYERS.GAME_ID.eq(gameId))
                .and(COLLAPSI_PLAYERS.PLAYER_SEAT.eq(playerNum))
                .fetchOne();
    }

    public CollapsiPlayersRecord getPlayerByUserId(Long gameId, long userId) {
        return dslContext.selectFrom(COLLAPSI_PLAYERS)
                .where(COLLAPSI_PLAYERS.GAME_ID.eq(gameId))
                .and(COLLAPSI_PLAYERS.USER_ID.eq(userId))
                .fetchOne();
    }

    /**
     * Insert a new player record.
     */
    public void addPlayer(Long gameId, Long userId, int playerSeat) {
        CollapsiPlayersRecord record = dslContext.newRecord(COLLAPSI_PLAYERS);
        record.setUserId(userId);
        record.setGameId(gameId);
        record.setPlayerSeat(playerSeat);
        record.store();
    }

}
