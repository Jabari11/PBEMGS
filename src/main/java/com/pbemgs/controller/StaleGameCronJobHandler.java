package com.pbemgs.controller;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.pbemgs.dko.DSLContextFactory;
import com.pbemgs.dko.UsersDKO;
import com.pbemgs.game.GameFactory;
import com.pbemgs.game.GameInterface;
import com.pbemgs.generated.tables.records.UsersRecord;
import org.jooq.DSLContext;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The controller class that handles AWS "cron job" events for checking for stale games.
 */
public class StaleGameCronJobHandler implements RequestHandler<Map<String, Object>, String> {

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        LambdaLogger logger = context.getLogger();
        logger.log("Received scheduled event for StaleGameCronJobHandler: " + event.toString());

        LocalDateTime startTime = LocalDateTime.now();
        DSLContext dslContext = DSLContextFactory.getProductionInstance();
        SESEmailSender emailSender = new SESEmailSender(logger);
        UsersDKO userDKO = new UsersDKO(dslContext);

        Map<Long, String> staleMsgsByUserId = new HashMap<>();
        List<GameInterface> games = GameFactory.createAllGames(dslContext, logger);
        for (GameInterface game : games) {
            Map<Long, String> thisGameStaleMsgs = game.processStaleGameCheck(emailSender);
            for (Map.Entry<Long, String> entry : thisGameStaleMsgs.entrySet()) {
                staleMsgsByUserId.merge(entry.getKey(), entry.getValue(), (a, b) -> a + "\n" + b);
            }
        }

        Map<Long, UsersRecord> userById = userDKO.fetchUsersByIds(staleMsgsByUserId.keySet());
        for (Long userId : staleMsgsByUserId.keySet()) {
            emailSender.sendEmail(userById.get(userId).getEmailAddr(), "PBEMGS - Stale Game Reminder",
                    TextResponseProvider.getStaleGameEmailBody(staleMsgsByUserId.get(userId)));
            logger.log("Sent stale game reminder to: " + userById.get(userId).getHandle() + " (ID: " + userId + ") for " + staleMsgsByUserId.get(userId));
        }

        logger.log("Completed Stale Game checks - time used: " + Duration.between(startTime, LocalDateTime.now()).toMillis() + "ms");
        return "Stale Game periodic update executed successfully.";
    }
}
