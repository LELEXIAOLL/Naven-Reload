package com.heypixel.heypixelmod.obsoverlay.modules.impl.move;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMoveInput;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.combat.Aura;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import net.minecraft.world.entity.Entity;

@ModuleInfo(
        name = "TargetStrafe",
        description = "Automatically moves forward to follow the Aura target(Maybe failed)",
        category = Category.MOVEMENT
)
public class TargetStrafe extends Module {
    public FloatValue range = ValueBuilder.create(this, "Range")
            .setDefaultFloatValue(5.0F)
            .setMinFloatValue(1.0F)
            .setMaxFloatValue(6.0F)
            .setFloatStep(0.1F)
            .build()
            .getFloatValue();

    public FloatValue switchDelay = ValueBuilder.create(this, "SwitchDelay")
            .setDefaultFloatValue(1000.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(10000.0F)
            .setFloatStep(100.0F)
            .build()
            .getFloatValue();

    public BooleanValue collisionSmart = ValueBuilder.create(this, "Collision Smart")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private Entity currentTarget = null;
    private long lastTargetSwitchTime = 0;
    private boolean isIgnoringRange = false;

    @EventTarget
    public void onMoveInput(EventMoveInput event) {
        Aura aura = (Aura) Naven.getInstance().getModuleManager().getModule(Aura.class);
        if (aura == null || !aura.isEnabled()) {
            currentTarget = null;
            isIgnoringRange = false;
            return;
        }

        Entity auraTarget = Aura.getTarget();
        if (auraTarget == null) {
            currentTarget = null;
            isIgnoringRange = false;
            return;
        }
        boolean shouldIgnoreRange = false;
        if (collisionSmart.getCurrentValue()) {
            try {
                Speed speedModule = (Speed) Naven.getInstance().getModuleManager().getModule(Speed.class);
                if (speedModule != null && speedModule.isEnabled() &&
                        speedModule.mode.isCurrentMode("Collision") &&
                        speedModule.getCurrentBPS() > 8.0) {
                    shouldIgnoreRange = true;
                }
            } catch (Exception e) {
            }
        }
        if (!shouldIgnoreRange && mc.player != null &&
                mc.player.distanceTo(auraTarget) > range.getCurrentValue()) {
            currentTarget = null;
            isIgnoringRange = false;
            return;
        }
        if (shouldIgnoreRange) {
            isIgnoringRange = true;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTarget == null || currentTarget != auraTarget) {
            if (currentTime - lastTargetSwitchTime >= switchDelay.getCurrentValue()) {
                currentTarget = auraTarget;
                lastTargetSwitchTime = currentTime;
                isIgnoringRange = shouldIgnoreRange;
            } else {
                return;
            }
        }
        if (isIgnoringRange) {
            this.setSuffix("Smart");
        } else {
            this.setSuffix(null);
        }

        event.setForward(1.0F);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        currentTarget = null;
        isIgnoringRange = false;
        this.setSuffix(null);
    }
}