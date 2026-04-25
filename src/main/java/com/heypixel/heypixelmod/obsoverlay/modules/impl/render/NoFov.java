package com.heypixel.heypixelmod.obsoverlay.modules.impl.render;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventUpdateFoV;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;

@ModuleInfo(
        name = "NoFov",
        description = "Locks your Field of View to a specific value.",
        category = Category.RENDER
)
public class NoFov extends Module {
    private final FloatValue fov = ValueBuilder.create(this, "FOV")
            .setDefaultFloatValue(1.0F)
            .setMaxFloatValue(3.0F)
            .setMinFloatValue(0.3F)
            .setFloatStep(0.01F)
            .build()
            .getFloatValue();

    @EventTarget
    public void onFovUpdate(EventUpdateFoV event) {
        event.setFov(fov.getCurrentValue());
        this.setSuffix(String.format("%.2f", fov.getCurrentValue()));
    }
}