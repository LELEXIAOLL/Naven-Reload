package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventPacket;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

@ModuleInfo(
        name = "Criticals",
        description = "Forces critical hits on every attack.",
        category = Category.COMBAT
)
public class Criticals extends Module {
    private boolean shouldJump = false;
    private int jumpTicks = 0;

    public Criticals() {
    }

    @Override
    public void onEnable() {
        shouldJump = false;
        jumpTicks = 0;
    }

    @Override
    public void onDisable() {
        if (mc.options.keyJump.isDown()) {
            mc.options.keyJump.setDown(false);
        }
        shouldJump = false;
        jumpTicks = 0;
    }

    @EventTarget
    public void onTick(EventRunTicks event) {
        if (event.getType() != EventType.PRE || mc.player == null) {
            return;
        }
        if (shouldJump) {
            mc.options.keyJump.setDown(true);
            shouldJump = false;
            jumpTicks = 2;
        }

        if (jumpTicks > 0) {
            jumpTicks--;
            if (jumpTicks == 0) {
                mc.options.keyJump.setDown(false);
            }
        }
    }

    @EventTarget
    public void onPacketSend(EventPacket event) {
        if (event.getType() != EventType.SEND || mc.player == null) {
            return;
        }

        if (event.getPacket() instanceof ServerboundInteractPacket) {
            if (mc.hitResult instanceof EntityHitResult) {
                Player player = mc.player;
                boolean canCrit = player.onGround() &&
                        !player.isInWater() &&
                        !player.isInLava() &&
                        !player.isPassenger() &&
                        !player.isCrouching() &&
                        player.fallDistance == 0.0F;

                if (canCrit) {
                    this.shouldJump = true;
                }
            }
        }
    }
}