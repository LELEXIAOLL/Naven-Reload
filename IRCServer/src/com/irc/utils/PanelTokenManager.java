package com.irc.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PanelTokenManager {

    // 内部类：存储Token详情
    private static class TokenInfo {
        String token;
        String ip;
        long createTime;

        public TokenInfo(String token, String ip) {
            this.token = token;
            this.ip = ip;
            this.createTime = System.currentTimeMillis();
        }
    }

    // 内存存储：Key是用户名，Value是Token信息
    private static final Map<String, TokenInfo> tokenStore = new ConcurrentHashMap<>();

    // 24小时 (毫秒)
    private static final long EXPIRATION_TIME = 24L * 60 * 60 * 1000;

    /**
     * 生成并保存 Token (如果用户已存在，会覆盖旧Token)
     */
    public static String createToken(String username, String ip) {
        String token = java.util.UUID.randomUUID().toString();
        tokenStore.put(username, new TokenInfo(token, ip));
        return token;
    }

    /**
     * 验证 Token 是否有效
     */
    public static boolean validateToken(String username, String token, String currentIp) {
        TokenInfo info = tokenStore.get(username);

        // 1. 用户不存在
        if (info == null) return false;

        // 2. Token 不匹配
        if (!info.token.equals(token)) return false;

        // 3. IP 地址发生变化
        if (!info.ip.equals(currentIp)) return false;

        // 4. 超过 24 小时
        if (System.currentTimeMillis() - info.createTime > EXPIRATION_TIME) {
            tokenStore.remove(username); // 过期了就删掉
            return false;
        }

        return true;
    }
}