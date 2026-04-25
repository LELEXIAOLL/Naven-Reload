package com.heypixel.heypixelmod.obsoverlay.utils;

public class AttackStateManager {
    private static long lastAttackTime = 0L;
    private static boolean isAttacking = false;

    public static void updateAttackTime() {
        lastAttackTime = System.currentTimeMillis();
        isAttacking = true;
    }

    public static long getLastAttackTime() {
        return lastAttackTime;
    }

    public static boolean isAttacking() {
        return System.currentTimeMillis() - lastAttackTime < 350L;
    }

    public static void reset() {
        isAttacking = false;
        lastAttackTime = 0L;
    }
}
