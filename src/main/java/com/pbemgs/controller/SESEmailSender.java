package com.pbemgs.controller;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.util.List;
import java.util.Properties;

public class SESEmailSender {

    private LambdaLogger logger;

    public SESEmailSender(LambdaLogger logger) {
        this.logger = logger;
    }

    public void sendEmail(String toAddress, String subject, String plainTextBody) {
        logger.log("Attempting to send email to: " + toAddress);
        String smtpHost = "email-smtp.us-west-1.amazonaws.com"; // Update to your SES region
        int smtpPort = 587; // Use 587 for TLS or 465 for SSL
        String smtpUsername = System.getenv("SMTP_USERNAME");
        String smtpPassword = System.getenv("SMTP_PASSWORD");

        // Set SMTP properties
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);

        // Create a session with authentication
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });

        try {
            // Create the email message
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress("pbemgs@mail.angryturtlestudios.com"));
            message.setReplyTo(new InternetAddress[]{new InternetAddress("pbemgs@angryturtlestudios.com")});
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(toAddress));
            message.setSubject(subject);

            // create html and plain body parts from the same text
            MimeBodyPart plainTextPart = new MimeBodyPart();
            plainTextPart.setText(plainTextBody, "utf-8");

            MimeBodyPart htmlPart = new MimeBodyPart();

            String htmlContent = "<html><head><meta charset=\"UTF-8\"></head>" +
            "<body style=\"font-family: 'Courier New', Courier, Consolas, 'Lucida Console', 'DejaVu Sans Mono', monospace;\"><pre>" + plainTextBody + "</pre></body></html>";
            htmlPart.setContent(htmlContent, "text/html; charset=UTF-8");

            MimeMultipart multipart = new MimeMultipart("alternative");
            multipart.addBodyPart(plainTextPart);
            multipart.addBodyPart(htmlPart);

            message.setContent(multipart);

            // Send the email
            Transport.send(message);
            logger.log("Email sent successfully to " + toAddress);

        } catch (MessagingException e) {
            logger.log("Failed to send email: " + e.getMessage());
        }
    }

    public void sendNotificationEmail(List<String> toAddresses, String plainTextBody) {
        logger.log("Attempting to send bulk BCC notification email to: " + toAddresses.size() + " addresses.");
        String smtpHost = "email-smtp.us-west-1.amazonaws.com";
        int smtpPort = 587; // Use 587 for TLS or 465 for SSL
        String smtpUsername = System.getenv("SMTP_USERNAME");
        String smtpPassword = System.getenv("SMTP_PASSWORD");

        // Set SMTP properties
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);

        // Create a session with authentication
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });

        try {
            // Create the email message
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress("pbemgs@mail.angryturtlestudios.com"));
            message.setReplyTo(new InternetAddress[]{new InternetAddress("pbemgs@angryturtlestudios.com")});
            message.setRecipient(Message.RecipientType.TO, new InternetAddress("pbemgs@angryturtlestudios.com"));
            message.setRecipients(Message.RecipientType.BCC,
                    toAddresses.stream().map(SESEmailSender::safeAddress).toArray(InternetAddress[]::new)
            );
            message.setSubject("PBEMGS-NOTIFICATION");

            // create html and plain body parts from the same text
            MimeBodyPart plainTextPart = new MimeBodyPart();
            plainTextPart.setText(plainTextBody, "utf-8");

            MimeBodyPart htmlPart = new MimeBodyPart();
            String htmlContent = "<html><body style=\"font-family: monospace;\"><pre>" + plainTextBody + "</pre></body></html>";
            htmlPart.setContent(htmlContent, "text/html");

            MimeMultipart multipart = new MimeMultipart("alternative");
            multipart.addBodyPart(plainTextPart);
            multipart.addBodyPart(htmlPart);

            message.setContent(multipart);

            // Send the email
            Transport.send(message);
            logger.log("Notification email sent successfully!");
        } catch (MessagingException e) {
            logger.log("Failed to send system notification email: " + e.getMessage());
        }
    }

    private static InternetAddress safeAddress(String email) {
        try {
            return new InternetAddress(email);
        } catch (AddressException e) {
            throw new RuntimeException("Invalid email address: " + email, e);
        }
    }

}
