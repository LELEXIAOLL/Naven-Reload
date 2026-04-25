package com.heypixel.heypixelmod.obsoverlay.ui.notification;

import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender2D;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventShader;
import com.heypixel.heypixelmod.obsoverlay.utils.SmoothAnimationTimer;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationManager {
   private final List<Notification> notifications = new CopyOnWriteArrayList<>();
   private static final float BOTTOM_MARGIN = 30.0F;
   private static final float SPACING = 5.0F;
   public static boolean isConfigLoading = false;

   public List<Notification> getNotifications() {
      return this.notifications;
   }

   public void addNotification(Notification notification) {
      this.notifications.add(notification);
   }

   public void onRenderShader(EventShader e) {
      Window window = Minecraft.getInstance().getWindow();
      float screenHeight = (float)window.getGuiScaledHeight();
      float currentY = screenHeight - BOTTOM_MARGIN;

      for (int i = notifications.size() - 1; i >= 0; i--) {
         Notification notification = notifications.get(i);
         if (notification == null) continue;

         float animatedWidth = notification.getWidthTimer().value;
         float animatedHeight = notification.getHeightTimer().value;
         float notificationX = (float)window.getGuiScaledWidth() - animatedWidth + 2.0F;
         float notificationY = currentY - animatedHeight;
         notification.renderShader(e.getStack(), notificationX, notificationY);
         currentY -= (animatedHeight + SPACING);
      }
   }

   public void onRender(EventRender2D e) {
      Window window = Minecraft.getInstance().getWindow();
      float screenHeight = (float)window.getGuiScaledHeight();
      float currentY = screenHeight - BOTTOM_MARGIN;

      for (int i = notifications.size() - 1; i >= 0; i--) {
         Notification notification = notifications.get(i);
         if (notification == null) {
            notifications.remove(i);
            continue;
         }

         e.getStack().pushPose();
         SmoothAnimationTimer widthTimer = notification.getWidthTimer();
         SmoothAnimationTimer heightTimer = notification.getHeightTimer();
         long lifeTime = System.currentTimeMillis() - notification.getCreateTime();

         if (lifeTime > notification.getMaxAge()) {
            widthTimer.target = 0.0F;
            heightTimer.target = notification.getHeight();
            if (widthTimer.value < 1.0F) {
               this.notifications.remove(notification);
               e.getStack().popPose();
               continue;
            }
         } else {
            widthTimer.target = notification.getWidth();
            heightTimer.target = notification.getHeight();
         }

         widthTimer.update(true);
         heightTimer.update(true);
         float animatedWidth = widthTimer.value;
         float animatedHeight = heightTimer.value;
         float notificationX = (float)window.getGuiScaledWidth() - animatedWidth + 2.0F;
         float notificationY = currentY - animatedHeight;
         notification.render(e.getStack(), notificationX, notificationY);

         currentY -= (animatedHeight + SPACING);
         e.getStack().popPose();
      }
   }
}