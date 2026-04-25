package com.heypixel.heypixelmod.mixin.O;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Camera.class})
public abstract class MixinCamera {

   @Shadow private Vec3 position;
   @Shadow protected abstract void setPosition(Vec3 pPos);

   @Inject(
           at = {@At("HEAD")},
           method = {"getMaxZoom"},
           cancellable = true
   )
   private void getMaxZoom(double pStartingDistance, CallbackInfoReturnable<Double> cir) {
      if (Naven.getInstance() != null && Naven.getInstance().getModuleManager() != null) {
         com.heypixel.heypixelmod.obsoverlay.modules.impl.render.Camera cameraModule =
                 (com.heypixel.heypixelmod.obsoverlay.modules.impl.render.Camera) Naven.getInstance().getModuleManager().getModule(
                         com.heypixel.heypixelmod.obsoverlay.modules.impl.render.Camera.class
                 );

         if (cameraModule != null && cameraModule.isEnabled() && cameraModule.viewClipEnabled.getCurrentValue()) {
            cir.setReturnValue(pStartingDistance * (double)cameraModule.scale.getCurrentValue() * (double)cameraModule.personViewAnimation.value / 100.0);
            cir.cancel();
         }
      }
   }

   @Inject(method = "move", at = @At("HEAD"), cancellable = true)
   private void onMove(double pX, double pY, double pZ, CallbackInfo ci) {
      if (Naven.getInstance() != null && Naven.getInstance().getModuleManager() != null) {
         Freecam freecamModule = (Freecam) Naven.getInstance().getModuleManager().getModule(Freecam.class);
         if (freecamModule != null && freecamModule.isEnabled()) {
            if (freecamModule.isNoclipEnabled()) {
               this.setPosition(this.position.add(pX, pY, pZ));
               ci.cancel();
            }
         }
      }
   }

   @Inject(method = "setup", at = @At("RETURN"))
   private void onSetupReturn(BlockGetter pLevel, Entity pEntity, boolean pThirdPerson, boolean pThirdPersonReverse, float pPartialTick, CallbackInfo ci) {
      if (Naven.getInstance() != null && Naven.getInstance().getModuleManager() != null) {
         Freecam freecamModule = (Freecam) Naven.getInstance().getModuleManager().getModule(Freecam.class);
         if (freecamModule != null && freecamModule.isEnabled()) {
            this.setPosition(new Vec3(freecamModule.getFreecamX(), freecamModule.getFreecamY(), freecamModule.getFreecamZ()));
         }
      }
   }

   @Inject(
           method = "setup",
           at = @At("TAIL")
   )
   private void onCameraSetup(BlockGetter pLevel, net.minecraft.world.entity.Entity pEntity, boolean pThirdPerson, boolean pThirdPersonReverse, float pPartialTick, CallbackInfo ci) {
      if (Naven.getInstance() != null && Naven.getInstance().getModuleManager() != null) {
         com.heypixel.heypixelmod.obsoverlay.modules.impl.render.Camera cameraModule =
                 (com.heypixel.heypixelmod.obsoverlay.modules.impl.render.Camera) Naven.getInstance().getModuleManager().getModule(
                         com.heypixel.heypixelmod.obsoverlay.modules.impl.render.Camera.class
                 );
         if (cameraModule != null && cameraModule.isEnabled() && cameraModule.motionCameraEnabled.getCurrentValue() && pThirdPerson) {
            Vec3 currentPos = this.position;

            if (cameraModule.lastMotionCamX == 0.0D && cameraModule.lastMotionCamY == 0.0D && cameraModule.lastMotionCamZ == 0.0D) {
               cameraModule.lastMotionCamX = currentPos.x;
               cameraModule.lastMotionCamY = currentPos.y;
               cameraModule.lastMotionCamZ = currentPos.z;
            }

            double newX = Mth.lerp(cameraModule.motionCameraFactor.getCurrentValue(), cameraModule.lastMotionCamX, currentPos.x);
            double newY = Mth.lerp(cameraModule.motionCameraFactor.getCurrentValue(), cameraModule.lastMotionCamY, currentPos.y);
            double newZ = Mth.lerp(cameraModule.motionCameraFactor.getCurrentValue(), cameraModule.lastMotionCamZ, currentPos.z);

            this.setPosition(new Vec3(newX, newY, newZ));

            cameraModule.lastMotionCamX = newX;
            cameraModule.lastMotionCamY = newY;
            cameraModule.lastMotionCamZ = newZ;
         } else if (cameraModule != null) {
            cameraModule.lastMotionCamX = 0.0D;
            cameraModule.lastMotionCamY = 0.0D;
            cameraModule.lastMotionCamZ = 0.0D;
         }
      }
   }
}