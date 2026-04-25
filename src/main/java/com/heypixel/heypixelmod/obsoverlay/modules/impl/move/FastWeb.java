package com.heypixel.heypixelmod.obsoverlay.modules.impl.move;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMoveInput;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventStuckInBlock;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventUpdate; // [新增] 导入 EventUpdate
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.MoveUtils; // [新增] 导入 MoveUtils
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder; // [新增] 导入 ValueBuilder
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue; // [新增] 导入 FloatValue
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue; // [新增] 导入 ModeValue
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB; // [新增] 导入 AABB
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        name = "FastWeb",
        category = Category.MOVEMENT,
        description = "Allows you to move faster in cobwebs."
)
public class FastWeb extends Module {
   public ModeValue mode = ValueBuilder.create(this, "Mode")
           .setDefaultModeIndex(0)
           .setModes("Heypixel", "Grim")
           .build()
           .getModeValue();
   public FloatValue flyspeed = ValueBuilder.create(this, "Speed")
           .setDefaultFloatValue(0.64F)
           .setMaxFloatValue(3.0F)
           .setMinFloatValue(0.01F)
           .setFloatStep(0.01F)
           .setVisibility(() -> mode.isCurrentMode("Grim")) // 设置显示条件
           .build()
           .getFloatValue();

   private int playerInWebTick = 0;
   private int ticksInWeb = 0;

   @Override
   public void onEnable() {
      this.playerInWebTick = 0;
      this.ticksInWeb = 0;
   }

   // --- Heypixel 模式的逻辑 ---
   @EventTarget
   public void onMotion(EventMotion e) {
      if (!mode.isCurrentMode("Heypixel")) return;

      if (e.getType() == EventType.POST && this.playerInWebTick < mc.player.tickCount) {
         this.ticksInWeb = 0;
      }
   }

   @EventTarget
   public void onJump(EventMoveInput e) {
      // 只有在 Heypixel 模式下才执行
      if (!mode.isCurrentMode("Heypixel")) return;

      if (this.ticksInWeb > 1) {
         e.setJump(false);
      }
   }

   @EventTarget
   public void onStuck(EventStuckInBlock e) {
      // 只有在 Heypixel 模式下才执行
      if (!mode.isCurrentMode("Heypixel")) return;

      if (e.getState().getBlock() == Blocks.COBWEB) {
         this.playerInWebTick = mc.player.tickCount;
         this.ticksInWeb++;
         if (this.ticksInWeb > 5) {
            Vec3 newSpeed = new Vec3(0.88, 1.88, 0.88);
            e.setStuckSpeedMultiplier(newSpeed);
         }
      }
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (!mode.isCurrentMode("Grim")) return;

      if (mc.player == null || mc.level == null) {
         return;
      }

      if (isInWeb()) {
         double motionX = 0;
         double motionZ = 0;
         double currentSpeed = this.flyspeed.getCurrentValue();

         if (MoveUtils.isMoving()) {
            float direction = MoveUtils.getDirection();
            motionX = -Math.sin(Math.toRadians(direction)) * currentSpeed;
            motionZ = Math.cos(Math.toRadians(direction)) * currentSpeed;
         }

         double verticalSpeed = 0;
         if (mc.options.keyJump.isDown()) {
            verticalSpeed = currentSpeed;
         } else if (mc.options.keyShift.isDown()) {
            verticalSpeed = -currentSpeed;
         }

         mc.player.setDeltaMovement(motionX, verticalSpeed, motionZ);
      }
   }
   private boolean isInWeb() {
      if (mc.player == null || mc.level == null) return false;

      AABB playerBox = mc.player.getBoundingBox();
      int minX = (int) Math.floor(playerBox.minX);
      int minY = (int) Math.floor(playerBox.minY);
      int minZ = (int) Math.floor(playerBox.minZ);
      int maxX = (int) Math.ceil(playerBox.maxX);
      int maxY = (int) Math.ceil(playerBox.maxY);
      int maxZ = (int) Math.ceil(playerBox.maxZ);

      for (int x = minX; x < maxX; x++) {
         for (int y = minY; y < maxY; y++) {
            for (int z = minZ; z < maxZ; z++) {
               if (mc.level.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).is(Blocks.COBWEB)) {
                  return true;
               }
            }
         }
      }
      return false;
   }
}