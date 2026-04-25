package com.heypixel.heypixelmod.obsoverlay.events.impl;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public class TabMenuBlocker {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void cancelRender(CallbackInfo ci) {
        if (true) { // 将此行替换为你的实际逻辑
            ci.cancel();
        }
    }
}