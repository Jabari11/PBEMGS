package com.pbemgs.game;

import com.pbemgs.controller.SESEmailSender;
import com.pbemgs.generated.tables.records.UsersRecord;
import com.pbemgs.model.S3Email;

import java.util.HashMap;
import java.util.Map;

/**
 * Base interface for a PBEMGS game.  The controller logic uses this interface to handle commands.
 */
public interface GameInterface {

    void processCreateGame(UsersRecord user, S3Email email, SESEmailSender emailSender);
    void processJoinGame(UsersRecord user, long gameId, SESEmailSender emailSender);
    void processMove(UsersRecord user, long gameId, S3Email emailBody, SESEmailSender emailSender);
    void processStatus(UsersRecord user, long gameId, SESEmailSender emailSender);

    String getOpenGamesTextBody();  // get the text response to an "open_games" command
    String getRulesTextBody();      // get the text response to a "rules" command
    String getMyGamesTextBody(long userId);  // get the text response to a "my_games" command

    default void processPeriodicUpdate(SESEmailSender emailSender) {
        // No-op for games that don't need it.
    }

    default Map<Long, String> processStaleGameCheck(SESEmailSender emailSender) {
        // No-op for games that don't need it.
        return new HashMap<>();
    }
}
