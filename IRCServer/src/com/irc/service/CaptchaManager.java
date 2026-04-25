package com.irc.service;

import com.irc.utils.LogManager;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class CaptchaManager {
    private static class CaptchaEntry {
        final String code;
        final long creationTime;

        CaptchaEntry(String code) {
            this.code = code;
            this.creationTime = System.currentTimeMillis();
        }
    }

    private final Map<String, CaptchaEntry> captchas = new ConcurrentHashMap<>();
    private final Map<String, Long> ipRequestTimestamps = new ConcurrentHashMap<>();

    private static final long CAPTCHA_VALIDITY_MS = 60 * 1000;
    private static final long IP_RATE_LIMIT_MS = 60 * 1000;
    private final Random random = new Random();

    public String generateAndStore(String qq, String ip) {
        long now = System.currentTimeMillis();
        Long lastRequestTime = ipRequestTimestamps.get(ip);
        if (lastRequestTime != null && (now - lastRequestTime < IP_RATE_LIMIT_MS)) {
            LogManager.warn("验证码管理：IP " + ip + " 请求过于频繁，已拒绝。");
            return null;
        }

        String code = String.format("%06d", random.nextInt(999999));
        captchas.put(qq, new CaptchaEntry(code));
        ipRequestTimestamps.put(ip, now);
        LogManager.info("验证码管理：已为QQ " + qq + " (来自IP: " + ip + ") 生成验证码: " + code);
        return code;
    }

    public boolean verify(String qq, String code) {
        CaptchaEntry entry = captchas.get(qq);
        if (entry == null) {
            return false;
        }

        captchas.remove(qq);

        long now = System.currentTimeMillis();
        if (now - entry.creationTime > CAPTCHA_VALIDITY_MS) {
            return false;
        }

        return entry.code.equals(code);
    }
}