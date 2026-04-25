package com.heypixel.heypixelmod.obsoverlay.ui.notification.LingDong;

import com.heypixel.heypixelmod.obsoverlay.ui.notification.Notification;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.NotificationStyle;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * 一个空的通知样式，作为触发器，让 LingDong 类接管通知的渲染。
 * 它自身不执行任何渲染操作。
 */
public class LingDongNotificationStyle implements NotificationStyle {

    @Override
    public void renderShader(Notification notification, PoseStack stack, float x, float y) {
        // 留空，由 LingDong.java 统一处理背景效果
    }

    @Override
    public void render(Notification notification, PoseStack stack, float x, float y) {
        // 留空，由 LingDong.java 渲染具体内容
    }

    @Override
    public float getWidth(Notification notification) {
        // 返回0，因为尺寸由 LingDong.java 动态计算
        return 0;
    }

    @Override
    public float getHeight(Notification notification) {
        // 返回0，因为尺寸由 LingDong.java 动态计算
        return 0;
    }
}