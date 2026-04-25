package com.heypixel.heypixelmod.obsoverlay.modules.impl.move;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.BlockItem;

@ModuleInfo(
        name = "SafeWalk",
        description = "Prevents you from falling off blocks",
        category = Category.MOVEMENT
)
public class SafeWalk extends Module {
   private final BooleanValue onlyBlock = ValueBuilder.create(this, "Only on holding a Block")
           .setDefaultBooleanValue(false)
           .build()
           .getBooleanValue();

   private final BooleanValue onlyBackward = ValueBuilder.create(this, "Only on Backward")
           .setDefaultBooleanValue(false)
           .build()
           .getBooleanValue();

   public static boolean isOnBlockEdge(float sensitivity) {
      if (mc.level == null || mc.player == null) return false;
      return !mc.level
              .getCollisions(mc.player, mc.player.getBoundingBox().move(0.0, -0.5, 0.0).inflate(-sensitivity, 0.0, -sensitivity))
              .iterator()
              .hasNext();
   }

   @EventTarget
   public void onMotion(EventMotion e) {
      if (e.getType() == EventType.PRE) {
         if (mc.player == null) return;
         boolean blockConditionMet = !onlyBlock.getCurrentValue() || (mc.player.getMainHandItem().getItem() instanceof BlockItem);
         boolean backwardConditionMet = !onlyBackward.getCurrentValue() || mc.options.keyDown.isDown();
         boolean baseCondition = mc.player.onGround() && isOnBlockEdge(0.3F);
         boolean shouldSneak = baseCondition && blockConditionMet && backwardConditionMet;

         mc.options.keyShift.setDown(shouldSneak);
      }
   }

   @Override
   public void onDisable() {
      boolean isHoldingShift = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyShift.getKey().getValue());
      mc.options.keyShift.setDown(isHoldingShift);
   }
}