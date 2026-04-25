package com.heypixel.heypixelmod.obsoverlay.modules.impl.move;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;

@ModuleInfo(
        name = "Speed",
        description = "Speed up your movement",
        category = Category.MOVEMENT
)
public class Speed extends Module {
    final ModeValue mode = ValueBuilder.create(this, "Mode")
            .setModes("LegitJump", "Simple")
            .setDefaultModeIndex(0)
            .build()
            .getModeValue();

    private final BooleanValue legitSprint = ValueBuilder.create(this, "Legit Sprint")
            .setDefaultBooleanValue(true)
            .setVisibility(() -> mode.isCurrentMode("LegitJump"))
            .build()
            .getBooleanValue();



    private final FloatValue simpleSpeed = ValueBuilder.create(this, "Simple Speed")
            .setDefaultFloatValue(0.2F)
            .setMinFloatValue(0.1F)
            .setMaxFloatValue(2.0F)
            .setFloatStep(0.01F)
            .setVisibility(() -> mode.isCurrentMode("Simple"))
            .build()
            .getFloatValue();

    private boolean isSprinting = false;

    @EventTarget
    public void onMotion(EventMotion event) {
        if (event.getType() == EventType.PRE && mc.player != null) {
            this.setSuffix(mode.getCurrentMode());

            switch (mode.getCurrentMode()) {
                case "LegitJump":
                    handleLegitJump();
                    break;
                case "Simple":
                    handleSimpleSpeed();
                    break;
            }
        }
    }

    @EventTarget
    public void onUpdate(EventRunTicks event) {
        if (mc.player != null) {
            isSprinting = mc.player.zza > 0.8;
            if (mode.isCurrentMode("LegitJump") && legitSprint.getCurrentValue() && isSprinting) {
                mc.player.setSprinting(true);
            }
        }
    }

    private void handleLegitJump() {
        if (mc.player != null && mc.player.onGround() && isSprinting) {
            mc.player.jumpFromGround();
        }
    }

    private void handleSimpleSpeed() {
        if (mc.player != null && mc.player.onGround() && isSprinting) {
            float speed = simpleSpeed.getCurrentValue();
            double yaw = Math.toRadians(mc.player.getYRot());
            mc.player.setDeltaMovement(
                    mc.player.getDeltaMovement().x + (-Math.sin(yaw) * speed),
                    mc.player.getDeltaMovement().y,
                    mc.player.getDeltaMovement().z + (Math.cos(yaw) * speed)
            );
        }
    }



    @Override
    public void onDisable() {
        isSprinting = false;
    }

    public double getCurrentBPS() {
        return currentBPS;
    }

    public double currentBPS = 0.0;
}