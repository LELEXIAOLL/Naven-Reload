package com.heypixel.heypixelmod.obsoverlay.ui.notification.Reload;

import com.heypixel.heypixelmod.obsoverlay.ui.notification.Notification;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.NotificationStyle;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.StencilUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.awt.*;

/**
 * 一个美观、中等大小的白色主题通知样式，使用图片图标和裁剪的内部进度条，无阴影。
 * (增加了对特定错误消息的图标判断逻辑)
 */
public class ReloadNotificationStyle implements NotificationStyle {

    // --- 图片资源路径 ---
    private static final ResourceLocation SUCCESS_ICON = new ResourceLocation("heypixel", "textures/noti/yes.png");
    private static final ResourceLocation WARNING_ICON = new ResourceLocation("heypixel", "textures/noti/warn.png");
    private static final ResourceLocation ERROR_ICON = new ResourceLocation("heypixel", "textures/noti/error.png");
    private static final ResourceLocation INFO_ICON = new ResourceLocation("heypixel", "textures/noti/no.png");

    // --- 样式常量 ---
    private static final float PADDING = 8.0f;
    private static final float ICON_AREA_WIDTH = 30.0f;
    private static final float ICON_SIZE = 16.0f;
    private static final float PROGRESS_BAR_HEIGHT = 2.0f;
    private static final float TEXT_SCALE = 0.4f;
    private static final float CORNER_RADIUS = 6.0f;

    @Override
    public void renderShader(Notification notification, PoseStack stack, float x, float y) {
        // 无阴影
    }

    @Override
    public void render(Notification notification, PoseStack stack, float x, float y) {
        float width = getWidth(notification);
        float height = getHeight(notification);

        StencilUtils.write(false);
        RenderUtils.drawRoundedRect(stack, x, y, width, height, CORNER_RADIUS, Color.WHITE.getRGB());

        StencilUtils.erase(true);

        RenderUtils.drawRoundedRect(stack, x, y, width, height, CORNER_RADIUS, Color.WHITE.getRGB());

        RenderUtils.drawRoundedRect(stack, x, y, ICON_AREA_WIDTH, height, CORNER_RADIUS, notification.getLevel().getColor());
        RenderUtils.fill(stack, x + ICON_AREA_WIDTH - CORNER_RADIUS, y, x + ICON_AREA_WIDTH, y + height, notification.getLevel().getColor());

        // --- 【核心修改】 ---
        // 绘制图片图标，并加入特定判断
        ResourceLocation icon;
        switch (notification.getLevel()) {
            case SUCCESS:
                icon = SUCCESS_ICON;
                break;
            case WARNING:
                icon = WARNING_ICON;
                break;
            case ERROR:
                // 如果是错误通知，检查消息内容是否包含 "Disable"
                if (notification.getMessage().contains("Disable")) {
                    icon = INFO_ICON; // 如果包含，则显示 "no" 图标
                } else {
                    icon = ERROR_ICON; // 否则，显示常规的 "error" 图标
                }
                break;
            case INFO:
            default:
                icon = INFO_ICON;
                break;
        }
        // --- 修改结束 ---

        float iconX = x + (ICON_AREA_WIDTH - ICON_SIZE) / 2;
        float iconY = y + (height - ICON_SIZE - PROGRESS_BAR_HEIGHT) / 2;
        renderTexture(stack, icon, iconX, iconY, ICON_SIZE, ICON_SIZE);

        float textHeight = (float) Fonts.google.getHeight(true, TEXT_SCALE);
        float textX = x + ICON_AREA_WIDTH + PADDING / 2;
        float textY = y + (height - textHeight - PROGRESS_BAR_HEIGHT) / 2;
        Fonts.google.render(stack, notification.getMessage(), textX, textY, new Color(20, 20, 20), true, TEXT_SCALE);

        long lifeTime = System.currentTimeMillis() - notification.getCreateTime();
        if (lifeTime < notification.getMaxAge()) {
            float progress = (float) (notification.getMaxAge() - lifeTime) / notification.getMaxAge();
            float barWidth = width * progress;
            RenderUtils.fill(stack, x, y + height - PROGRESS_BAR_HEIGHT, x + barWidth, y + height, notification.getLevel().getColor());
        }

        StencilUtils.dispose();
    }

    @Override
    public float getWidth(Notification notification) {
        float stringWidth = Fonts.google.getWidth(notification.getMessage(), TEXT_SCALE);
        return ICON_AREA_WIDTH + PADDING / 2 + stringWidth + PADDING;
    }

    @Override
    public float getHeight(Notification notification) {
        return 28.0F;
    }

    private void renderTexture(PoseStack poseStack, ResourceLocation texture, float x, float y, float width, float height) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(matrix, x, y + height, 0).uv(0, 1).endVertex();
        bufferBuilder.vertex(matrix, x + width, y + height, 0).uv(1, 1).endVertex();
        bufferBuilder.vertex(matrix, x + width, y, 0).uv(1, 0).endVertex();
        bufferBuilder.vertex(matrix, x, y, 0).uv(0, 0).endVertex();

        BufferUploader.drawWithShader(bufferBuilder.end());
        RenderSystem.disableBlend();
    }
}