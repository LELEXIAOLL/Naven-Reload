package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventUpdate;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        name = "Freecam",
        description = "Simple freecam with through walls",
        category = Category.MISC
)
public class Freecam extends Module {

    private final FloatValue speed = ValueBuilder.create(this, "Speed")
            .setDefaultFloatValue(1.0F)
            .setFloatStep(0.1F)
            .setMinFloatValue(0.1F)
            .setMaxFloatValue(10.0F)
            .build()
            .getFloatValue();

    private final BooleanValue fakeBypassFly = ValueBuilder.create(this, "FakeBypassFly")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private double freecamX, freecamY, freecamZ;
    private int tickCounter = 0;

    @Override
    public void onEnable() {
        if (mc.player == null) {
            this.toggle();
            return;
        }
        this.freecamX = mc.player.getX();
        this.freecamY = mc.player.getY();
        this.freecamZ = mc.player.getZ();
        this.tickCounter = 0; // 重置计数器
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.options == null) return;

        this.setSuffix(String.format("%.1f", speed.getCurrentValue()));
        tickCounter++;

        float moveSpeed = speed.getCurrentValue();

        double forward = mc.options.keyUp.isDown() ? 1 : (mc.options.keyDown.isDown() ? -1 : 0);
        double strafe = mc.options.keyLeft.isDown() ? 1 : (mc.options.keyRight.isDown() ? -1 : 0);
        double vertical = mc.options.keyJump.isDown() ? 1 : (mc.options.keyShift.isDown() ? -1 : 0);

        float yaw = mc.player.getYRot();
        double radians = Math.toRadians(yaw);

        double motionX = -Math.sin(radians) * forward + Math.cos(radians) * strafe;
        double motionZ = Math.cos(radians) * forward + Math.sin(radians) * strafe;

        double length = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (length > 1) {
            motionX /= length;
            motionZ /= length;
        }

        this.freecamX += motionX * moveSpeed;
        this.freecamZ += motionZ * moveSpeed;
        double verticalMovement = vertical * moveSpeed;
        if (fakeBypassFly.getCurrentValue()) {
            final double BOB_AMOUNT = 0.5;
            verticalMovement += (tickCounter % 2 == 0) ? BOB_AMOUNT : -BOB_AMOUNT;
        }
        this.freecamY += verticalMovement;
    }

    public double getFreecamX() { return freecamX; }
    public double getFreecamY() { return freecamY; }
    public double getFreecamZ() { return freecamZ; }

    public boolean isNoclipEnabled() {
        return !fakeBypassFly.getCurrentValue();
    }
}