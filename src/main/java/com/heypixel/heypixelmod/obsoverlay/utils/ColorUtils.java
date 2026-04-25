package com.heypixel.heypixelmod.obsoverlay.utils;

import net.minecraft.client.player.AbstractClientPlayer;

import java.awt.*;

public class ColorUtils {
    public static Color colorSwitch(Color firstColor, Color secondColor, float time, int index, long timePerIndex, double speed) {
        return colorSwitch(firstColor, secondColor, time, index, timePerIndex, speed, 255.0D);
    }

    public static Color colorSwitch(Color firstColor, Color secondColor, float time, int index, long timePerIndex, double speed, double alpha) {
        long now = (long) (speed * (double) System.currentTimeMillis() + (double) ((long) index * timePerIndex));
        float redDiff = (float) (firstColor.getRed() - secondColor.getRed()) / time;
        float greenDiff = (float) (firstColor.getGreen() - secondColor.getGreen()) / time;
        float blueDiff = (float) (firstColor.getBlue() - secondColor.getBlue()) / time;
        int red = Math.round((float) secondColor.getRed() + redDiff * (float) (now % (long) time));
        int green = Math.round((float) secondColor.getGreen() + greenDiff * (float) (now % (long) time));
        int blue = Math.round((float) secondColor.getBlue() + blueDiff * (float) (now % (long) time));
        float redInverseDiff = (float) (secondColor.getRed() - firstColor.getRed()) / time;
        float greenInverseDiff = (float) (secondColor.getGreen() - firstColor.getGreen()) / time;
        float blueInverseDiff = (float) (secondColor.getBlue() - firstColor.getBlue()) / time;
        int inverseRed = Math.round((float) firstColor.getRed() + redInverseDiff * (float) (now % (long) time));
        int inverseGreen = Math.round((float) firstColor.getGreen() + greenInverseDiff * (float) (now % (long) time));
        int inverseBlue = Math.round((float) firstColor.getBlue() + blueInverseDiff * (float) (now % (long) time));

        return now % ((long) time * 2L) < (long) time ? (new Color(inverseRed, inverseGreen, inverseBlue, (int) alpha)) : (new Color(red, green, blue, (int) alpha));
    }

    public static Color rainbow(int speed, int index) {
        int angle = (int)((System.currentTimeMillis() / (long)speed + (long)index) % 360L);
        float hue = (float)angle / 360.0F;
        return new Color(Color.HSBtoRGB(hue, 0.7F, 1.0F));
    }

    public static Color getBlendColor(double current, double max) {
        final long base = Math.round(max / 5);
        if (current >= base * 5) return new Color(15, 255, 15);
        else if (current >= base * 4) return new Color(165, 255, 0);
        else if (current >= base * 3) return new Color(255, 190, 0);
        else if (current >= base * 2) return new Color(255, 90, 0);
        else return new Color(255, 0, 0);
    }

    public static Color getHealthColor(AbstractClientPlayer player, float brightness) {
        float healthPercent = player.getHealth() / player.getMaxHealth();
        int r, g, b;

        if (healthPercent > 0.7f) {
            // 绿色到青色的渐变
            float factor = (healthPercent - 0.7f) / 0.3f;
            r = (int)(50 * brightness);
            g = (int)((200 + 55 * factor) * brightness);
            b = (int)((80 + 175 * factor) * brightness);
        } else if (healthPercent > 0.3f) {
            // 黄色到橙色的渐变
            float factor = (healthPercent - 0.3f) / 0.4f;
            r = (int)((220 - 20 * factor) * brightness);
            g = (int)((180 - 80 * factor) * brightness);
            b = (int)(50 * brightness);
        } else {
            // 红色到深红色的渐变
            float factor = healthPercent / 0.3f;
            r = (int)((220 - 50 * (1 - factor)) * brightness);
            g = (int)((60 * factor) * brightness);
            b = (int)((60 * factor) * brightness);
        }

        return new Color(Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
    }

    public static Color getDistanceColor(double distance) {
        if (distance < 3) return new Color(255, 50, 50);     // 红色 - 非常近
        if (distance < 8) return new Color(255, 150, 50);    // 橙色 - 近
        if (distance < 15) return new Color(255, 255, 50);   // 黄色 - 中等
        if (distance < 25) return new Color(150, 255, 50);   // 黄绿色 - 远
        return new Color(100, 200, 255);                     // 蓝色 - 非常远
    }
}