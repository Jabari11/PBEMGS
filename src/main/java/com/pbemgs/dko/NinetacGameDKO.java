package com.pbemgs.dko;

import com.pbemgs.generated.enums.NinetacGamesBoardOption;
import com.pbemgs.generated.enums.NinetacGamesGameState;
import com.pbemgs.generated.tables.records.NinetacGamesRecord;
import org.jooq.DSLContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.pbemgs.generated.tables.NinetacGames.NINETAC_GAMES;

public class NinetacGameDKO {

    private final DSLContext dslContext;

    public NinetacGameDKO(DSLContext jooqContext) {
        dslContext = jooqContext;
    }

    // Get game by game ID
    public NinetacGamesRecord getGameById(Long gameId) {
        return dslContext.selectFrom(NINETAC_GAMES)
                .where(NINETAC_GAMES.GAME_ID.eq(gameId))
                .fetchOne();
    }

    // Get all games in the OPEN state
    public List<NinetacGamesRecord> getOpenGames() {
        return dslContext.selectFrom(NINETAC_GAMES)
                .where(NINETAC_GAMES.GAME_STATE.eq(NinetacGamesGameState.OPEN))
                .fetchInto(NinetacGamesRecord.class);
    }

    // Get all active games for a user (OPEN or IN_PROGRESS)
    public List<NinetacGamesRecord> getActiveGamesForUser(Long playerId) {
        return dslContext.selectFrom(NINETAC_GAMES)
                .where(NINETAC_GAMES.GAME_STATE.in(NinetacGamesGameState.OPEN, NinetacGamesGameState.IN_PROGRESS)
                        .and(NINETAC_GAMES.X_PLAYER_ID.eq(playerId).or(NINETAC_GAMES.O_PLAYER_ID.eq(playerId))))
                .fetchInto(NinetacGamesRecord.class);
    }

    // Create a new game
    public Long createNewGame(Long xPlayerId, String boardState, NinetacGamesBoardOption boardOption) {
        NinetacGamesRecord record = dslContext.newRecord(NINETAC_GAMES);
        record.setGameState(NinetacGamesGameState.OPEN);
        record.setXPlayerId(xPlayerId);
        record.setBoardState(boardState);
        record.setLastMoveTimestamp(LocalDateTime.now());
        record.setRulesVersion(1);
        record.setBoardOption(boardOption);
        record.setLastReminderTimestamp(null);
        record.store();
        return record.getGameId();
    }

    // Complete game creation by setting the O player
    public void completeGameCreation(Long gameId, Long oPlayerId, Long firstPlayerId) {
        dslContext.update(NINETAC_GAMES)
                .set(NINETAC_GAMES.GAME_STATE, NinetacGamesGameState.IN_PROGRESS)
                .set(NINETAC_GAMES.O_PLAYER_ID, oPlayerId)
                .set(NINETAC_GAMES.PLAYER_ID_TO_MOVE, firstPlayerId)
                .set(NINETAC_GAMES.STARTING_PLAYER_ID, firstPlayerId)
                .where(NINETAC_GAMES.GAME_ID.eq(gameId))
                .execute();
    }

    // Update state in an existing game
    public void updateGame(NinetacGamesRecord gameRecord) {
        // Ensure the gameRecord has a valid primary key (GameID) to update
        if (gameRecord.getGameId() == null) {
            throw new IllegalArgumentException("Cannot update game: GameID is null.");
        }

        int rowsUpdated = dslContext.update(NINETAC_GAMES)
                .set(NINETAC_GAMES.GAME_STATE, gameRecord.getGameState())
                .set(NINETAC_GAMES.BOARD_STATE, gameRecord.getBoardState())
                .set(NINETAC_GAMES.PLAYER_ID_TO_MOVE, gameRecord.getPlayerIdToMove())
                .set(NINETAC_GAMES.LAST_MOVE_TIMESTAMP, gameRecord.getLastMoveTimestamp())
                .set(NINETAC_GAMES.VICTOR_PLAYER_ID, gameRecord.getVictorPlayerId())
                .set(NINETAC_GAMES.LAST_REMINDER_TIMESTAMP, gameRecord.getLastMoveTimestamp())
                .where(NINETAC_GAMES.GAME_ID.eq(gameRecord.getGameId()))
                .execute();

        if (rowsUpdated == 0) {
            throw new IllegalArgumentException("Cannot update game - game doesn't exist!");
        }
    }

    public void updateReminderTimestamps(Set<Long> userIds, LocalDateTime updateTo) {
        dslContext.update(NINETAC_GAMES)
                .set(NINETAC_GAMES.LAST_REMINDER_TIMESTAMP, updateTo)
                .where(NINETAC_GAMES.PLAYER_ID_TO_MOVE.in(userIds))
                .execute();
    }

    public List<NinetacGamesRecord> getActiveGames() {
        return dslContext.selectFrom(NINETAC_GAMES)
                .where(NINETAC_GAMES.GAME_STATE.eq(NinetacGamesGameState.IN_PROGRESS))
                .fetchInto(NinetacGamesRecord.class);
    }
}
