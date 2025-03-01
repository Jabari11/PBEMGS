package com.pbemgs.controller;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.pbemgs.dko.DSLContextFactory;
import com.pbemgs.game.surge.Surge;
import org.jooq.DSLContext;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * The controller class that handles AWS "cron job" events for the periodic updates to Surge.
 */
public class SurgeCronJobHandler implements RequestHandler<Map<String, Object>, String> {

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        LambdaLogger logger = context.getLogger();
        DSLContext dslContext = DSLContextFactory.getProductionInstance();
        SESEmailSender emailSender = new SESEmailSender(logger);

        logger.log("Received scheduled event for SurgeCronJobHandler: " + event.toString());
        LocalDateTime startTime = LocalDateTime.now();

        Surge surgeGame = new Surge(dslContext, logger);
        surgeGame.processPeriodicUpdate(emailSender);

        logger.log("Completed Surge updates - time used: " + Duration.between(startTime, LocalDateTime.now()).toMillis() + "ms");
        return "Surge periodic update executed successfully.";
    }
}
