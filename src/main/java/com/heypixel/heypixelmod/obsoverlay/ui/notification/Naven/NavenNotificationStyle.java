package com.heypixel.heypixelmod.obsoverlay.ui.notification.Naven;

import com.heypixel.heypixelmod.obsoverlay.ui.notification.Notification;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.NotificationStyle;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.StencilUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import java.awt.*;

/**
 * "Naven" 风格的具体实现。
 */
public class NavenNotificationStyle implements NotificationStyle {

    @Override
    public void renderShader(Notification notification, PoseStack stack, float x, float y) {
        RenderUtils.drawRoundedRect(stack, x + 2.0F, y + 4.0F, getWidth(notification), 20.0F, 5.0F, notification.getLevel().getColor());
    }

    @Override
    public void render(Notification notification, PoseStack stack, float x, float y) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        StencilUtils.write(false);
        RenderUtils.drawRoundedRect(stack, x + 2.0F, y + 4.0F, getWidth(notification), 20.0F, 5.0F, notification.getLevel().getColor());
        StencilUtils.erase(true);
        RenderUtils.fillBound(stack, x + 2.0F, y + 4.0F, getWidth(notification), 20.0F, notification.getLevel().getColor());
        Fonts.harmony.render(stack, notification.getMessage(), (double)(x + 6.0F), (double)(y + 9.0F), Color.WHITE, true, 0.35);
        StencilUtils.dispose();
    }

    @Override
    public float getWidth(Notification notification) {
        float stringWidth = Fonts.harmony.getWidth(notification.getMessage(), 0.35);
        return stringWidth + 12.0F;
    }

    @Override
    public float getHeight(Notification notification) {
        return 24.0F;
    }
}