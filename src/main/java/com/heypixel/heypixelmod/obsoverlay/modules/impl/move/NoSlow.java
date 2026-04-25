package com.heypixel.heypixelmod.obsoverlay.modules.impl.move;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventSlowdown;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;

@ModuleInfo(
        name = "NoSlow",
        description = "Prevents slowdown when using items in main or off-hand.",
        category = Category.MOVEMENT
)
public class NoSlow extends Module {
    private final BooleanValue food = ValueBuilder.create(this, "Food")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private final BooleanValue potion = ValueBuilder.create(this, "Potion")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private final BooleanValue bow = ValueBuilder.create(this, "Bow")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private final BooleanValue forceJump = ValueBuilder.create(this, "Force Jump")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private int onGroundTicksCounter = 0;
    private boolean isForcingJump = false;

    @Override
    public void onEnable() {
        isForcingJump = false;
    }

    @Override
    public void onDisable() {
        if (isForcingJump) {
            mc.options.keyJump.setDown(false);
            isForcingJump = false;
        }
    }

    @EventTarget
    public void onSlowdown(EventSlowdown event) {
        if (mc.player == null || mc.level == null || !mc.player.isUsingItem()) {
            return;
        }

        if (onGroundTicksCounter % 3 == 0) {
            return;
        }

        InteractionHand activeHand = mc.player.getUsedItemHand();
        if (activeHand == null) return;

        ItemStack activeItemStack = mc.player.getItemInHand(activeHand);
        if (activeItemStack.isEmpty()) return;

        if (shouldApplyNoSlow(activeItemStack)) {
            event.setSlowdown(false);
        }
    }

    @EventTarget
    public void onTick(EventRunTicks event) {
        if (mc.player == null || event.getType() != EventType.PRE) {
            return;
        }

        if (mc.player.onGround()) {
            onGroundTicksCounter++;
        } else {
            onGroundTicksCounter = 0;
        }

        boolean shouldBeForcingJump = false;
        if (forceJump.getCurrentValue() && mc.player.isUsingItem()) {
            InteractionHand activeHand = mc.player.getUsedItemHand();
            if (activeHand != null) {
                ItemStack activeItemStack = mc.player.getItemInHand(activeHand);
                if (shouldApplyNoSlow(activeItemStack)) {
                    shouldBeForcingJump = true;
                }
            }
        }

        if (shouldBeForcingJump) {
            if (!isForcingJump && !mc.options.keyJump.isDown()) {
                mc.options.keyJump.setDown(true);
                isForcingJump = true;
            }
        } else {
            if (isForcingJump) {
                mc.options.keyJump.setDown(false);
                isForcingJump = false;
            }
        }
    }

    private boolean shouldApplyNoSlow(ItemStack itemStack) {
        Item item = itemStack.getItem();

        if (food.getCurrentValue() && item.isEdible()) {
            return true;
        }
        if (potion.getCurrentValue() && (item instanceof PotionItem || item instanceof MilkBucketItem)) {
            return true;
        }
        if (bow.getCurrentValue() && item instanceof BowItem) {
            return true;
        }

        return false;
    }
}