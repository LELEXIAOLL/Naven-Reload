package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventPacket;
import com.heypixel.heypixelmod.obsoverlay.files.FileManager;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.MathUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

@ModuleInfo(
        name = "Disabler",
        category = Category.MISC,
        description = "Disables some checks of the anti cheat."
)
public class Disabler extends Module {
   // --- 设置 ---
   private final BooleanValue logging = ValueBuilder.create(this, "Logging").setDefaultBooleanValue(false).build().getBooleanValue();
   private final BooleanValue acaaimstep = ValueBuilder.create(this, "ACAAimStep").setDefaultBooleanValue(false).build().getBooleanValue();
   private final BooleanValue acaperfectrotation = ValueBuilder.create(this, "ACAPerfectRotation").setDefaultBooleanValue(false).build().getBooleanValue();
   private final BooleanValue grimDuplicateRotPlace = ValueBuilder.create(this, "Grim-DuplicateRotPlace")
           .setDefaultBooleanValue(false)
           .build()
           .getBooleanValue();

   // --- BadPackets 设置 ---
   private final BooleanValue badPacketsA = ValueBuilder.create(this, "BadPackets-A")
           .setDefaultBooleanValue(false)
           .build()
           .getBooleanValue();
   private final BooleanValue badPacketsD = ValueBuilder.create(this, "BadPackets-D")
           .setDefaultBooleanValue(false)
           .build()
           .getBooleanValue();
   private final BooleanValue badPacketsF = ValueBuilder.create(this, "BadPackets-F")
           .setDefaultBooleanValue(false)
           .build()
           .getBooleanValue();
   private final BooleanValue badPacketsG = ValueBuilder.create(this, "BadPackets-G")
           .setDefaultBooleanValue(false)
           .build()
           .getBooleanValue();
   private final BooleanValue badPacketY = ValueBuilder.create(this, "BadPacket-Y")
           .setDefaultBooleanValue(false)
           .build()
           .getBooleanValue();


   private float playerYaw;
   private float deltaYaw;
   private float lastPlacedDeltaYaw;
   private boolean rotated = false;
   private float lastYaw = 0.0F;
   private float lastPitch = 0.0F;
   private final Random random = new Random();
   private static final double[] PERFECT_PATTERNS = new double[]{0.1, 0.25};
   private static final Unsafe unsafe;
   private final LinkedBlockingQueue<Packet<?>> packets = new LinkedBlockingQueue<>();
   private static Disabler instance;

   // --- 状态变量 ---
   private int lastSlot = -1;
   private boolean lastSprinting = false;
   private boolean lastSneaking = false;

   private void log(String message) {
      if (this.logging.getCurrentValue()) {
         ChatUtils.addChatMessage("[Disabler] " + message);
      }
   }

   public Disabler() {
      super();
      instance = this;
   }

   public static Disabler getInstance() {
      return instance;
   }

   @Override
   public void onEnable() {
      reset();
   }

   @Override
   public void onDisable() {
      reset();
   }

   private void reset() {
      this.packets.clear();
      this.rotated = false;
      this.lastSlot = -1;
      this.lastSprinting = false;
      this.lastSneaking = false;
   }

