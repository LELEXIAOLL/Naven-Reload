package com.irc.handler;

public class LoginAttemptResult {

    private final LoginResult status;
    private final String username;
    private final String rank;
    private final String tag; // 新增Tag字段

    public LoginAttemptResult(LoginResult status, String username, String rank, String tag) {
        this.status = status;
        this.username = username;
        this.rank = rank;
        this.tag = tag;
    }

    public LoginResult getStatus() {
        return status;
    }
    public String getUsername() {
        return username;
    }
    public String getRank() {
        return rank;
    }
    public String getTag() {
        return tag;
    }
}