package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.TimeHelper;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(
        name = "AutoRod",
        description = "Auto use rod knowback killaura or silentaim target",
        category = Category.COMBAT
)
public class AutoRod extends Module {
    private final FloatValue prepareSwitchTime = ValueBuilder.create(this, "Ready time(ms)")
            .setDefaultFloatValue(500.0F)
            .setFloatStep(50.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(1000.0F)
            .build()
            .getFloatValue();

    private final FloatValue rodAttackTime = ValueBuilder.create(this, "Attack time")
            .setDefaultFloatValue(200.0F)
            .setFloatStep(20.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(500.0F)
            .build()
            .getFloatValue();

    private final FloatValue switchBackTime = ValueBuilder.create(this, "Back time(ms)")
            .setDefaultFloatValue(200.0F)
            .setFloatStep(20.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(500.0F)
            .build()
            .getFloatValue();

    private final FloatValue freezeTime = ValueBuilder.create(this, "Cooldown(ms)")
            .setDefaultFloatValue(2000.0F)
            .setFloatStep(100.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(5000.0F)
            .build()
            .getFloatValue();

    private final TimeHelper timer = new TimeHelper();
    private State currentState = State.IDLE;
    private int originalSlot = -1;
    private int rodSlot = -1;
    private final Minecraft mc = Minecraft.getInstance();

    private enum State {
        IDLE,
        PREPARING,
        USING_ROD,
        SWITCHING_BACK,
        COOLDOWN
    }

    @Override
    public void onEnable() {
        currentState = State.IDLE;
        originalSlot = -1;
        rodSlot = -1;
        timer.reset();
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (event.getType() != EventType.PRE || mc.player == null) {
            return;
        }

        Aura aura = (Aura) Naven.getInstance().getModuleManager().getModule(Aura.class);
        SilentAim silentAim = (SilentAim) Naven.getInstance().getModuleManager().getModule(SilentAim.class);

        boolean shouldActivate = checkCombatModulesActive(aura, silentAim) && hasValidTarget(aura, silentAim);
        if (!shouldActivate) {
            currentState = State.IDLE;
            return;
        }

        switch (currentState) {
            case IDLE:
                rodSlot = findRodSlot();
                if (rodSlot == -1) break;

                originalSlot = mc.player.getInventory().selected;
                if (originalSlot == rodSlot) break;

                timer.reset();
                currentState = State.PREPARING;
                break;

            case PREPARING:
                if (timer.delay((long) prepareSwitchTime.getCurrentValue())) {
                    mc.player.getInventory().selected = rodSlot;
                    timer.reset();
                    currentState = State.USING_ROD;
                }
                break;

            case USING_ROD:
                if (mc.gameMode != null) {
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                }

                if (timer.delay((long) rodAttackTime.getCurrentValue())) {
                    timer.reset();
                    currentState = State.SWITCHING_BACK;
                }
                break;

            case SWITCHING_BACK:
                if (timer.delay((long) switchBackTime.getCurrentValue()) && originalSlot != -1) {
                    mc.player.getInventory().selected = originalSlot;
                    timer.reset();
                    currentState = State.COOLDOWN;
                }
                break;

            case COOLDOWN:
                if (timer.delay((long) freezeTime.getCurrentValue())) {
                    currentState = State.IDLE;
                }
                break;
        }
    }

    private boolean checkCombatModulesActive(Aura aura, SilentAim silentAim) {
        return (aura != null && aura.isEnabled()) || (silentAim != null && silentAim.isEnabled());
    }

    private boolean hasValidTarget(Aura aura, SilentAim silentAim) {
        boolean hasAuraTarget = aura != null && aura.target != null;
        boolean hasSilentAimTarget = silentAim != null && silentAim.getTargetRotation() != null && silentAim.isWorking();
        return hasAuraTarget || hasSilentAimTarget;
    }

    private int findRodSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.FISHING_ROD) {
                return i;
            }
        }
        return -1;
    }
}