package com.optimizer.mail;

import com.optimizer.mail.config.MailConfig;
import io.dropwizard.lifecycle.Managed;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;

/***
 Created by mudit.g on Mar, 2019
 ***/
public class MailSender implements Managed {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailSender.class);
    @Getter
    private MailConfig mailConfig;
    private Session mailSession;

    public MailSender(MailConfig mailConfig) {
        this.mailConfig = mailConfig;
        Properties mailProps = new Properties();
        mailProps.put("mail.transport.protocol", "smtp");
        mailProps.put("mail.smtp.port", mailConfig.getPort());
        mailProps.put("mail.smtp.auth", false);
        mailProps.put("mail.smtp.host", mailConfig.getHost());
        mailProps.put("mail.smtp.startttls.enable", false);
        mailProps.put("mail.smtp.timeout", 10000);
        mailProps.put("mail.smtp.connectiontimeout", 10000);
        this.mailSession = Session.getDefaultInstance(mailProps);
    }

    public void send(String subject, String content, String recipients) {
        try {
            MimeMessage message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress(mailConfig.getFrom()));
            if(mailConfig.isDefaultOwnersEnabled()){
                recipients = recipients + ", nitish.goyal@phonepe.com, phaneesh@phonepe.com, santanu@phonepe.com, mudit.g@phonepe.com";
            }
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipients));
            message.setSubject(subject);
            InternetHeaders headers = new InternetHeaders();
            headers.addHeader("Content-type", "text/html; charset=UTF-8");
            BodyPart messageBodyPart = new MimeBodyPart(headers, content.getBytes("UTF-8"));
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);
            message.setContent(multipart);
            // Send message
            Transport.send(message, mailConfig.getUser(), mailConfig.getPassword());
            LOGGER.info("Mail Sent");
        } catch (Exception e) {
            LOGGER.error("Error sending mail", e);
        }
    }

    @Override
    public void start() {
        Properties mailProps = new Properties();
        mailProps.put("mail.transport.protocol", "smtp");
        mailProps.put("mail.smtp.port", mailConfig.getPort());
        mailProps.put("mail.smtp.auth", false);
        mailProps.put("mail.smtp.host", mailConfig.getHost());
        mailProps.put("mail.smtp.startttls.enable", false);
        mailProps.put("mail.smtp.timeout", 10000);
        mailProps.put("mail.smtp.connectiontimeout", 10000);
        mailSession = Session.getDefaultInstance(mailProps);
    }

    @Override
    public void stop() {
    }
}
