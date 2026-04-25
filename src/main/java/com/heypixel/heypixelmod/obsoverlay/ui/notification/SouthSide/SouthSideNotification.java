package com.heypixel.heypixelmod.obsoverlay.ui.notification.SouthSide;

import com.heypixel.heypixelmod.obsoverlay.ui.notification.Notification;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.NotificationLevel;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.awt.*;

public class SouthSideNotification extends Notification {
    // 基础颜色保留，但在渲染时我们会动态修改Alpha
    private static final int BASE_BG_COLOR = new Color(21, 22, 25).getRGB();
    private static final int BASE_ALPHA = 220;

    private static final int PROGRESS_BAR_COLOR = new Color(50, 100, 255).getRGB();
    private static final float FIXED_WIDTH = 180.0F;
    private static final float LEFT_PADDING = 10.0F;
    private static final float VERTICAL_PADDING = 10.0F;
    private static final float PROGRESS_BAR_HEIGHT = 2.0F;
    private static final float HEIGHT = 50.0F;

    public SouthSideNotification(NotificationLevel level, String message, long age) {
        super(level, message, age);
    }

    // 获取当前的动画进度/透明度因子 (0.0 ~ 1.0)
    private float getAlphaFactor() {
        long lifeTime = System.currentTimeMillis() - this.getCreateTime();
        long maxAge = this.getMaxAge();
        long timeLeft = maxAge - lifeTime;

        // 如果需要淡入淡出效果
        // 这里假设最后 500ms 淡出
        if (timeLeft < 500) {
            return Mth.clamp((float) timeLeft / 500.0F, 0.0F, 1.0F);
        }
        // 如果刚开始 500ms 淡入 (可选)
        if (lifeTime < 500) {
            return Mth.clamp((float) lifeTime / 500.0F, 0.0F, 1.0F);
        }
        return 1.0F;
    }

    @Override
    public void renderShader(PoseStack stack, float x, float y) {
        // 关键修复：阴影也需要应用透明度，否则主界面消失了阴影还在
        float alphaFactor = getAlphaFactor();
        if (alphaFactor <= 0.05f) return; // 如果几乎看不见，就不渲染阴影，节省性能

        // 计算带动态Alpha的背景色
        int currentAlpha = (int) (BASE_ALPHA * alphaFactor);
        int colorWithAlpha = this.modifyAlpha(BASE_BG_COLOR, currentAlpha);

        RenderUtils.fill(stack, x, y, x + this.getWidth(), y + this.getHeight(), colorWithAlpha);
    }

    @Override
    public void render(PoseStack stack, float x, float y) {
        float alphaFactor = getAlphaFactor();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 设置全局 Shader 颜色透明度
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alphaFactor);

        GuiGraphics guiGraphics = new GuiGraphics(Minecraft.getInstance(), Minecraft.getInstance().renderBuffers().bufferSource());
        guiGraphics.pose().last().pose().mul(stack.last().pose());

        // 背景
        int currentAlpha = (int) (BASE_ALPHA * alphaFactor);
        int bgColor = this.modifyAlpha(BASE_BG_COLOR, currentAlpha);
        RenderUtils.fill(stack, x, y, x + this.getWidth(), y + this.getHeight(), bgColor);

        String title = getTitleForRender();
        String displayMessage;
        String originalMessage = this.getMessage();

        if (originalMessage.toLowerCase().contains("enabled") || originalMessage.toLowerCase().contains("disabled")) {
            displayMessage = "Toggled " + originalMessage;
        } else {
            displayMessage = originalMessage;
        }
        float textX = x + LEFT_PADDING;
        float textY = y + VERTICAL_PADDING;

        // 字体渲染也需要应用透明度
        // 注意：Fonts.render 通常需要接受 int color，你需要确保 color 包含 alpha
        int whiteWithAlpha = new Color(255, 255, 255, (int)(255 * alphaFactor)).getRGB();
        int grayWithAlpha = new Color(180, 180, 180, (int)(255 * alphaFactor)).getRGB();

        Fonts.harmony.render(guiGraphics.pose(), title, textX, textY, new Color(whiteWithAlpha, true), true, 0.35f);
        Fonts.harmony.render(guiGraphics.pose(), displayMessage, textX, textY + Fonts.harmony.getHeight(true, 0.35f) + 4, new Color(grayWithAlpha, true), true, 0.3f);

        // 进度条
        float lifeTime = (float)(System.currentTimeMillis() - this.getCreateTime());
        float progress = Math.min(lifeTime / (float)this.getMaxAge(), 1.0F);
        float progressBarWidth = this.getWidth() * progress;

        int progressColor = this.modifyAlpha(PROGRESS_BAR_COLOR, (int)(255 * alphaFactor));
        RenderUtils.fill(stack, x, y + this.getHeight() - PROGRESS_BAR_HEIGHT, x + progressBarWidth, y + this.getHeight(), progressColor);

        // 重置 Shader 颜色，避免影响后续渲染
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public float getWidth() {
        // (保持不变)
        String title = getTitleForRender();
        String displayMessage;
        String originalMessage = this.getMessage();
        if (originalMessage.toLowerCase().contains("enabled") || originalMessage.toLowerCase().contains("disabled")) {
            displayMessage = "Toggled " + originalMessage;
        } else {
            displayMessage = originalMessage;
        }
        float titleWidth = Fonts.harmony.getWidth(title, 0.35f);
        float messageWidth = Fonts.harmony.getWidth(displayMessage, 0.3f);

        float textWidth = Math.max(titleWidth, messageWidth);
        float requiredContentWidth = LEFT_PADDING + textWidth + LEFT_PADDING;

        return Math.max(FIXED_WIDTH, requiredContentWidth);
    }

    @Override
    public float getHeight() {
        return HEIGHT;
    }

    private String getTitleForRender() {
        // (保持不变)
        String title;
        switch (this.getLevel()) {
            case SUCCESS: title = "Success"; break;
            case WARNING: title = "Warning"; break;
            case ERROR: title = "Error"; break;
            case INFO: default: title = "Module"; break;
        }
        if (this.getLevel() == NotificationLevel.SUCCESS || this.getLevel() == NotificationLevel.ERROR) {
            String message = this.getMessage().toLowerCase();
            if (message.contains("enabled") || message.contains("disabled")) {
                title = "Module";
            }
        }
        return title;
    }

    private int modifyAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}