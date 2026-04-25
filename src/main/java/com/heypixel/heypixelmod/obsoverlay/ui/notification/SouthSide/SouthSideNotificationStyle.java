package com.heypixel.heypixelmod.obsoverlay.ui.notification.SouthSide;

import com.heypixel.heypixelmod.obsoverlay.ui.notification.Notification;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.NotificationLevel;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.NotificationStyle;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.awt.*;

public class SouthSideNotificationStyle implements NotificationStyle {

    private static final int BASE_BG_COLOR = new Color(21, 22, 25).getRGB();
    private static final int BASE_ALPHA = 220;
    private static final int PROGRESS_BAR_COLOR = new Color(50, 100, 255).getRGB();

    private static final float FIXED_WIDTH = 180.0F;
    private static final float LEFT_PADDING = 10.0F;
    private static final float VERTICAL_PADDING = 10.0F;
    private static final float PROGRESS_BAR_HEIGHT = 2.0F;
    private static final float HEIGHT = 50.0F;

    // 辅助方法：计算透明度因子
    private float getAlphaFactor(Notification notification) {
        long lifeTime = System.currentTimeMillis() - notification.getCreateTime();
        long maxAge = notification.getMaxAge();
        long timeLeft = maxAge - lifeTime;
        if (timeLeft < 500) {
            return Mth.clamp((float) timeLeft / 500.0F, 0.0F, 1.0F);
        }
        if (lifeTime < 500) {
            return Mth.clamp((float) lifeTime / 500.0F, 0.0F, 1.0F);
        }
        return 1.0F;
    }

    @Override
    public void renderShader(Notification notification, PoseStack stack, float x, float y) {
        float alphaFactor = getAlphaFactor(notification);
        if (alphaFactor <= 0.05f) return;

        int currentAlpha = (int) (BASE_ALPHA * alphaFactor);
        int bgColor = this.modifyAlpha(BASE_BG_COLOR, currentAlpha);

        RenderUtils.fill(stack, x, y, x + getWidth(notification), y + getHeight(notification), bgColor);
    }

    @Override
    public void render(Notification notification, PoseStack stack, float x, float y) {
        float alphaFactor = getAlphaFactor(notification);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alphaFactor);

        GuiGraphics guiGraphics = new GuiGraphics(Minecraft.getInstance(), Minecraft.getInstance().renderBuffers().bufferSource());
        guiGraphics.pose().last().pose().mul(stack.last().pose());

        int currentAlpha = (int) (BASE_ALPHA * alphaFactor);
        int bgColor = this.modifyAlpha(BASE_BG_COLOR, currentAlpha);

        RenderUtils.fill(stack, x, y, x + getWidth(notification), y + getHeight(notification), bgColor);

        String title = getTitleForRender(notification);
        String displayMessage = getDisplayMessage(notification);

        float textX = x + LEFT_PADDING;
        float textY = y + VERTICAL_PADDING;

        int whiteWithAlpha = new Color(255, 255, 255, (int)(255 * alphaFactor)).getRGB();
        int grayWithAlpha = new Color(180, 180, 180, (int)(255 * alphaFactor)).getRGB();

        Fonts.harmony.render(guiGraphics.pose(), title, textX, textY, new Color(whiteWithAlpha, true), true, 0.35f);
        Fonts.harmony.render(guiGraphics.pose(), displayMessage, textX, textY + Fonts.harmony.getHeight(true, 0.35f) + 4, new Color(grayWithAlpha, true), true, 0.3f);

        float lifeTime = (float)(System.currentTimeMillis() - notification.getCreateTime());
        float progress = Math.min(lifeTime / (float)notification.getMaxAge(), 1.0F);
        float progressBarWidth = getWidth(notification) * progress;

        int progressColor = this.modifyAlpha(PROGRESS_BAR_COLOR, (int)(255 * alphaFactor));
        RenderUtils.fill(stack, x, y + getHeight(notification) - PROGRESS_BAR_HEIGHT, x + progressBarWidth, y + getHeight(notification), progressColor);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public float getWidth(Notification notification) {
        String title = getTitleForRender(notification);
        String displayMessage = getDisplayMessage(notification);
        float titleWidth = Fonts.harmony.getWidth(title, 0.35f);
        float messageWidth = Fonts.harmony.getWidth(displayMessage, 0.3f);
        float textWidth = Math.max(titleWidth, messageWidth);
        float requiredContentWidth = LEFT_PADDING + textWidth + LEFT_PADDING;
        return Math.max(FIXED_WIDTH, requiredContentWidth);
    }

    @Override
    public float getHeight(Notification notification) {
        return HEIGHT;
    }

    private String getDisplayMessage(Notification notification) {
        String originalMessage = notification.getMessage();
        if (originalMessage.toLowerCase().contains("enabled") || originalMessage.toLowerCase().contains("disabled")) {
            return "Toggled " + originalMessage;
        }
        return originalMessage;
    }

    private String getTitleForRender(Notification notification) {
        String title;
        switch (notification.getLevel()) {
            case SUCCESS: title = "Success"; break;
            case WARNING: title = "Warning"; break;
            case ERROR: title = "Error"; break;
            case INFO: default: title = "Module"; break;
        }

        String message = notification.getMessage().toLowerCase();
        if ((notification.getLevel() == NotificationLevel.SUCCESS || notification.getLevel() == NotificationLevel.ERROR)
                && (message.contains("enabled") || message.contains("disabled"))) {
            title = "Module";
        }
        return title;
    }

    private int modifyAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}