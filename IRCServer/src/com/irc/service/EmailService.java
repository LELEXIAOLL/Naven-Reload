package com.irc.service;

import com.irc.utils.LogManager;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

public class EmailService {
    private static final String SENDER_EMAIL = "2037256099@qq.com";
    private static final String SENDER_AUTHORIZATION_CODE = "mtmnztksfodlbdfa";
    private static final String SMTP_HOST = "smtp.qq.com";
    private static final String SMTP_PORT = "465";

    public boolean sendVerificationEmail(String recipientEmail, String captcha) {
        LogManager.info("[EmailService] 准备发送邮件至: " + recipientEmail);

        Properties props = new Properties();
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        // 添加超时设置，防止长时间卡顿
        props.put("mail.smtp.connectiontimeout", "5000"); // 5秒连接超时
        props.put("mail.smtp.timeout", "5000");           // 5秒读写超时

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER_EMAIL, SENDER_AUTHORIZATION_CODE);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject("您的 IRC 验证码");
            message.setText("您好，\n\n您的验证码是: " + captcha + "\n\n该验证码将在1分钟后失效。");

            LogManager.info("[EmailService] 正在连接SMTP服务器并发送邮件...");
            Transport.send(message);
            LogManager.info("[EmailService] 邮件发送成功。");
            return true;
        } catch (MessagingException e) {
            LogManager.error("[EmailService] 邮件发送失败!");
            // 打印完整的错误堆栈，这对于调试至关重要
            e.printStackTrace();
            return false;
        }
    }
}