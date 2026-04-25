package com.irc.handler;

public enum LoginResult {
    SUCCESS,
    USER_NOT_FOUND,
    INVALID_PASSWORD,
    HWID_MISMATCH,
    USER_BANNED,
    HWID_BANNED
}