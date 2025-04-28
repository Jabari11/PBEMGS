package com.pbemgs.controller;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.pbemgs.dko.DSLContextFactory;
import com.pbemgs.model.S3Email;
import org.jooq.DSLContext;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Email Handler: AWS Lambda Entry Point for handling a new email in the system.
 * <p>
 * This simply has the interface for the Lambda call, and forwards on to the main back-end handler.
 */
public class SESEmailHandler implements RequestHandler<Map<String, Object>, String> {

    static final boolean DEBUG_EMAIL_SENDING = false;
    static final boolean DEBUG_CONNECTIONS = false;

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {

        LambdaLogger logger = context.getLogger();
        logger.log("Start of handleRequest() - event: " + event.toString());

        if (DEBUG_EMAIL_SENDING) {
            logger.log("DEBUG TEST OF HTTP CONNECTIONS:");
            testDNS(logger);
            testHttpConnectivity(logger);
            testConnectivity(logger);
        }
        if (DEBUG_CONNECTIONS) {
            logger.log("DEBUG TEST OF EMAIL SENDING:");
            sendTestSMTPEmail(logger);
            sendTestSesApiEmail(logger);
        }

        int successRecords = 0;
        int failRecords = 0;

        List<Map<String, Object>> records = (List<Map<String, Object>>) event.get("Records");
        if (records == null || records.isEmpty()) {
            logger.log("No Records found in the event.");
            return "No records found in the event payload.";
        }

        logger.log("Lambda Endpoint handleRequest - number of records: " + records.size());
        DSLContext dslContext = DSLContextFactory.getProductionInstance();

        for (Map<String, Object> record : records) {
            try {
                S3Email email = S3Email.fromJson(record, logger);
                if (email.getFrom().equalsIgnoreCase("pbemgs@angryturtlestudios.com") ||
                        email.getFrom().equalsIgnoreCase("pbemgs@mail.angryturtlestudios.com")) {
                    logger.log("Ignoring mail from self! - subject line is: " + email.getSubject());
                    continue;
                }
                MainEmailProcessor commandParser = new MainEmailProcessor(dslContext);
                commandParser.process(email, logger);
                ++successRecords;
            } catch (IllegalArgumentException e) {
                logger.log("Caught exception: " + e.getMessage());
                ++failRecords;
            }
        }

        return "Complete.  Successes: " + successRecords + ", Failures: " + failRecords;
    }

    // Test methods for debugging AWS Setup
    public void sendTestSMTPEmail(LambdaLogger logger) {
        try {
            SESEmailSender sender = new SESEmailSender(logger);
            sender.sendEmail("mmast11@yahoo.com", "SMTP Email Test",
                    "This is a test email through SMTP from the lambda function.");
        } catch (Exception e) {
            logger.log("exception sending SMTP email: " + e.getMessage());
        }
    }

    public void sendTestSesApiEmail(LambdaLogger logger) {
        try (SesClient sesClient = SesClient.builder().region(Region.US_WEST_1).build()) {
            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .destination(Destination.builder()
                            .toAddresses("mmast11@yahoo.com")
                            .build())
                    .message(software.amazon.awssdk.services.ses.model.Message.builder()
                            .subject(Content.builder()
                                    .data("SES API Test Email")
                                    .build())
                            .body(Body.builder()
                                    .text(Content.builder()
                                            .data("This is a test email through the SES API from your Lambda function.")
                                            .build())
                                    .build())
                            .build())
                    .source("pbemgs@mail.angryturtlestudios.com")
                    .build();
            sesClient.sendEmail(emailRequest);
        } catch (Exception e) {
            logger.log("exception sending SES API email: " + e.getMessage());
            throw e;
        }
    }

    public void testDNS(LambdaLogger logger) {
        try {
            InetAddress google = InetAddress.getByName("www.google.com");
            logger.log("DNS resolved: " + google.getHostAddress());
        } catch (Exception e) {
            logger.log("DNS resolution failed: " + e.getMessage());
        }
    }

    public void testHttpConnectivity(LambdaLogger logger) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.google.com"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.log("HTTP Response Code: " + response.statusCode());
        } catch (Exception e) {
            logger.log("HTTP connection failed: " + e.getMessage());
        }
    }

    public void testConnectivity(LambdaLogger logger) {
        ApacheHttpClient httpClient = (ApacheHttpClient) ApacheHttpClient.builder().build();

        try {
            // Build the HTTP request
            SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                    .uri(new URI("https://www.google.com"))
                    .method(SdkHttpMethod.GET)
                    .build();

            // Execute the HTTP request
            HttpExecuteRequest executeRequest = HttpExecuteRequest.builder()
                    .request(request)
                    .build();

            HttpExecuteResponse response = httpClient.prepareRequest(executeRequest).call();

            // Check response status
            if (response.httpResponse().isSuccessful()) {
                logger.log("Connection successful!");
            } else {
                logger.log("Connection failed with status: " + response.httpResponse().statusCode());
            }
        } catch (Exception e) {
            logger.log("Connection failed with exception: " + e.getMessage());
        } finally {
            httpClient.close();
        }
    }

}
