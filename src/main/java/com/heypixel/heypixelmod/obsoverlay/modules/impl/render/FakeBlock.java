// 文件路径: com/heypixel/heypixelmod/obsoverlay/modules/impl/render/FakeBlock.java

package com.heypixel.heypixelmod.obsoverlay.modules.impl.render;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.combat.Aura;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import net.minecraft.client.Minecraft;

@ModuleInfo(
        name = "FakeBlock",
        description = "Shows a fake blocking animation",
        category = Category.RENDER
)
public class FakeBlock extends Module {

    private static FakeBlock instance;

    public FakeBlock() {
        instance = this;
    }

    public static FakeBlock getInstance() {
        return instance;
    }

    public boolean shouldRenderBlockAnimation() {
        Aura aura = (Aura) Naven.getInstance().getModuleManager().getModule(Aura.class);
        if (aura == null) {
            return false;
        }

        boolean isAuraTriggered = aura.isEnabled()
                && aura.shouldAutoBlock.getCurrentValue()
                && Aura.target != null;

        if (isAuraTriggered) {
            return true;
        }

        boolean isManualTriggered = this.isEnabled()
                && Minecraft.getInstance().options.keyUse.isDown();

        return isManualTriggered;
    }
}