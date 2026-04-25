package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventUpdateHeldItem;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.InventoryUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;

@ModuleInfo(
        name = "AutoTools",
        description = "Automatically switches to the best tool or golden apples.",
        category = Category.MISC
)
public class AutoTools extends Module {
   // --- Existing Settings ---
   private final BooleanValue checkSword = ValueBuilder.create(this, "Check Sword").setDefaultBooleanValue(true).build().getBooleanValue();
   private final BooleanValue switchBack = ValueBuilder.create(this, "Switch Back").setDefaultBooleanValue(true).build().getBooleanValue();
   private final BooleanValue silent = ValueBuilder.create(this, "Silent")
           .setVisibility(this.switchBack::getCurrentValue)
           .setDefaultBooleanValue(true)
           .build()
           .getBooleanValue();

   // --- NEW SETTING ---
   private final BooleanValue switchEat = ValueBuilder.create(this, "SwitchEat")
           .setDefaultBooleanValue(false)
           .build()
           .getBooleanValue();

   private int originSlot = -1;

   @EventTarget
   public void onUpdateHeldItem(EventUpdateHeldItem e) {
      if (this.switchBack.getCurrentValue() && this.silent.getCurrentValue() && e.getHand() == InteractionHand.MAIN_HAND && this.originSlot != -1) {
         e.setItem(mc.player.getInventory().getItem(this.originSlot));
      }
   }

   @EventTarget
   public void onMotion(EventMotion e) {
      if(silent.getCurrentValue()){
         this.setSuffix("Silent");
      } else {
         this.setSuffix("Normal");
      }

      // We handle all logic in the PRE event and switch back in the POST event
      if (e.getType() == EventType.PRE) {
         // If a switch has already happened (for any reason), do nothing more.
         if (this.originSlot != -1) {
            return;
         }

         // --- NEW SwitchEat LOGIC ---
         if (this.switchEat.getCurrentValue() && mc.options.keyUse.isDown()) {
            int gappleSlot = findGappleSlot();
            // Check if a gapple is found and the current item is not a gapple (to prevent switching from gapple to gapple)
            if (gappleSlot != -1 && mc.player.getInventory().selected != gappleSlot) {
               this.originSlot = mc.player.getInventory().selected;
               mc.player.getInventory().selected = gappleSlot;
               return; // Return after switching to prevent tool logic from running
            }
         }

         // --- Existing AutoTool LOGIC ---
         if (mc.gameMode.isDestroying()) {
            if (this.checkSword.getCurrentValue() && mc.player.getMainHandItem().getItem() instanceof SwordItem) {
               return;
            }

            if (mc.hitResult != null && mc.hitResult.getType() == Type.BLOCK) {
               BlockHitResult hitResult = (BlockHitResult)mc.hitResult;
               int bestTool = this.getBestTool(hitResult.getBlockPos());
               if (bestTool != -1 && bestTool != mc.player.getInventory().selected) {
                  this.originSlot = mc.player.getInventory().selected;
                  mc.player.getInventory().selected = bestTool;
               }
            }
         }
      } else { // POST Event
         // --- UNIFIED SWITCH BACK LOGIC ---
         boolean isBreaking = mc.gameMode.isDestroying();
         boolean isEating = this.switchEat.getCurrentValue() && mc.options.keyUse.isDown();

         // If a switch has occurred, but neither of the triggering conditions are active anymore, switch back.
         if (this.switchBack.getCurrentValue() && this.originSlot != -1 && !isBreaking && !isEating) {
            mc.player.getInventory().selected = this.originSlot;
            this.originSlot = -1;
         }
      }
   }

   /**
    * Finds the best tool for breaking a block in the hotbar.
    */
   private int getBestTool(BlockPos pos) {
      BlockState blockState = mc.level.getBlockState(pos);
      Block block = blockState.getBlock();
      int slot = -1; // Default to -1 (not found)
      float bestSpeed = 1.0F;

      for (int index = 0; index < 9; index++) {
         ItemStack itemStack = mc.player.getInventory().getItem(index);
         if (!InventoryUtils.isGodItem(itemStack) && !itemStack.isEmpty()) {
            float speed = itemStack.getDestroySpeed(blockState);

            // In 1.20.1, getDestroySpeed already incorporates enchantments.
            // But we can add a small bonus for efficiency to prioritize it if speeds are similar.
            if (speed > 1.0F) {
               int efficiency = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, itemStack);
               if (efficiency > 0) {
                  speed += efficiency * 0.5f; // Add a small bonus
               }
            }

            if (speed > bestSpeed) {
               slot = index;
               bestSpeed = speed;
            }
         }
      }
      return slot;
   }

   private int findGappleSlot() {
      for (int i = 0; i < 9; i++) {
         ItemStack stack = mc.player.getInventory().getItem(i);
         if (stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
            return i;
         }
      }
      return -1;
   }
}