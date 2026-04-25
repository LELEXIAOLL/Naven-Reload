package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventHandlePacket;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;

@ModuleInfo(
        name = "IQboost",
        description = "lowiq",
        category = Category.MISC
)
public class IQboost extends Module {
    private final FloatValue IQ = ValueBuilder.create(this, "IQ")
            .setDefaultFloatValue(1337.0F)
            .setMinFloatValue(1337.0F)
            .setMaxFloatValue(1337.0F)
            .setFloatStep(1.0F)
            .build()
            .getFloatValue();
    @Override
    public void onEnable() {
        setSuffix("1337");
    }
}