   @EventTarget(3)
   public void onPacket(EventPacket e) {
      if (mc.player == null || e.getType() != EventType.SEND || e.isCancelled()) {
         return;
      }

      Packet<?> packet = e.getPacket();

      // --- BadPackets-A (Slot) ---
      if (badPacketsA.getCurrentValue() && packet instanceof ServerboundSetCarriedItemPacket setCarriedItemPacket) {
         if (setCarriedItemPacket.getSlot() == this.lastSlot) {
            e.setCancelled(true);
            log("BadPackets-A: Cancelled duplicate slot packet.");
         } else {
            this.lastSlot = setCarriedItemPacket.getSlot();
         }
      }

      // --- BadPackets-D (Pitch) ---
      if (badPacketsD.getCurrentValue() && packet instanceof ServerboundMovePlayerPacket movePacket) {
         if (movePacket.hasRotation()) {
            float pitch = movePacket.getXRot(0.0F);
            if (Math.abs(pitch) > 90) {
               e.setCancelled(true);
               log("BadPackets-D: Cancelled invalid pitch packet.");
            }
         }
      }

      // --- BadPackets-F (Sprint) & BadPackets-G (Sneak) ---
      if ((badPacketsF.getCurrentValue() || badPacketsG.getCurrentValue()) && packet instanceof ServerboundPlayerCommandPacket playerCommandPacket) {
         ServerboundPlayerCommandPacket.Action action = playerCommandPacket.getAction();

         if (badPacketsF.getCurrentValue()) {
            if (action == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
               if (this.lastSprinting) {
                  e.setCancelled(true);
                  log("BadPackets-F: Cancelled duplicate start sprint packet.");
               } else {
                  this.lastSprinting = true;
               }
            } else if (action == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) {
               if (!this.lastSprinting) {
                  e.setCancelled(true);
                  log("BadPackets-F: Cancelled duplicate stop sprint packet.");
               } else {
                  this.lastSprinting = false;
               }
            }
         }

         if (badPacketsG.getCurrentValue()) {
            if (action == ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY) {
               if (this.lastSneaking) {
                  e.setCancelled(true);
                  log("BadPackets-G: Cancelled duplicate start sneak packet.");
               } else {
                  this.lastSneaking = true;
               }
            } else if (action == ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY) {
               if (!this.lastSneaking) {
                  e.setCancelled(true);
                  log("BadPackets-G: Cancelled duplicate stop sneak packet.");
               } else {
                  this.lastSneaking = false;
               }
            }
         }
      }

      if (badPacketY.getCurrentValue() && packet instanceof ServerboundSetCarriedItemPacket slotPacket) {
         int slotValue = slotPacket.getSlot();
         if (slotValue < 0 || slotValue > 8) {
            int clampedSlot = Math.max(0, Math.min(8, slotValue));
            e.setPacket(new ServerboundSetCarriedItemPacket(clampedSlot));
         }
      }

      // --- Grim-DuplicateRotPlace ---
      if (this.grimDuplicateRotPlace.currentValue && e.getType() == EventType.SEND && !e.isCancelled() && mc.player != null) {
         if (e.getPacket() instanceof ServerboundMovePlayerPacket) {
            ServerboundMovePlayerPacket movePlayerPacket = (ServerboundMovePlayerPacket) e.getPacket();
            if (movePlayerPacket.hasRotation()) {
               float yaw = movePlayerPacket.getYRot(0.0F);
               float xRot = movePlayerPacket.getXRot(0.0F);
               boolean modified = false;
               float newYaw = yaw;

               // 1. 先处理重复旋转检测逻辑
               if (this.rotated) {
                  float deltaYaw = Math.abs(yaw - this.playerYaw);
                  if (deltaYaw > 2.0F) {
                     float xDiff = Math.abs(deltaYaw - this.lastPlacedDeltaYaw);
                     if (xDiff < 1.0E-4) {
                        this.log("Disabling DuplicateRotPlace!");
                        newYaw = yaw + 0.002F; // 添加微小偏移破坏重复模式
                        modified = true;
                     }
                  }
               }

               // 2. 更新旋转状态（必须在包修改前记录原始值）
               float lastPlayerYaw = this.playerYaw;
               this.playerYaw = modified ? newYaw : yaw; // 记录修改后的yaw
               this.deltaYaw = Math.abs(this.playerYaw - lastPlayerYaw);
               this.rotated = true;

               // 3. 应用720度偏移（避免干扰重复检测）
               if (yaw > -360.0F && yaw < 360.0F) {
                  newYaw += 720.0F;
                  modified = true;
               }

               // 4. 应用所有修改
               if (modified) {
                  if (movePlayerPacket.hasPosition()) {
                     e.setPacket(new PosRot(
                             movePlayerPacket.getX(0.0),
                             movePlayerPacket.getY(0.0),
                             movePlayerPacket.getZ(0.0),
                             newYaw,
                             xRot,
                             movePlayerPacket.isOnGround()
                     ));
                  } else {
                     e.setPacket(new Rot(newYaw, xRot, movePlayerPacket.isOnGround()));
                  }
               }
            }
         } else if (e.getPacket() instanceof ServerboundUseItemOnPacket) {
            // 只在确实有旋转时记录（避免误触发）
            if (this.rotated) {
               this.lastPlacedDeltaYaw = this.deltaYaw;
               this.rotated = false;
            }
         }
      }

      // --- ACA Aim & Rotation ---
      if ((this.acaaimstep.getCurrentValue() || this.acaperfectrotation.getCurrentValue()) && packet instanceof ServerboundMovePlayerPacket movePacket) {
         ServerboundMovePlayerPacket currentMovePacket = (ServerboundMovePlayerPacket) e.getPacket();

         float currentYaw = getPacketYRot(currentMovePacket);
         float currentPitch = getPacketXRot(currentMovePacket);
         boolean modified = false;

         if (this.acaaimstep.getCurrentValue() && this.shouldModifyRotation(currentYaw, currentPitch)) {
            float[] modifiedRotation = this.getModifiedRotation(currentYaw, currentPitch);
            currentYaw = modifiedRotation[0];
            currentPitch = modifiedRotation[1];
            modified = true;
         }

         if (this.acaperfectrotation.getCurrentValue()) {
            float[] antiPerfectRotation = this.getAntiPerfectRotation(currentYaw, currentPitch);
            if (antiPerfectRotation[0] != currentYaw || antiPerfectRotation[1] != currentPitch) {
               currentYaw = antiPerfectRotation[0];
               currentPitch = antiPerfectRotation[1];
               modified = true;
               this.log("PerfectRotation: Modified rotation");
            }
         }

         if (modified) {
            setPacketYRot(currentMovePacket, currentYaw);
            setPacketXRot(currentMovePacket, MathUtils.clampPitch_To90(currentPitch));
         }

         this.lastYaw = getPacketYRot(currentMovePacket);
         this.lastPitch = getPacketXRot(currentMovePacket);
      }
   }

