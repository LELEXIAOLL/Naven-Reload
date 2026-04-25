package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

@ModuleInfo(
        name = "ShieldBreaker",
        category = Category.COMBAT,
        description = "Automatically breaks shields by silently attacking them."
)
public class ShieldBreaker extends Module {

    private final FloatValue spamCPS = ValueBuilder.create(this, "Spam CPS")
            .setDefaultFloatValue(10.0F)
            .setFloatStep(1.0F)
            .setMinFloatValue(1.0F)
            .setMaxFloatValue(20.0F)
            .build()
            .getFloatValue();

    private final FloatValue switchBackDelay = ValueBuilder.create(this, "Switch Back Delay (Ticks)")
            .setDefaultFloatValue(2.0F)
            .setFloatStep(1.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(10.0F)
            .build()
            .getFloatValue();

    private long lastAttackTime = 0;
    private int originalSlot = -1;
    private long attackTick = -1;

    public ShieldBreaker() {
    }

    @Override
    public void onDisable() {
        if (originalSlot != -1) {
            mc.player.getInventory().selected = originalSlot;
        }
        originalSlot = -1;
        attackTick = -1;
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;

        if (mc.player.isUsingItem()) {
            return;
        }

        if (originalSlot != -1) {
            if (mc.player.tickCount - attackTick >= this.switchBackDelay.getCurrentValue()) {
                mc.player.getInventory().selected = originalSlot;
                originalSlot = -1;
                attackTick = -1;
            }
            return;
        }

        long currentTime = System.currentTimeMillis();
        long attackInterval = (long) (1000 / this.spamCPS.getCurrentValue());
        if (currentTime - lastAttackTime < attackInterval) {
            return;
        }

        HitResult hitResult = mc.hitResult;

        if (hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof Player target) {
            if (isPlayerUsingShield(target)) {

                int axeSlot = findAxeInHotbar();
                if (axeSlot != -1) {

                    originalSlot = mc.player.getInventory().selected;
                    mc.player.getInventory().selected = axeSlot;

                    mc.gameMode.attack(mc.player, target);
                    mc.player.swing(InteractionHand.MAIN_HAND);

                    attackTick = mc.player.tickCount;
                    lastAttackTime = currentTime;
                }
            }
        }
    }

    private int findAxeInHotbar() {
        for (int i = 0; i < 9; ++i) {
            if (mc.player.getInventory().getItem(i).getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }

    private boolean isPlayerUsingShield(Entity entity) {
        return entity instanceof Player player && player.isUsingItem() && player.getUseItem().is(Items.SHIELD);
    }
}