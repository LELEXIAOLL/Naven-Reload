package com.heypixel.heypixelmod.obsoverlay.modules.impl.move;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMoveInput;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventPacket;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRespawn;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.NetworkUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationManager;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BowlFoodItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.TridentItem;

@ModuleInfo(
        name = "Stuck",
        description = "Stuck in air!",
        category = Category.MOVEMENT
)
public class Stuck extends Module {
   int stage = 0;
   Packet<?> packet;
   // 记录拦截时的旋转，确保投掷物方向精准
   float interceptedYaw;
   float interceptedPitch;
   // 记录上一次发送给服务器的旋转
   float lastYaw;
   float lastPitch;
   boolean tryDisable = false;
   Queue<ServerboundPongPacket> packets = new ConcurrentLinkedQueue<>();

   @Override
   public void onEnable() {
      this.stage = 0;
      this.packet = null;
      if (RotationManager.rotations != null) {
         this.lastYaw = RotationManager.rotations.x;
         this.lastPitch = RotationManager.rotations.y;
      } else if (mc.player != null) {
         this.lastYaw = mc.player.getYRot();
         this.lastPitch = mc.player.getXRot();
      }
      this.tryDisable = false;
      this.packets.clear();
   }

   @Override
   public void setEnabled(boolean enabled) {
      if (mc.player == null) return;

      if (enabled) {
         super.setEnabled(true);
      } else {
         if (this.stage != 3) {
            this.tryDisable = true;
         } else {
            super.setEnabled(false);
         }
      }
   }

   @EventTarget
   public void onMotion(EventMotion e) {
      // 移除了 Scaffold 的检测逻辑

      if (e.getType() == EventType.PRE) {
         mc.player.setDeltaMovement(0.0, 0.0, 0.0);

         if (this.stage == 1) {
            this.stage = 2;

            // 使用拦截包时记录的精准旋转，而不是当前 tick 的旋转
            // 这能保证即使在高速甩头时丢珍珠，方向也是点击鼠标那一瞬间的方向
            float targetYaw = this.interceptedYaw;
            float targetPitch = this.interceptedPitch;

            // 如果 shouldRotate 返回 false (代表需要更新旋转)，则发送旋转包
            // 对于珍珠等投掷物，shouldRotate 现在会返回 false，从而进入此逻辑
            if (!this.shouldRotate() || (this.lastYaw != targetYaw || this.lastPitch != targetPitch)) {
               NetworkUtils.sendPacketNoEvent(new Rot(targetYaw, targetPitch, mc.player.onGround()));

               while (!this.packets.isEmpty()) {
                  NetworkUtils.sendPacketNoEvent((Packet<?>)this.packets.poll());
               }

               this.lastYaw = targetYaw;
               this.lastPitch = targetPitch;
            }

            NetworkUtils.sendPacketNoEvent(this.packet);
         }

         if (this.tryDisable) {
            NetworkUtils.sendPacketNoEvent(new Pos(mc.player.getX() + 1337.0, mc.player.getY(), mc.player.getZ() + 1337.0, mc.player.onGround()));

            while (!this.packets.isEmpty()) {
               NetworkUtils.sendPacketNoEvent((Packet<?>)this.packets.poll());
            }

            this.tryDisable = false;
         }
      }
   }

   private boolean shouldRotate() {
      if (this.packet instanceof ServerboundUseItemPacket blockPlacement) {
         ItemStack item = mc.player.getItemInHand(blockPlacement.getHand());

         // 如果是投掷物，返回 false (表示允许/强制旋转更新)
         if (isProjectile(item)) {
            return false;
         }

         // 原有逻辑：不是食物且不是弓（通常指方块），返回 true (锁定旋转)
         return !(item.getItem() instanceof BowlFoodItem) && !(item.getItem() instanceof BowItem);
      } else {
         return this.packet instanceof ServerboundPlayerActionPacket playerDigging
                 ? playerDigging.getAction() == Action.RELEASE_USE_ITEM && mc.player.getUseItem().getItem() instanceof BowItem
                 : false;
      }
   }

   // 辅助方法：判断是否为投掷物
   private boolean isProjectile(ItemStack item) {
      return item.getItem() instanceof EnderpearlItem ||
              item.getItem() instanceof SnowballItem ||
              item.getItem() instanceof EggItem ||
              item.getItem() instanceof SplashPotionItem ||
              item.getItem() instanceof LingeringPotionItem ||
              item.getItem() instanceof ExperienceBottleItem ||
              item.getItem() instanceof TridentItem;
   }

   @EventTarget
   public void onMoveInput(EventMoveInput e) {
      e.setForward(0.0F);
      e.setStrafe(0.0F);
      e.setJump(false);
      e.setSneak(false);
   }

   @EventTarget
   public void onRespawn(EventRespawn e) {
      this.stage = 3;
      this.packet = null;
      this.toggle();
   }

   @EventTarget(1)
   public void onPacket(EventPacket e) {
      if (e.getPacket() instanceof ServerboundMovePlayerPacket) {
         e.setCancelled(true);
      } else if (e.getPacket() instanceof ServerboundPongPacket) {
         this.packets.offer((ServerboundPongPacket)e.getPacket());
         e.setCancelled(true);
      } else if (e.getPacket() instanceof ServerboundUseItemPacket || e.getPacket() instanceof ServerboundPlayerActionPacket) {
         this.packet = e.getPacket();
         this.stage = 1;

         // 关键修改：在拦截包的瞬间记录玩家的朝向
         this.interceptedYaw = mc.player.getYRot();
         this.interceptedPitch = mc.player.getXRot();

         e.setCancelled(true);
      } else if (e.getPacket() instanceof ClientboundPlayerPositionPacket) {
         while (!this.packets.isEmpty()) {
            NetworkUtils.sendPacketNoEvent((Packet<?>)this.packets.poll());
         }

         this.stage = 3;
         this.toggle();
      }
   }
}