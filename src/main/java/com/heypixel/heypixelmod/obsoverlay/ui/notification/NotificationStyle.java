package com.heypixel.heypixelmod.obsoverlay.ui.notification;

import com.mojang.blaze3d.vertex.PoseStack;

public interface NotificationStyle {

    //渲染通知的模糊/阴影等背景效果。
    void renderShader(Notification notification, PoseStack stack, float x, float y);

    //渲染通知的主要内容（背景、文字等）。
    void render(Notification notification, PoseStack stack, float x, float y);

    //获取通知的宽度。
    float getWidth(Notification notification);

    //获取通知的高度。
    float getHeight(Notification notification);
}