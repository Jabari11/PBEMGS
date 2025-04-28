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
    public List<NinetacGamesRecord> getActiveGamesForUser(Long userId) {
        return dslContext.selectFrom(NINETAC_GAMES)
                .where(NINETAC_GAMES.GAME_STATE.in(NinetacGamesGameState.OPEN, NinetacGamesGameState.IN_PROGRESS)
                        .and(NINETAC_GAMES.X_USER_ID.eq(userId).or(NINETAC_GAMES.O_USER_ID.eq(userId))))
                .fetchInto(NinetacGamesRecord.class);
    }

    // Create a new game
    public Long createNewGame(Long xUserId, String boardState, NinetacGamesBoardOption boardOption) {
        NinetacGamesRecord record = dslContext.newRecord(NINETAC_GAMES);
        record.setGameState(NinetacGamesGameState.OPEN);
        record.setXUserId(xUserId);
        record.setBoardState(boardState);
        record.setLastMoveTimestamp(LocalDateTime.now());
        record.setBoardOption(boardOption);
        record.setLastReminderTimestamp(null);
        record.store();
        return record.getGameId();
    }

    // Complete game creation by setting the O player
    public void completeGameCreation(Long gameId, Long oUserId, Long firstUserId) {
        dslContext.update(NINETAC_GAMES)
                .set(NINETAC_GAMES.GAME_STATE, NinetacGamesGameState.IN_PROGRESS)
                .set(NINETAC_GAMES.O_USER_ID, oUserId)
                .set(NINETAC_GAMES.USER_ID_TO_MOVE, firstUserId)
                .set(NINETAC_GAMES.STARTING_USER_ID, firstUserId)
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
                .set(NINETAC_GAMES.USER_ID_TO_MOVE, gameRecord.getUserIdToMove())
                .set(NINETAC_GAMES.LAST_MOVE_TIMESTAMP, gameRecord.getLastMoveTimestamp())
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
                .where(NINETAC_GAMES.USER_ID_TO_MOVE.in(userIds))
                .execute();
    }

    public List<NinetacGamesRecord> getActiveGames() {
        return dslContext.selectFrom(NINETAC_GAMES)
                .where(NINETAC_GAMES.GAME_STATE.eq(NinetacGamesGameState.IN_PROGRESS))
                .fetchInto(NinetacGamesRecord.class);
    }
}
