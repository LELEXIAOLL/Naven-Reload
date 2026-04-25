package com.heypixel.heypixelmod.mixin.O;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventUpdateHeldItem;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.combat.Aura;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemInHandRenderer.class})
public abstract class MixinItemInHandRenderer {

   public MixinItemInHandRenderer() {
   }

   @Redirect(
           method = {"tick"},
           at = @At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"
           )
   )
   public ItemStack hookMainHand(LocalPlayer player) {
      EventUpdateHeldItem event = new EventUpdateHeldItem(InteractionHand.MAIN_HAND, player.getMainHandItem());
      if (player == Minecraft.getInstance().player) {
         Naven.getInstance().getEventManager().call(event);
      }
      return event.getItem();
   }

   @Redirect(
           method = {"tick"},
           at = @At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"
           )
   )
   public ItemStack hookOffHand(LocalPlayer player) {
      EventUpdateHeldItem event = new EventUpdateHeldItem(InteractionHand.OFF_HAND, player.getOffhandItem());
      if (player == Minecraft.getInstance().player) {
         Naven.getInstance().getEventManager().call(event);
      }
      return event.getItem();
   }

   @Shadow
   public abstract void renderItem(LivingEntity livingEntity, ItemStack itemStack, ItemDisplayContext displayContext, boolean isLeftHanded, PoseStack poseStack, MultiBufferSource buffer, int combinedLight);

   @Inject(
           method = {"renderArmWithItem"},
           at = {@At("HEAD")},
           cancellable = true
   )
   public void onRenderArmWithItem(AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, CallbackInfo ci) {
      Aura aura = (Aura)Naven.getInstance().getModuleManager().getModule(Aura.class);
      boolean autoBlock = aura.shouldAutoBlock();

      if (hand == InteractionHand.MAIN_HAND && stack.getItem() instanceof SwordItem && autoBlock) {
         ci.cancel();
         int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
         this.translate((float)side * 0.56F, -0.52F + equippedProgress * -0.6F, -0.72, poseStack);
         this.translate((float)side * -0.1414214F, 0.08, 0.1414214, poseStack);
         this.rotate(-102.25F, 1.0F, 0.0F, 0.0F, poseStack);
         this.rotate((float)side * 13.365F, 0.0F, 1.0F, 0.0F, poseStack);
         this.rotate((float)side * 78.05F, 0.0F, 0.0F, 1.0F, poseStack);
         double f = Math.sin(swingProgress * swingProgress * Math.PI);
         double f1 = Math.sin(Math.sqrt(swingProgress) * Math.PI);
         this.rotate((float)(f * -20.0), 0.0F, 1.0F, 0.0F, poseStack);
         this.rotate((float)(f1 * -20.0), 0.0F, 0.0F, 1.0F, poseStack);
         this.rotate((float)(f1 * -80.0), 1.0F, 0.0F, 0.0F, poseStack);
         this.scale(1.0F, 1.0F, 1.0F, poseStack);
         boolean isRightHand = player.getMainArm() == HumanoidArm.RIGHT;
         this.renderItem(player, stack, isRightHand ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !isRightHand, poseStack, buffer, combinedLight);
      }
   }

   public void translate(double x, double y, double z, PoseStack matrixStack) {
      matrixStack.translate(x, y, z);
   }

   public void rotate(float angle, float x, float y, float z, PoseStack matrixStack) {
      matrixStack.mulPose((new Quaternionf()).rotationAxis(angle * ((float)Math.PI / 180F), x, y, z));
   }

   public void scale(float x, float y, float z, PoseStack matrixStack) {
      matrixStack.scale(x, y, z);
   }
}