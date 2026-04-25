package com.heypixel.heypixelmod.obsoverlay.modules.impl.move;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;

@ModuleInfo(
        name = "Sprint",
        description = "Automatically sprints",
        category = Category.MOVEMENT
)
public class Sprint extends Module {

   @EventTarget(0)
   public void onMotion(EventMotion e) {
      if (e.getType() != EventType.PRE || mc.player == null) {
         return;
      }

      // 关键修改：添加最高优先级的GUI检查
      // 如果当前有任何GUI界面是打开的
      if (mc.screen != null) {
         mc.options.keySprint.setDown(false);
         return;
      }

      // --- 只有在没有GUI界面时，才会执行以下自动疾跑逻辑 ---

      boolean canSprint = mc.player.input.forwardImpulse > 0 &&
              !mc.player.horizontalCollision &&
              !mc.player.isShiftKeyDown() &&
              !mc.player.isUsingItem();

      // 根据条件按下或松开疾跑键
      mc.options.keySprint.setDown(canSprint);
   }

   @Override
   public void onDisable() {
      // 确保在禁用模块时，疾跑状态被完全重置
      if (mc.options != null) {
         mc.options.keySprint.setDown(false);
      }
      if (mc.player != null) {
         mc.player.setSprinting(false);
      }
   }

   @Override
   public void onEnable() {
      // 禁用原版的切换疾跑，以避免冲突
      if (mc.options != null) {
         mc.options.toggleSprint().set(false);
      }
   }
}