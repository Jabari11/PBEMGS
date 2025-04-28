package com.pbemgs.dko;

import com.pbemgs.generated.enums.AtaxxGamesBoardOption;
import com.pbemgs.generated.enums.AtaxxGamesGameState;
import com.pbemgs.generated.tables.records.AtaxxGamesRecord;
import org.jooq.DSLContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.pbemgs.generated.tables.AtaxxGames.ATAXX_GAMES;

public class AtaxxGameDKO {
    private final DSLContext dslContext;

    public AtaxxGameDKO(DSLContext jooqContext) {
        dslContext = jooqContext;
    }

    // Get game by game ID
    public AtaxxGamesRecord getGameById(Long gameId) {
        return dslContext.selectFrom(ATAXX_GAMES)
                .where(ATAXX_GAMES.GAME_ID.eq(gameId))
                .fetchOne();
    }

    // Get all games in the OPEN state
    public List<AtaxxGamesRecord> getOpenGames() {
        return dslContext.selectFrom(ATAXX_GAMES)
                .where(ATAXX_GAMES.GAME_STATE.eq(AtaxxGamesGameState.OPEN))
                .fetchInto(AtaxxGamesRecord.class);
    }

    // Get all active games for a user (OPEN or IN_PROGRESS)
    public List<AtaxxGamesRecord> getActiveGamesForUser(Long userId) {
        return dslContext.selectFrom(ATAXX_GAMES)
                .where(ATAXX_GAMES.GAME_STATE.in(AtaxxGamesGameState.OPEN, AtaxxGamesGameState.IN_PROGRESS)
                        .and(ATAXX_GAMES.USER0_ID.eq(userId)
                                .or(ATAXX_GAMES.USER1_ID.eq(userId))
                                .or(ATAXX_GAMES.USER2_ID.eq(userId))
                                .or(ATAXX_GAMES.USER3_ID.eq(userId))))
                .fetchInto(AtaxxGamesRecord.class);
    }

    // Create a new game
    public Long createNewGame(Long player0Id, int numPlayers, String turnOrder, int boardSize, String boardState, AtaxxGamesBoardOption boardOption) {
        AtaxxGamesRecord record = dslContext.newRecord(ATAXX_GAMES);
        record.setGameState(AtaxxGamesGameState.OPEN);
        record.setNumPlayers(numPlayers);
        record.setUser0Id(player0Id);
        record.setTurnOrder(turnOrder);
        record.setBoardSize(boardSize);
        record.setBoardState(boardState);
        record.setBoardOption(boardOption);
        record.store();
        return record.getGameId();
    }

    // Add a new player to a 4P game (handles player1 and player2 only)
    public void addPlayerToGame(Long gameId, Long newPlayerId) {
        AtaxxGamesRecord game = getGameById(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Cannot add player: Game not found.");
        }
        if (game.getUser1Id() == null) {
            dslContext.update(ATAXX_GAMES)
                    .set(ATAXX_GAMES.USER1_ID, newPlayerId)
                    .where(ATAXX_GAMES.GAME_ID.eq(gameId))
                    .execute();
        } else if (game.getUser2Id() == null) {
            dslContext.update(ATAXX_GAMES)
                    .set(ATAXX_GAMES.USER2_ID, newPlayerId)
                    .where(ATAXX_GAMES.GAME_ID.eq(gameId))
                    .execute();
        } else {
            throw new IllegalStateException("Cannot add player - player 3 will complete the game.");
        }
    }

    // Finalize a 2P game when player2 joins (single DB execute)
    public void completeGameCreation2P(Long gameId, Long player1Id, Long firstPlayerId) {
        int rowsUpdated = dslContext.update(ATAXX_GAMES)
                .set(ATAXX_GAMES.USER1_ID, player1Id)
                .set(ATAXX_GAMES.GAME_STATE, AtaxxGamesGameState.IN_PROGRESS)
                .set(ATAXX_GAMES.USER_ID_TO_MOVE, firstPlayerId)
                .set(ATAXX_GAMES.LAST_MOVE_TIMESTAMP, LocalDateTime.now())
                .where(ATAXX_GAMES.GAME_ID.eq(gameId))
                .execute();

        if (rowsUpdated == 0) {
            throw new IllegalArgumentException("Cannot complete game creation - game not found.");
        }
    }

    // Finalize a 4P game when player4 joins
    public void completeGameCreation4P(Long gameId, Long player3Id, Long firstPlayerId) {

        int rowsUpdated = dslContext.update(ATAXX_GAMES)
                .set(ATAXX_GAMES.USER3_ID, player3Id)
                .set(ATAXX_GAMES.GAME_STATE, AtaxxGamesGameState.IN_PROGRESS)
                .set(ATAXX_GAMES.USER_ID_TO_MOVE, firstPlayerId)
                .set(ATAXX_GAMES.LAST_MOVE_TIMESTAMP, LocalDateTime.now())
                .where(ATAXX_GAMES.GAME_ID.eq(gameId))
                .execute();

        if (rowsUpdated == 0) {
            throw new IllegalArgumentException("Cannot complete game creation - game not found.");
        }
    }

    // Update game state
    public void updateGame(AtaxxGamesRecord gameRecord) {
        if (gameRecord.getGameId() == null) {
            throw new IllegalArgumentException("Cannot update game: GameID is null.");
        }

        int rowsUpdated = dslContext.update(ATAXX_GAMES)
                .set(ATAXX_GAMES.GAME_STATE, gameRecord.getGameState())
                .set(ATAXX_GAMES.BOARD_STATE, gameRecord.getBoardState())
                .set(ATAXX_GAMES.USER_ID_TO_MOVE, gameRecord.getUserIdToMove())
                .set(ATAXX_GAMES.LAST_MOVE_TIMESTAMP, gameRecord.getLastMoveTimestamp())
                .where(ATAXX_GAMES.GAME_ID.eq(gameRecord.getGameId()))
                .execute();

        if (rowsUpdated == 0) {
            throw new IllegalArgumentException("Cannot update game - game doesn't exist!");
        }
    }

    public List<AtaxxGamesRecord> getActiveGames() {
        return dslContext.selectFrom(ATAXX_GAMES)
                .where(ATAXX_GAMES.GAME_STATE.eq(AtaxxGamesGameState.IN_PROGRESS))
                .fetchInto(AtaxxGamesRecord.class);
    }

    public void updateReminderTimestamps(Set<Long> userIds, LocalDateTime updateTo) {
        dslContext.update(ATAXX_GAMES)
                .set(ATAXX_GAMES.LAST_REMINDER_TIMESTAMP, updateTo)
                .where(ATAXX_GAMES.USER_ID_TO_MOVE.in(userIds))
                .execute();
    }
}
