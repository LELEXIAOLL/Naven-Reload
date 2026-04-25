package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventAttackSlowdown;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import net.minecraft.client.Minecraft;

@ModuleInfo(
        name = "SuperKB",
        description = "High KB to target",
        category = Category.COMBAT
)
public class SuperKB extends Module{
    private boolean wasSprinting = false;
    private int resetSprintTimer = 0;
    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public void onDisable() {
        resetSprintState();
    }

    @Override
    public void onEnable() {
        resetSprintState();
    }

    private void resetSprintState() {
        resetSprintTimer = 0;
        if (wasSprinting && mc.player != null) {
            mc.player.setSprinting(true);
        }
        wasSprinting = false;
    }

    @EventTarget
    public void onAttack(EventAttackSlowdown event) {
        if (mc.player == null) return;


        wasSprinting = mc.player.isSprinting();


        if (wasSprinting) {
            mc.player.setSprinting(false);
            resetSprintTimer = 2;
        }
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (resetSprintTimer > 0) {
            resetSprintTimer--;

            if (resetSprintTimer == 0 && wasSprinting && mc.player != null) {
                mc.player.setSprinting(true);
                wasSprinting = false;
            }
        }
    }
}
