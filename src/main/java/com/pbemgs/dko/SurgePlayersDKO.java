package com.pbemgs.dko;

import com.pbemgs.generated.enums.SurgePlayersStatus;
import com.pbemgs.generated.tables.records.SurgePlayersRecord;
import org.jooq.DSLContext;

import java.time.LocalDateTime;
import java.util.List;

import static com.pbemgs.generated.tables.SurgePlayers.SURGE_PLAYERS;

public class SurgePlayersDKO {
    private final DSLContext dslContext;
    
    public SurgePlayersDKO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    /**
     * Fetch all players in a given game, sorted by their player number
     */
    public List<SurgePlayersRecord> getPlayersForGame(Long gameId) {
        return dslContext.selectFrom(SURGE_PLAYERS)
                .where(SURGE_PLAYERS.GAME_ID.eq(gameId))
                .orderBy(SURGE_PLAYERS.PLAYER_NUMBER)
                .fetchInto(SurgePlayersRecord.class);
    }

    /**
     * Fetch a specific player entry.
     */
    public SurgePlayersRecord getPlayerByPlayerNumber(long gameId, int playerNum) {
        return dslContext.selectFrom(SURGE_PLAYERS)
                .where(SURGE_PLAYERS.GAME_ID.eq(gameId))
                .and(SURGE_PLAYERS.PLAYER_NUMBER.eq(playerNum))
                .fetchOne();
    }

    public SurgePlayersRecord getPlayerByUserId(Long gameId, long userId) {
        return dslContext.selectFrom(SURGE_PLAYERS)
                .where(SURGE_PLAYERS.GAME_ID.eq(gameId))
                .and(SURGE_PLAYERS.USER_ID.eq(userId))
                .fetchOne();
    }

    /**
     * Insert a new player record.
     */
    public void addPlayer(Long gameId, Long userId, int playerNumber) {
        SurgePlayersRecord record = dslContext.newRecord(SURGE_PLAYERS);
        record.setUserId(userId);
        record.setGameId(gameId);
        record.setPlayerNumber(playerNumber);
        record.setStatus(SurgePlayersStatus.ACTIVE);
        record.store();
    }

    /**
     * Update a player's command.
     */
    public void updatePlayerCommand(Long userId, Long gameId, String commandString) {
        dslContext.update(SURGE_PLAYERS)
                .set(SURGE_PLAYERS.CURRENT_COMMAND, commandString)
                .set(SURGE_PLAYERS.LAST_COMMAND_UPDATE, LocalDateTime.now())
                .where(SURGE_PLAYERS.USER_ID.eq(userId))
                .and(SURGE_PLAYERS.GAME_ID.eq(gameId))
                .execute();
    }

    /**
     * Clears all player commands for the given game (after an update)
     */
    public void clearAllCommandsForGame(long gameId) {
        dslContext.update(SURGE_PLAYERS)
                .set(SURGE_PLAYERS.CURRENT_COMMAND, (String) null)
                .where(SURGE_PLAYERS.GAME_ID.eq(gameId))
                .execute();
    }

    /**
     * Mark a player as eliminated.
     */
    public void eliminatePlayer(Long userId, Long gameId) {
        dslContext.update(SURGE_PLAYERS)
                .set(SURGE_PLAYERS.STATUS, SurgePlayersStatus.ELIMINATED)
                .where(SURGE_PLAYERS.USER_ID.eq(userId))
                .and(SURGE_PLAYERS.GAME_ID.eq(gameId))
                .execute();
    }

}
