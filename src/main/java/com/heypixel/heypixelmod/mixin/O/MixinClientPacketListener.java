package com.heypixel.heypixelmod.mixin.O;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventServerSetPosition;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.Freecam;
import com.heypixel.heypixelmod.obsoverlay.utils.HttpUtils;
import java.io.IOException;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
   @Redirect(
           method = "handleMovePlayer",
           at = @At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;)V",
                   ordinal = 1
           )
   )
   public void onSendPacket(Connection instance, Packet<?> pPacket) {
      EventServerSetPosition event = new EventServerSetPosition(pPacket);
      Naven.getInstance().getEventManager().call(event);
      instance.send(event.getPacket());
   }

   @Inject(
           method = "handleLogin",
           at = @At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/telemetry/WorldSessionTelemetryManager;onPlayerInfoReceived(Lnet/minecraft/world/level/GameType;Z)V",
                   shift = At.Shift.AFTER
           ),
           cancellable = true
   )
   private void onLogin(ClientboundLoginPacket p_105030_, CallbackInfo ci) {
      try {
         HttpUtils.get("http://127.0.0.1:23233/api/setHook?hook=0");
      } catch (IOException ignored) {
      }
   }

   // 修正后的队伍包处理方法
   @Inject(
           method = "handleSetPlayerTeamPacket",
           at = @At("HEAD"),
           cancellable = true
   )
   private void onHandleTeamPacket(ClientboundSetPlayerTeamPacket packet, CallbackInfo ci) {
      try {
         // 直接使用getName()返回的String进行比较
         if (packet.getName().contains("collideRule")) {
            ci.cancel();
         }
      } catch (Exception e) {
         ci.cancel();
      }
   }
   @Inject(
           method = "handleLogin",
           at = @At("HEAD"),
           remap = false
   )
   private void disableSignatureCheck(CallbackInfo ci) {
      System.setProperty("com.mojang.eula.agree", "true");
      System.setProperty("ignoreInvalidSkinSignatures", "true");
   }
}