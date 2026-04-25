package com.heypixel.heypixelmod.obsoverlay.ui.notification.Naven;

import com.heypixel.heypixelmod.obsoverlay.ui.notification.Notification;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.NotificationLevel;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.StencilUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import java.awt.*;

/**
 * Naven 风格的通知实现。
 */
public class NavenNotification extends Notification {

    public NavenNotification(NotificationLevel level, String message, long age) {
        super(level, message, age);
    }

    @Override
    public void renderShader(PoseStack stack, float x, float y) {
        RenderUtils.drawRoundedRect(stack, x + 2.0F, y + 4.0F, this.getWidth(), 20.0F, 5.0F, this.level.getColor());
    }

    @Override
    public void render(PoseStack stack, float x, float y) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        StencilUtils.write(false);
        RenderUtils.drawRoundedRect(stack, x + 2.0F, y + 4.0F, this.getWidth(), 20.0F, 5.0F, this.level.getColor());
        StencilUtils.erase(true);
        RenderUtils.fillBound(stack, x + 2.0F, y + 4.0F, this.getWidth(), 20.0F, this.level.getColor());
        Fonts.harmony.render(stack, this.message, (double)(x + 6.0F), (double)(y + 9.0F), Color.WHITE, true, 0.35);
        StencilUtils.dispose();
    }

    @Override
    public float getWidth() {
        float stringWidth = Fonts.harmony.getWidth(this.message, 0.35);
        return stringWidth + 12.0F;
    }

    @Override
    public float getHeight() {
        return 24.0F;
    }
}