package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

@ModuleInfo(
        name = "AnchorExploder",
        category = Category.COMBAT,
        description = "Explodes anchors with glowstone in them when looking at them."
)
public class AnchorExploder extends Module {

    private final FloatValue delayTicks;
    private final FloatValue switchToSlot;
    private final BooleanValue switchBack;

    private int ticksWaited;
    private int originalSlot = -1;

    public AnchorExploder() {
        delayTicks = ValueBuilder.create(this, "Delay (Ticks)")
                .setDefaultFloatValue(4.0f)
                .setMinFloatValue(0.0f)
                .setMaxFloatValue(40.0f)
                .setFloatStep(1.0f)
                .build()
                .getFloatValue();

        switchToSlot = ValueBuilder.create(this, "Switch To Slot")
                .setDefaultFloatValue(1.0f)
                .setMinFloatValue(1.0f)
                .setMaxFloatValue(9.0f)
                .setFloatStep(1.0f)
                .build()
                .getFloatValue();

        switchBack = ValueBuilder.create(this, "Switch Back")
                .setDefaultBooleanValue(true)
                .build()
                .getBooleanValue();
    }

    @Override
    public void onEnable() {
        this.ticksWaited = 0;
        this.originalSlot = -1;
    }

    @Override
    public void onDisable() {
        if (mc.player != null && switchBack.getCurrentValue() && originalSlot != -1 && mc.player.getInventory().selected != originalSlot) {
            mc.player.getInventory().selected = originalSlot;
        }
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (event.getType() != EventType.PRE) {
            return;
        }

        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null || !mc.isWindowActive()) {
            return;
        }

        ticksWaited++;

        if (ticksWaited < delayTicks.getCurrentValue()) {
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult blockHitResult = (BlockHitResult) hitResult;
        BlockState blockState = mc.level.getBlockState(blockHitResult.getBlockPos());

        if (blockState.is(Blocks.RESPAWN_ANCHOR) && blockState.getValue(RespawnAnchorBlock.CHARGE) > 0) {
            if (originalSlot == -1) {
                originalSlot = mc.player.getInventory().selected;
            }

            int targetSlot = (int) switchToSlot.getCurrentValue() - 1;
            mc.player.getInventory().selected = targetSlot;

            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHitResult);
            mc.player.swing(InteractionHand.MAIN_HAND);

            ticksWaited = 0;

            if (!switchBack.getCurrentValue()) {
                originalSlot = -1;
            }
        } else {
            if (switchBack.getCurrentValue() && originalSlot != -1) {
                mc.player.getInventory().selected = originalSlot;
                originalSlot = -1;
            }
        }
    }
}