package com.heypixel.heypixelmod.obsoverlay.modules.impl.render;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import net.minecraft.client.multiplayer.ClientLevel;

@ModuleInfo(
        name = "Weather",
        description = "Change the client-side weather",
        category = Category.RENDER
)
public class Weather extends Module {
    
    public ModeValue weatherMode = ValueBuilder.create(this, "Weather Mode")
            .setModes("Clear", "Rain", "Thunder")
            .setDefaultModeIndex(0)
            .build()
            .getModeValue();
    
    private int originalRainLevel = 0;
    private int originalThunderLevel = 0;
    
    @Override
    public void onEnable() {
        if (mc.level != null) {
            originalRainLevel = (int) (mc.level.getRainLevel(1.0F) * 100);
            originalThunderLevel = (int) (mc.level.getThunderLevel(1.0F) * 100);
        }
    }
    
    @Override
    public void onDisable() {
        if (mc.level != null) {
            mc.level.setRainLevel(originalRainLevel / 100.0f);
            mc.level.setThunderLevel(originalThunderLevel / 100.0f);
        }
    }
    
    @EventTarget
    public void onRender(EventRender event) {
        if (mc.level == null) return;
        String mode = weatherMode.getCurrentMode();
        ClientLevel level = mc.level;
        switch (mode) {
            case "Clear":
                level.setRainLevel(0.0f);
                level.setThunderLevel(0.0f);
                break;
            case "Rain":
                level.setRainLevel(1.0f);
                level.setThunderLevel(0.0f);
                break;
            case "Thunder":
                level.setRainLevel(1.0f);
                level.setThunderLevel(1.0f);
                break;
        }
    }
}