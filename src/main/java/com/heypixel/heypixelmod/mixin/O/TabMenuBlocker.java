package com.heypixel.heypixelmod.mixin.O;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleManager;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.render.HUD;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public class TabMenuBlocker {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void cancelRender(CallbackInfo ci) {
        // 通过 Naven 类的单例实例获取 ModuleManager
        ModuleManager moduleManager = Naven.getInstance().getModuleManager();

        // 从 ModuleManager 中获取 HUD 模块实例
        HUD hudModule = (HUD) moduleManager.getModule(HUD.class);

        // 如果 HUD 模块存在且已启用，并且其 WaterMarkMode 为 "LingDong"，则取消渲染
        if (hudModule != null && hudModule.waterMark.getCurrentValue() && hudModule.waterMarkMode.isCurrentMode("LingDong") && hudModule.bettertab.getCurrentValue()) {
            ci.cancel();
        }
    }
}