   // --- 反射和工具方法 (保持不变) ---
   private float normalizeYaw(float yaw) {
      while (yaw > 180.0F) {
         yaw -= 360.0F;
      }
      while (yaw < -180.0F) {
         yaw += 360.0F;
      }
      return yaw;
   }

   private boolean shouldModifyRotation(float currentYaw, float currentPitch) {
      if (this.lastYaw == 0.0F && this.lastPitch == 0.0F) {
         return false;
      } else {
         double yawDelta = (double)Math.abs(this.normalizeYaw(currentYaw - this.lastYaw));
         double pitchDelta = (double)Math.abs(currentPitch - this.lastPitch);
         boolean isStepYaw = yawDelta < 1.0E-5 && pitchDelta > 1.0;
         boolean isStepPitch = pitchDelta < 1.0E-5 && yawDelta > 1.0;
         return isStepYaw || isStepPitch;
      }
   }

   private float[] getModifiedRotation(float yaw, float pitch) {
      double yawDelta = (double)Math.abs(this.normalizeYaw(yaw - this.lastYaw));
      double pitchDelta = (double)Math.abs(pitch - this.lastPitch);
      float newYaw = yaw;
      float newPitch = pitch;
      if (yawDelta < 1.0E-5 && pitchDelta > 1.0) {
         newYaw = this.lastYaw + (float)(this.random.nextGaussian() * 0.001);
      }

      if (pitchDelta < 1.0E-5 && yawDelta > 1.0) {
         newPitch = this.lastPitch + (float)(this.random.nextGaussian() * 0.001);
      }

      return new float[]{newYaw, newPitch};
   }

   private float[] getAntiPerfectRotation(float yaw, float pitch) {
      if (this.lastYaw == 0.0F && this.lastPitch == 0.0F) {
         return new float[]{yaw, pitch};
      } else {
         double yawDelta = (double)Math.abs(this.normalizeYaw(yaw - this.lastYaw));
         double pitchDelta = (double)Math.abs(pitch - this.lastPitch);
         float newYaw = yaw;
         float newPitch = pitch;
         if (!this.isNoRotation(yawDelta) && this.isPerfectPattern(yawDelta)) {
            double jitter = this.random.nextGaussian() * 0.005;
            newYaw = yaw + (float)jitter;
         }

         if (!this.isNoRotation(pitchDelta) && this.isPerfectPattern(pitchDelta)) {
            double jitter = this.random.nextGaussian() * 0.005;
            newPitch = pitch + (float)jitter;
         }

         return new float[]{newYaw, newPitch};
      }
   }

