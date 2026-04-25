package com.heypixel.heypixelmod.obsoverlay.ui.notification;

import com.heypixel.heypixelmod.obsoverlay.utils.SmoothAnimationTimer;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.Objects;

/**
 * 最终稳定版：Notification 具体实现类。
 * 采用延迟加载策略，仅在需要时才获取样式，以避免在客户端启动早期发生空指针异常。
 */
public class Notification {
   protected NotificationLevel level;
   protected String message;
   protected long maxAge;
   protected long createTime = System.currentTimeMillis();
   protected SmoothAnimationTimer widthTimer = new SmoothAnimationTimer(0.0F);
   protected SmoothAnimationTimer heightTimer = new SmoothAnimationTimer(0.0F);

   // 修改点 1: 不再持有 style 字段
   // private NotificationStyle style;

   /**
    * 构造函数现在非常干净，不执行任何可能依赖未初始化模块的操作。
    */
   public Notification(NotificationLevel level, String message, long age) {
      this.level = level;
      this.message = message;
      this.maxAge = age;
      // 修改点 2: 移除了 `this.style = NotificationFactory.getStyle();`
   }

   /**
    * 修改点 3: 在需要时实时从工厂获取样式并执行渲染。
    */
   public void renderShader(PoseStack stack, float x, float y) {
      NotificationFactory.getStyle().renderShader(this, stack, x, y);
   }

   /**
    * 修改点 3: 在需要时实时从工厂获取样式并执行渲染。
    */
   public void render(PoseStack stack, float x, float y) {
      NotificationFactory.getStyle().render(this, stack, x, y);
   }

   /**
    * 修改点 3: 在需要时实时从工厂获取样式并计算宽度。
    */
   public float getWidth() {
      return NotificationFactory.getStyle().getWidth(this);
   }

   /**
    * 修改点 3: 在需要时实时从工厂获取样式并计算高度。
    */
   public float getHeight() {
      return NotificationFactory.getStyle().getHeight(this);
   }


   // --- Getters and Setters (保持不变) ---
   public NotificationLevel getLevel() {
      return this.level;
   }

   public String getMessage() {
      return this.message;
   }

   public long getMaxAge() {
      return this.maxAge;
   }

   public long getCreateTime() {
      return this.createTime;
   }

   public SmoothAnimationTimer getWidthTimer() {
      return this.widthTimer;
   }

   public SmoothAnimationTimer getHeightTimer() {
      return this.heightTimer;
   }

   public void setLevel(NotificationLevel level) {
      this.level = level;
   }

   public void setMessage(String message) {
      this.message = message;
   }

   public void setMaxAge(long maxAge) {
      this.maxAge = maxAge;
   }

   public void setCreateTime(long createTime) {
      this.createTime = createTime;
   }

   public void setWidthTimer(SmoothAnimationTimer widthTimer) {
      this.widthTimer = widthTimer;
   }

   public void setHeightTimer(SmoothAnimationTimer heightTimer) {
      this.heightTimer = heightTimer;
   }

   // --- equals & hashCode (保持不变) ---
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Notification that = (Notification) o;
      return maxAge == that.maxAge && level == that.level && Objects.equals(message, that.message);
   }

   @Override
   public int hashCode() {
      return Objects.hash(level, message, maxAge);
   }


}