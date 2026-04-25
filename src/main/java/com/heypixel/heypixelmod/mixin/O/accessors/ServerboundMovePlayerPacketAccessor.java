// 文件路径：src/main/java/com/heypixel/heypixelmod/mixin/O/accessors/ServerboundMovePlayerPacketAccessor.java
package com.heypixel.heypixelmod.mixin.O.accessors;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface ServerboundMovePlayerPacketAccessor {
   @Accessor("yRot")
   float getYRot();

   @Accessor("yRot")
   void setYRot(float var1);

   @Accessor("xRot")
   float getXRot();

   @Accessor("xRot")
   void setXRot(float var1);
}