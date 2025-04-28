package com.pbemgs.model;

import com.pbemgs.generated.enums.PlayerOutcomesOutcome;

public record PlayerOutcome(long id, String gameName, long gameId, long userId, PlayerOutcomesOutcome outcome,
                            Integer place, Boolean wentFirst, java.sql.Timestamp timestamp) {
}
