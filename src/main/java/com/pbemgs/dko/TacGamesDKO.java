package com.pbemgs.dko;

import com.pbemgs.generated.enums.TacGamesGameState;
import com.pbemgs.generated.tables.records.TacGamesRecord;
import org.jooq.DSLContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.pbemgs.generated.tables.TacGames.TAC_GAMES;

public class TacGamesDKO {
    private final DSLContext dslContext;

    public TacGamesDKO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public Long createNewGame(Long userId, String boardState) {
        TacGamesRecord record = dslContext.newRecord(TAC_GAMES);
        record.setGameState(TacGamesGameState.IN_PROGRESS);
        record.setUserId(userId);
        record.setBoardState(boardState);
        record.setLastMoveTimestamp(LocalDateTime.now());
        record.setLastReminderTimestamp(null);
        record.store();
        return record.getGameId();
    }

    public TacGamesRecord getGameById(long gameId) {
        return dslContext.selectFrom(TAC_GAMES)
                .where(TAC_GAMES.GAME_ID.eq(gameId))
                .fetchOne();
    }

    public TacGamesRecord getActiveGameForUser(long userId) {
        return dslContext.selectFrom(TAC_GAMES)
                .where(TAC_GAMES.USER_ID.eq(userId))
                .and(TAC_GAMES.GAME_STATE.eq(TacGamesGameState.IN_PROGRESS))
                .fetchOne();
    }


    public void updateGame(TacGamesRecord game) {
        dslContext.update(TAC_GAMES)
                .set(game)
                .where(TAC_GAMES.GAME_ID.eq(game.getGameId()))
                .execute();
    }

    public List<TacGamesRecord> getActiveGames() {
        return dslContext.selectFrom(TAC_GAMES)
                .where(TAC_GAMES.GAME_STATE.eq(TacGamesGameState.IN_PROGRESS))
                .fetchInto(TacGamesRecord.class);
    }

    public void updateReminderTimestamps(Set<Long> userIds, LocalDateTime updateTo) {

        dslContext.update(TAC_GAMES)
                .set(TAC_GAMES.LAST_REMINDER_TIMESTAMP, updateTo)
                .where(TAC_GAMES.USER_ID.in(userIds))
                .execute();
    }
}
