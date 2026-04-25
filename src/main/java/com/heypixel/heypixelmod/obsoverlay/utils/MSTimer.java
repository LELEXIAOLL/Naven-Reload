package com.heypixel.heypixelmod.obsoverlay.utils;

public final class MSTimer {
    private static long time = -1L;

    public boolean hasTimePassed(long MS) {
        return System.currentTimeMillis() >= time + MS;
    }

    public void reset() {
        time = System.currentTimeMillis();
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - time;
    }
}