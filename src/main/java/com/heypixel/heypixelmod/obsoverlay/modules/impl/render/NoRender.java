package com.heypixel.heypixelmod.obsoverlay.modules.impl.render;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventPacket;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

@ModuleInfo(
   name = "NoRender",
   description = "Disables visual effects rendering (blindness, darkness, nausea)",
   category = Category.RENDER
)
public class NoRender extends Module {
   
   @EventTarget
   public void onPacket(EventPacket event) {
      if (event.getPacket() instanceof ClientboundUpdateMobEffectPacket packet) {
         // 阻止坏效果的数据包
         if (isBadEffect(packet.getEffect())) {
            event.setCancelled(true);
         }
      }
   }
   
   @EventTarget
   public void onTick(EventRunTicks event) {
      if (event.getType() == EventType.POST && mc.player != null) {
         // 移除已经存在的坏效果
         for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            if (isBadEffect(effect.getEffect())) {
               mc.player.removeEffect(effect.getEffect());
            }
         }
      }
   }
   
   private boolean isBadEffect(MobEffect effect) {
      // 只清除客户端视觉效果，不影响服务端游戏机制
      return effect == MobEffects.BLINDNESS ||        // 失明 - 纯视觉效果
             effect == MobEffects.CONFUSION ||        // 恶心 - 视角摇摆
             effect == MobEffects.DARKNESS;           // 黑暗 - 纯视觉效果
   }
   
   @Override
   public void onEnable() {
      if (mc.player != null) {
         // 启用时立即清除所有坏效果
         for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            if (isBadEffect(effect.getEffect())) {
               mc.player.removeEffect(effect.getEffect());
            }
         }
      }
   }
}
