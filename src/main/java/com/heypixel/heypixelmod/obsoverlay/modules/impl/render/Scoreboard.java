package com.heypixel.heypixelmod.obsoverlay.modules.impl.render;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender2D;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRenderScoreboard;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRenderTabOverlay;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventShader;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

@ModuleInfo(
        name = "Scoreboard",
        description = "Modifies the scoreboard and Server name spoof",
        category = Category.RENDER
)
public class Scoreboard extends Module {
   // --- 保留你原有的设置 ---
   public BooleanValue hideScore = ValueBuilder.create(this, "Hide Red Score").setDefaultBooleanValue(true).build().getBooleanValue();
   public ModeValue displayStyle = ValueBuilder.create(this, "Display Mode")
           .setDefaultModeIndex(0)
           .setModes("Hypixel", "BMW")
           .build()
           .getModeValue();

   public FloatValue size = ValueBuilder.create(this, "Size")
           .setDefaultFloatValue(1.0F)  // 默认大小为 100%
           .setFloatStep(0.05F)         // 每次调整 5%
           .setMinFloatValue(0.5F)      // 最小 50%
           .setMaxFloatValue(4.0F)      // 最大 400%
           .build()
           .getFloatValue();

   // --- 新增：用于存储渲染数据，供 onShader 使用 ---
   private float lastX, lastY, lastWidth, lastHeight;
   private boolean shouldRenderBlur = false;
   private static final float CORNER_RADIUS = 8.0f; // 保持圆角一致

   /**
    * 这个方法由 ScoreBoard (静态渲染类) 调用，用于每帧更新尺寸和位置
    */
   public void updateRenderData(float x, float y, float width, float height) {
      this.lastX = x;
      this.lastY = y;
      this.lastWidth = width;
      this.lastHeight = height;
      this.shouldRenderBlur = true;
   }

   /**
    * 清除渲染数据，防止在计分板消失后残余模糊
    */
   public void clearRenderData() {
      this.shouldRenderBlur = false;
   }

   @EventTarget
   public void onRender2D(EventRender2D e) {
      // 在每一帧渲染开始时，都假设没有计分板。
      // 如果 Mixin 捕获到了计分板，它会通过 updateRenderData 重新将 shouldRenderBlur 设为 true。
      // 这样可以确保计分板消失时，模糊效果也会立刻消失。
      if (this.isEnabled()) {
         clearRenderData();
      }
   }

   @EventTarget
   public void onShader(EventShader e) {
      // 在 BLUR 通道，如果需要渲染，就绘制一个模糊的圆角矩形作为背景
      if (e.getType() == EventType.BLUR && this.shouldRenderBlur && this.isEnabled()) {
         if (this.lastWidth > 0 && this.lastHeight > 0) {
            RenderUtils.drawRoundedRect(e.getStack(), this.lastX, this.lastY, this.lastWidth, this.lastHeight, CORNER_RADIUS, Integer.MIN_VALUE);
         }
      }
   }

   // --- 保留你原有的方法 ---
   private String getReplacementText() {
      return displayStyle.isCurrentMode("Hypixel") ? "§e§lHypixel" : "§d§l宝马岛";
   }

   @EventTarget
   public void onRenderScoreboard(EventRenderScoreboard e) {
      String string = e.getComponent().getString();
      if (string.contains("布吉岛")) {
         MutableComponent textComponent = Component.literal(getReplacementText());
         textComponent.setStyle(e.getComponent().getStyle());
         e.setComponent(textComponent);
      }
   }

   @EventTarget
   public void onRenderTab(EventRenderTabOverlay e) {
      String string = e.getComponent().getString();
      if (string.contains("布吉岛")) {
         if (e.getType() == EventType.HEADER) {
            e.setComponent(Component.literal(getReplacementText()));
         } else if (e.getType() == EventType.FOOTER) {
            e.setComponent(Component.literal(""));
         }
      }
   }
}