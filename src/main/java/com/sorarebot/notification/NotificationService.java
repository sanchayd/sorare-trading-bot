package com.sorarebot.notification;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.sorarebot.model.Card;

/**
 * Service for sending notifications about special card listings.
 */
public class NotificationService {
    private static final Logger LOGGER = Logger.getLogger(NotificationService.class.getName());
    
    private final String emailAddress;
    private final String smtpHost;
    private final String smtpPort;
    private final String smtpUsername;
    private final String smtpPassword;
    private final boolean enabledEmail;
    
    /**
     * Create a new notification service.
     * 
     * @param emailAddress The email address to send notifications to
     * @param smtpHost SMTP server host
     * @param smtpPort SMTP server port
     * @param smtpUsername SMTP username
     * @param smtpPassword SMTP password
     * @param enabledEmail Whether email notifications are enabled
     */
    public NotificationService(String emailAddress, String smtpHost, String smtpPort,
                              String smtpUsername, String smtpPassword, boolean enabledEmail) {
        this.emailAddress = emailAddress;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.enabledEmail = enabledEmail;
    }
    
    /**
     * Send a notification about a special card.
     * 
     * @param card The card to notify about
     * @param reason The reason for the notification (e.g., "Jersey mint", "Favorite serial")
     */
    public void notifySpecialCard(Card card, String reason) {
        LOGGER.info("Special card found: " + card.getId() + " - " + reason);
        
        // Log the notification regardless of email settings
        String message = String.format(
            "SPECIAL CARD ALERT: %s\n" +
            "Player: %s\n" +
            "Serial: %s\n" +
            "Rarity: %s\n" +
            "Price: %s ETH\n" +
            "Reason: %s\n" +
            "Link: https://sorare.com/card/%s",
            card.getId(), card.getPlayerId(), card.getSerial(), 
            card.getRarity(), card.getPrice(), reason, card.getId()
        );
        
        LOGGER.info(message);
        
        // Send email notification if enabled
        if (enabledEmail) {
            sendEmail("Sorare Bot: Special Card Alert", message);
        }
    }
    
    /**
     * Send an email notification.
     * 
     * @param subject The email subject
     * @param body The email body
     */
    private void sendEmail(String subject, String body) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", smtpPort);
            
            Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            });
            
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpUsername));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailAddress));
            message.setSubject(subject);
            message.setText(body);
            
            Transport.send(message);
            
            LOGGER.info("Email notification sent successfully");
        } catch (MessagingException e) {
            LOGGER.log(Level.SEVERE, "Failed to send email notification", e);
        }
    }
}