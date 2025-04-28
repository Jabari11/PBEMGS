package com.pbemgs.game.ironclad.dko;

import com.pbemgs.generated.enums.IroncladGamesCurrentMovePhase;
import com.pbemgs.generated.enums.IroncladGamesForcedMoveOption;
import com.pbemgs.generated.enums.IroncladGamesGameState;
import com.pbemgs.generated.tables.records.IroncladGamesRecord;
import org.jooq.DSLContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.pbemgs.generated.tables.IroncladGames.IRONCLAD_GAMES;

public class IroncladGameDKO {
    private final DSLContext dslContext;

    public IroncladGameDKO(DSLContext jooqContext) {
        dslContext = jooqContext;
    }

    // Get game by game ID
    public IroncladGamesRecord getGameById(Long gameId) {
        return dslContext.selectFrom(IRONCLAD_GAMES)
                .where(IRONCLAD_GAMES.GAME_ID.eq(gameId))
                .fetchOne();
    }

    // Get all games in the OPEN state
    public List<IroncladGamesRecord> getOpenGames() {
        return dslContext.selectFrom(IRONCLAD_GAMES)
                .where(IRONCLAD_GAMES.GAME_STATE.eq(IroncladGamesGameState.OPEN))
                .fetchInto(IroncladGamesRecord.class);
    }

    // Get all active games for a user (OPEN or IN_PROGRESS)
    public List<IroncladGamesRecord> getActiveGamesForUser(Long UserId) {
        return dslContext.selectFrom(IRONCLAD_GAMES)
                .where(IRONCLAD_GAMES.GAME_STATE.in(IroncladGamesGameState.OPEN, IroncladGamesGameState.IN_PROGRESS)
                        .and(IRONCLAD_GAMES.WHITE_USER_ID.eq(UserId).or(IRONCLAD_GAMES.BLACK_USER_ID.eq(UserId))))
                .fetchInto(IroncladGamesRecord.class);
    }

    // Create a new game
    public Long createNewGame(Long whiteUserId, String robotState, String stoneState, IroncladGamesForcedMoveOption forcedMoveOption) {
        IroncladGamesRecord record = dslContext.newRecord(IRONCLAD_GAMES);
        record.setGameState(IroncladGamesGameState.OPEN);
        record.setWhiteUserId(whiteUserId);
        record.setRobotBoardState(robotState);
        record.setStoneBoardState(stoneState);
        record.setCurrentMovePhase(IroncladGamesCurrentMovePhase.OPEN_MOVE);
        record.setForcedMoveOption(forcedMoveOption);
        record.setLastMoveTimestamp(LocalDateTime.now());

        record.setLastReminderTimestamp(null);
        record.store();
        return record.getGameId();
    }

    // Complete game creation by setting second player ID, game state, and first move.
    public void completeGameCreation(Long gameId, Long blackUserId, Long firstMoveUserId) {
        dslContext.update(IRONCLAD_GAMES)
                .set(IRONCLAD_GAMES.GAME_STATE, IroncladGamesGameState.IN_PROGRESS)
                .set(IRONCLAD_GAMES.BLACK_USER_ID, blackUserId)
                .set(IRONCLAD_GAMES.USER_ID_TO_MOVE, firstMoveUserId)
                .where(IRONCLAD_GAMES.GAME_ID.eq(gameId))
                .execute();
    }

    // Update state in an existing game
    public void updateGame(IroncladGamesRecord gameRecord) {
        // Ensure the gameRecord has a valid primary key (GameID) to update
        if (gameRecord.getGameId() == null) {
            throw new IllegalArgumentException("Cannot update game: GameID is null.");
        }

        int rowsUpdated = dslContext.update(IRONCLAD_GAMES)
                .set(IRONCLAD_GAMES.GAME_STATE, gameRecord.getGameState())
                .set(IRONCLAD_GAMES.ROBOT_BOARD_STATE, gameRecord.getRobotBoardState())
                .set(IRONCLAD_GAMES.STONE_BOARD_STATE, gameRecord.getStoneBoardState())
                .set(IRONCLAD_GAMES.USER_ID_TO_MOVE, gameRecord.getUserIdToMove())
                .set(IRONCLAD_GAMES.CURRENT_MOVE_PHASE, gameRecord.getCurrentMovePhase())
                .set(IRONCLAD_GAMES.LAST_STONE_MOVED, gameRecord.getLastStoneMoved())
                .set(IRONCLAD_GAMES.HALF_MOVE_TEXT, gameRecord.getHalfMoveText())
                .set(IRONCLAD_GAMES.LAST_MOVE_TIMESTAMP, gameRecord.getLastMoveTimestamp())
                .set(IRONCLAD_GAMES.LAST_REMINDER_TIMESTAMP, gameRecord.getLastMoveTimestamp())
                .where(IRONCLAD_GAMES.GAME_ID.eq(gameRecord.getGameId()))
                .execute();

        if (rowsUpdated == 0) {
            throw new IllegalArgumentException("Cannot update game - game doesn't exist!");
        }
    }

    public void updateReminderTimestamps(Set<Long> userIds, LocalDateTime updateTo) {
        dslContext.update(IRONCLAD_GAMES)
                .set(IRONCLAD_GAMES.LAST_REMINDER_TIMESTAMP, updateTo)
                .where(IRONCLAD_GAMES.USER_ID_TO_MOVE.in(userIds))
                .execute();
    }

    public List<IroncladGamesRecord> getActiveGames() {
        return dslContext.selectFrom(IRONCLAD_GAMES)
                .where(IRONCLAD_GAMES.GAME_STATE.eq(IroncladGamesGameState.IN_PROGRESS))
                .fetchInto(IroncladGamesRecord.class);
    }
}
