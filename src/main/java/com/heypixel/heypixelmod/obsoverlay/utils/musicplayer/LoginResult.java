package com.heypixel.heypixelmod.obsoverlay.utils.musicplayer;

public class LoginResult {
    public final boolean success;
    public final String cookie;
    public final String message;

    public LoginResult(boolean success, String cookie, String message) {
        this.success = success;
        this.cookie = cookie;
        this.message = message;
    }
}