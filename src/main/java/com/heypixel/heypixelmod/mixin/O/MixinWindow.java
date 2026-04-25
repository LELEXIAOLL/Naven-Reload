package com.heypixel.heypixelmod.mixin.O;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
abstract class MixinWindow {
    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
    private void onSetTitle(String pTitle, CallbackInfo ci) {
        if (!pTitle.equals(Naven.TITLE)) {
            ci.cancel();
        }
    }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        Window thisWindow = (Window)(Object)this;
        thisWindow.setTitle(Naven.TITLE);
    }
}