   private boolean isNoRotation(double rotation) {
      return Math.abs(rotation) <= 1.0E-10 || this.isIntegerMultiple(360.0, rotation);
   }

   private boolean isPerfectPattern(double rotation) {
      if (!Double.isInfinite(rotation) && !Double.isNaN(rotation)) {
         for (double pattern : PERFECT_PATTERNS) {
            if (this.isIntegerMultiple(pattern, rotation)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean isIntegerMultiple(double reference, double value) {
      if (reference == 0.0) {
         return Math.abs(value) <= 1.0E-10;
      } else {
         double multiple = value / reference;
         return Math.abs(multiple - (double)Math.round(multiple)) <= 1.0E-10;
      }
   }

   public static float getPacketYRot(ServerboundMovePlayerPacket packet) {
      if (mc.gameMode == null) {
         return 0.0F;
      } else {
         Field yRotField = findField(packet.getClass(), "f_134121_");

         try {
            return yRotField.getFloat(packet);
         } catch (Exception var3) {
            FileManager.logger.error("Failed to get yrot field", var3);
            var3.printStackTrace();
            return 0.0F;
         }
      }
   }

   public static float getPacketXRot(ServerboundMovePlayerPacket packet) {
      if (mc.gameMode == null) {
         return 0.0F;
      } else {
         Field xRotField = findField(packet.getClass(), "f_134122_");

         try {
            return xRotField.getFloat(packet);
         } catch (Exception var3) {
            FileManager.logger.error("Failed to get xrot field", var3);
            var3.printStackTrace();
            return 0.0F;
         }
      }
   }

   private static Field findField(Class<?> clazz, String... fieldNames) {
      if (clazz != null && fieldNames != null && fieldNames.length != 0) {
         Exception failed = null;

         for (Class<?> currentClass = clazz; currentClass != null; currentClass = currentClass.getSuperclass()) {
            for (String fieldName : fieldNames) {
               if (fieldName != null) {
                  try {
                     Field f = currentClass.getDeclaredField(fieldName);
                     f.setAccessible(true);
                     if ((f.getModifiers() & 16) != 0) {
                        unsafe.putInt(f, (long)unsafe.arrayBaseOffset(boolean[].class), f.getModifiers() & -17);
                     }

                     return f;
                  } catch (Exception var9) {
                     failed = var9;
                  }
               }
            }
         }

         throw new Disabler.UnableToFindFieldException(failed);
      } else {
         throw new IllegalArgumentException("Class and fieldNames must not be null or empty");
      }
   }

   public static void setPacketYRot(ServerboundMovePlayerPacket packet, float yRot) {
      if (mc.gameMode != null) {
         Field yRotField = findField(packet.getClass(), "f_134121_");

         try {
            yRotField.setFloat(packet, yRot);
         } catch (Exception var4) {
            FileManager.logger.error("Failed to set yrot field", var4);
            var4.printStackTrace();
         }
      }
   }

   public static void setPacketXRot(ServerboundMovePlayerPacket packet, float xRot) {
      if (mc.gameMode != null) {
         Field xRotField = findField(packet.getClass(), "f_134122_");

         try {
            xRotField.setFloat(packet, xRot);
         } catch (Exception var4) {
            FileManager.logger.error("Failed to set xrot field", var4);
            var4.printStackTrace();
         }
      }
   }

   static {
      try {
         Field field = Unsafe.class.getDeclaredField("theUnsafe");
         field.setAccessible(true);
         unsafe = (Unsafe)field.get(null);
      } catch (Exception var1) {
         throw new RuntimeException(var1);
      }
   }

   private static class UnableToFindFieldException extends RuntimeException {
      private static final long serialVersionUID = 1L;

      public UnableToFindFieldException(Exception e) {
         super(e);
      }
   }
}