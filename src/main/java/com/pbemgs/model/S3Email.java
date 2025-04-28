package com.pbemgs.model;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Structure for a "base" email - the email metadata and link to the contents of the body which are stored in S3.
 * This is a single email - the "Records" list is outside of here.
 */
public class S3Email {

    // Status types for spam and virus tests
    public enum StatusType {PASS, FAIL, GRAY, PROCESSING_FAILED}

    private final static String EXPECTED_SOURCE = "aws:ses";
    private final static String EXPECTED_VERSION = "1.0";
    private final static String EXPECTED_TO_ADDR = "pbemgs@angryturtlestudios.com";

    private final String from;
    private final String subject;
    private final String messageId;
    private final StatusType spamVerdict;
    private final StatusType virusVerdict;

    private S3Email(String from, String subject, String messageId, StatusType spamVerdict, StatusType virusVerdict) {
        this.from = from;
        this.subject = subject;
        this.messageId = messageId;
        this.spamVerdict = spamVerdict;
        this.virusVerdict = virusVerdict;
    }

    // Create the S3 Email from the given "JSON structure"
    public static S3Email fromJson(Map<String, Object> json, LambdaLogger logger) {
        logger.log("S3Email constructor - parsing json from email.");
        logger.log(json.toString());

        validateJson(json, logger);

        Map<String, Object> sesBlock = (Map<String, Object>) json.get("ses");
        if (sesBlock == null) {
            logger.log("Missing ses block");
            throw new IllegalArgumentException("Missing ses block");
        }
        Map<String, Object> mailBlock = (Map<String, Object>) sesBlock.get("mail");
        if (mailBlock == null) {
            logger.log("Missing mail block");
            throw new IllegalArgumentException("Missing mail block");
        }
        Map<String, Object> receiptBlock = (Map<String, Object>) sesBlock.get("receipt");
        if (receiptBlock == null) {
            logger.log("Missing receipt block");
            throw new IllegalArgumentException("Missing receipt block");
        }
        Map<String, Object> commonHeadersBlock = (Map<String, Object>) mailBlock.get("commonHeaders");
        if (commonHeadersBlock == null) {
            logger.log("Missing commonHeaders block");
            throw new IllegalArgumentException("Missing commonHeaders block");
        }

        String from = (String) mailBlock.get("source");
        logger.log("From: " + from.toString());
        List<String> toList = (List<String>) receiptBlock.get("recipients");
        logger.log("ToList: " + toList.toString());

        if (toList == null || toList.isEmpty() || !toList.contains(EXPECTED_TO_ADDR)) {
            logger.log("To list missing expected recipient: " + toList);
            throw new IllegalArgumentException("Missing recipient in email");
        }

        String subject = (String) commonHeadersBlock.get("subject");

        // ignoring timestamp for now
        String messageId = (String) mailBlock.get("messageId");

        // "receipt" side
        // ignoring recipients, should be the same as "To"
        Map<String, Object> spamVerdictMap = (Map<String, Object>) receiptBlock.get("spamVerdict");
        StatusType spamVerdict = convertStringToStatusType((String) spamVerdictMap.get("status"));

        Map<String, Object> virusVerdictMap = (Map<String, Object>) receiptBlock.get("virusVerdict");
        StatusType virusVerdict = convertStringToStatusType((String) virusVerdictMap.get("status"));

        if (spamVerdict != StatusType.PASS) {
            logger.log("Spam Verdict came back: " + spamVerdict.toString());
            throw new IllegalArgumentException("Bad spam verdict");
        }
        if (virusVerdict != StatusType.PASS) {
            logger.log("Virus Verdict came back: " + virusVerdict.toString());
            throw new IllegalArgumentException("Bad virus verdict");
        }

        return new S3Email(from, subject, messageId, spamVerdict, virusVerdict);
    }

    public String getFrom() {
        return from;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessageId() {
        return messageId;
    }

    public StatusType getSpamVerdict() {
        return spamVerdict;
    }

    public StatusType getVirusVerdict() {
        return virusVerdict;
    }

    // Methods to extract the plain text of the email body (which is stored in MIME format in S3)
    public String getEmailBodyText(LambdaLogger logger) throws Exception {
        return extractPlainTextFromS3Object("pbemgs-email-bodies", messageId, logger);
    }

    private String extractPlainTextFromS3Object(String bucketName, String objectKey, LambdaLogger logger) throws Exception {
        // Retrieve the object from S3
        try (S3Client s3Client = S3Client.create()) {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(request);

            // Parse the MIME message
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props, null);
            MimeMessage message = new MimeMessage(session, s3Object);

            // Extract the plain text content
            return getTextFromMimeMessage(message, logger);
        } catch (Exception e) {
            logger.log("Error retrieving S3 object: " + e.getMessage());
            throw e;
        }
    }

    private String getTextFromMimeMessage(MimeMessage message, LambdaLogger logger) throws Exception {
        Object content = message.getContent();
        if (content instanceof String) {
            String fullContent = (String) content;
            logger.log("Raw pure string content:\n" + fullContent);
            return fullContent.replace("\r", "").trim();
        } else if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (part.isMimeType("text/html")) {
                    String fullContent = (String) part.getContent();
                    logger.log("Raw HTML content:\n " + fullContent);
                    String processedHtml = extractTextFromHtml(fullContent);
                    logger.log("-- Flattened Text Part:\n " + processedHtml);
                    return processedHtml;
                }
            }

            // No HTML part found, fall back to the plain-text part
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (part.isMimeType("text/plain")) {
                    String fullContent = (String) part.getContent();
                    logger.log("Raw Plain Text content:\n " + fullContent);
                    return fullContent.trim(); // Return plain text part
                }
            }

        }
        logger.log("ERROR: Email received with no html or plain-text parts!");
        throw new Exception("No HTML or plain text content found");
    }

    /**
     * Extract the plain-text part of the html email part, preserving newlines but stripping all
     * other formatting nonsense.
     */
    private String extractTextFromHtml(String html) {
        return html.replaceAll("<div[^>]*>", "\n")  // Replace <div> with newline
                .replaceAll("<br\\s*/?>", "\n")  // Replace <br> tags with newlines
                .replaceAll("<p[^>]*>", "\n")    // Replace <p> tags with newlines
                .replaceAll("</p>", "")          // Remove closing </p> tags
                .replaceAll("</div>", "")        // Remove closing divs
                .replaceAll("<[^>]+>", "")       // Strip all other HTML tags
                .replace("&nbsp;", " ")          // Replace non-breaking spaces
                .trim();
    }

    // Internal helper methods

    private static void validateJson(Map<String, Object> json, LambdaLogger logger) {
        // Validate source and version
        if (!json.containsKey("eventSource") || !json.get("eventSource").equals(EXPECTED_SOURCE)) {
            logger.log("Unexpected or missing eventSource!");
            throw new IllegalArgumentException("Unexpected or missing eventSource");
        }
        if (!json.containsKey("eventVersion") || !json.get("eventVersion").equals(EXPECTED_VERSION)) {
            logger.log("Unexpected or missing eventVersion!");
            throw new IllegalArgumentException("Unexpected or missing eventVersion");
        }
    }

    private static StatusType convertStringToStatusType(String val) {
        if (val == null || val.isEmpty()) {
            throw new IllegalArgumentException("Invalid spam/virus verdict: " + val);
        }
        try {
            return StatusType.valueOf(val.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unexpected spam/virus verdict: " + val, e);
        }
    }

}
