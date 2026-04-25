package com.irc.handler;

import com.irc.IRC;
import com.irc.service.CaptchaManager;
import com.irc.service.EmailService;
import com.irc.utils.DatabaseManager;

import com.irc.utils.LogManager;

public class Register {
    private final DatabaseManager dbManager;
    private final CaptchaManager captchaManager;
    private final EmailService emailService;

    public enum RegistrationResult { SUCCESS, CAPTCHA_INVALID, USER_OR_QQ_EXISTS, DATABASE_ERROR, HWID_BANNED }

    public Register(DatabaseManager dbManager, CaptchaManager captchaManager, EmailService emailService) {
        this.dbManager = dbManager;
        this.captchaManager = captchaManager;
        this.emailService = emailService;
    }

    public boolean requestCaptcha(String qq, String ip) {
        LogManager.info("收到来自 IP: " + ip + " 的验证码请求，目标QQ: " + qq);
        String code = captchaManager.generateAndStore(qq, ip);
        if (code == null) {
            LogManager.info("请求被限流，已拒绝。");
            return false; // 被限流
        }
        String email = qq + "@qq.com";
        boolean emailSent = emailService.sendVerificationEmail(email, code);

        if (emailSent) {
        } else {
        }
        return emailSent;
    }

    public RegistrationResult attemptRegistration(String username, String passwordHash, String qq, String hwid, String captcha) {
        if (dbManager.isHwidBanned(hwid)) {
            return RegistrationResult.HWID_BANNED;
        }
        if (!captchaManager.verify(qq, captcha)) {
            return RegistrationResult.CAPTCHA_INVALID;
        }
        if (dbManager.doesUserExist(username, qq)) {
            return RegistrationResult.USER_OR_QQ_EXISTS;
        }
        if (dbManager.createUser(username, passwordHash, qq, hwid)) {
            return RegistrationResult.SUCCESS;
        } else {
            return RegistrationResult.DATABASE_ERROR;
        }
    }
}