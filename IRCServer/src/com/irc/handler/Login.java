package com.irc.handler;

import com.irc.utils.DatabaseManager;
import com.irc.utils.SecurityUtils;

import com.irc.utils.LogManager;

public class Login {
    private final DatabaseManager dbManager;

    public Login(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public LoginAttemptResult attempt(String username, String password, String hwid) {
        LogManager.info("收到登录尝试，用户: " + username);
        String[] credentials = dbManager.getUserCredentials(username);
        if (credentials == null) {
            return new LoginAttemptResult(LoginResult.USER_NOT_FOUND, null, null, null);
        }

        String dbPasswordHash = credentials[0];
        String dbHwid = credentials[1];
        int isBanned = Integer.parseInt(credentials[2]);
        String rank = credentials[3];
        String tag = credentials[4]; // 获取Tag

        String clientPasswordHash = SecurityUtils.sha256(password);
        if (!clientPasswordHash.equals(dbPasswordHash)) {
            return new LoginAttemptResult(LoginResult.INVALID_PASSWORD, null, null, null);
        }
        if (isBanned == 1) {
            return new LoginAttemptResult(LoginResult.USER_BANNED, null, null, null);
        }
        if (dbManager.isHwidBanned(hwid)) {
            return new LoginAttemptResult(LoginResult.HWID_BANNED, null, null, null);
        }
        if (dbHwid != null && !dbHwid.trim().isEmpty() && !dbHwid.equals(hwid)) {
            return new LoginAttemptResult(LoginResult.HWID_MISMATCH, null, null, null);
        }

        LogManager.info("用户 " + username + " 验证成功。正在更新最后登录时间...");
        dbManager.updateLastLoginTime(username);
        return new LoginAttemptResult(LoginResult.SUCCESS, username, rank, tag);
    }
}