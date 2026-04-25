package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.mixin.O.accessors.LocalPlayerAccessor;
import com.heypixel.heypixelmod.mixin.O.accessors.MultiPlayerGameModeAccessor;
import com.heypixel.heypixelmod.obfuscation.JNICObf;
import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.*;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.LongJump;
import com.heypixel.heypixelmod.obsoverlay.utils.*;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.Rotation;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationManager;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

@JNICObf
@ModuleInfo(
        name = "Velocity",
        description = "Reduces knockback.",
        category = Category.COMBAT
)
public class Velocity extends Module {
   LinkedBlockingDeque<Packet<ClientGamePacketListener>> inBound = new LinkedBlockingDeque<>();
   public static Velocity.Stage stage = Velocity.Stage.IDLE;
   public static int grimTick = -1;
   public static int debugTick = 10;
   private boolean delay_reverseFlag = false;
   private int delay_chanceCounter = 0;
   private BackTrack backTrackModule;

   public ModeValue mode = ValueBuilder.create(this, "Mode")
           .setDefaultModeIndex(0)
           .setModes("GrimFull", "JumpReset", "GrimReduce", "GrimNoXZ", "Delay", "Web")
           .build()
           .getModeValue();

   private final FloatValue delay_ticks = ValueBuilder.create(this, "Delay Ticks")
           .setDefaultFloatValue(3.0F)
           .setMinFloatValue(1.0F)
           .setMaxFloatValue(20.0F)
           .setFloatStep(1.0F)
           .setVisibility(() -> mode.isCurrentMode("Delay"))
           .build()
           .getFloatValue();
   private final FloatValue delay_chance = ValueBuilder.create(this, "Delay Chance")
           .setDefaultFloatValue(100.0F)
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(100.0F)
           .setFloatStep(1.0F)
           .setVisibility(() -> mode.isCurrentMode("Delay"))
           .build()
           .getFloatValue();

   public BooleanValue log = ValueBuilder.create(this, "Logging")
           .setDefaultBooleanValue(false)
           .build()
           .getBooleanValue();

   public ModeValue fixMode = ValueBuilder.create(this, "FixMode")
           .setDefaultModeIndex(0)
           .setModes("None", "PlaceWater")
           .setVisibility(() -> mode.isCurrentMode("GrimFull"))
           .build()
           .getModeValue();
   public BooleanValue onlyGround = ValueBuilder.create(this, "OnlyGround")
           .setDefaultBooleanValue(false)
           .setVisibility(() -> mode.isCurrentMode("GrimFull"))
           .build()
           .getBooleanValue();

   private final FloatValue jumpReset_jumpTick = ValueBuilder.create(this, "JumpResetTick")
           .setDefaultFloatValue(1.0F)
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(5.0F)
           .setFloatStep(1.0F)
           .setVisibility(() -> mode.isCurrentMode("JumpReset"))
           .build()
           .getFloatValue();

   private final FloatValue knockbackReductionValue = ValueBuilder.create(this, "Knockback Reduction")
           .setDefaultFloatValue(0.07776F)
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(1.0F)
           .setFloatStep(0.0001F)
           .setVisibility(() -> mode.isCurrentMode("GrimReduce"))
           .build()
           .getFloatValue();
   private final FloatValue attacks = ValueBuilder.create(this, "Attack Count")
           .setDefaultFloatValue(4.0F)
           .setMinFloatValue(4.0F)
           .setMaxFloatValue(11.0F)
           .setFloatStep(1.0F)
           .setVisibility(() -> mode.isCurrentMode("GrimReduce"))
           .build()
           .getFloatValue();
   private final BooleanValue jumpReset = ValueBuilder.create(this, "Jump")
           .setDefaultBooleanValue(false)
           .setVisibility(() -> mode.isCurrentMode("GrimReduce"))
           .build()
           .getBooleanValue();
   private final FloatValue jumpTick = ValueBuilder.create(this, "JumpResetTick")
           .setDefaultFloatValue(0.0F)
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(9.0F)
           .setFloatStep(1.0F)
           .setVisibility(() -> mode.isCurrentMode("GrimReduce"))
           .build()
           .getFloatValue();
   private final FloatValue fovLimitValue = ValueBuilder.create(this, "FOV Limit")
           .setDefaultFloatValue(45.0F)
           .setMinFloatValue(30.0F)
           .setMaxFloatValue(180.0F)
           .setFloatStep(1.0F)
           .setVisibility(() -> mode.isCurrentMode("GrimReduce"))
           .build()
           .getFloatValue();
   private final FloatValue speedThresholdValue = ValueBuilder.create(this, "Speed Threshold")
           .setDefaultFloatValue(0.60F)
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(1.0F)
           .setFloatStep(0.01F)
           .setVisibility(() -> mode.isCurrentMode("GrimReduce"))
           .build()
           .getFloatValue();

   public ModeValue noxzmode = ValueBuilder.create(this, "NoXZ Mode")
           .setDefaultModeIndex(0)
           .setModes("old", "new", "best")
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ"))
           .build()
           .getModeValue();

   private final FloatValue grimNoXZ_old_attacks = ValueBuilder.create(this, "Count")
           .setDefaultFloatValue(4.0F)
           .setMinFloatValue(4.0F)
           .setMaxFloatValue(11.0F)
           .setFloatStep(1.0F)
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("old"))
           .build()
           .getFloatValue();
   private final BooleanValue grimNoXZ_old_jumpReset = ValueBuilder.create(this, "Jump Reset")
           .setDefaultBooleanValue(false)
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("old"))
           .build()
           .getBooleanValue();
   private final FloatValue grimNoXZ_old_jumpTick = ValueBuilder.create(this, "JumpResetTick")
           .setDefaultFloatValue(0.0F)
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(9.0F)
           .setFloatStep(1.0F)
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("old"))
           .build()
           .getFloatValue();
   private final FloatValue grimNoXZ_old_fovLimitValue = ValueBuilder.create(this, "FOV Limit")
           .setDefaultFloatValue(45.0F)
           .setMinFloatValue(30.0F)
           .setMaxFloatValue(180.0F)
           .setFloatStep(1.0F)
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("old"))
           .build()
           .getFloatValue();
   private final FloatValue grimNoXZ_old_speedThresholdValue = ValueBuilder.create(this, "Speed Threshold")
           .setDefaultFloatValue(0.60F)
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(1.0F)
           .setFloatStep(0.01F)
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("old"))
           .build()
           .getFloatValue();

   private final ModeValue noxzNew_mode = ValueBuilder.create(this, "SubMode")
           .setDefaultModeIndex(0)
           .setModes("OneTime", "PerTick")
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("new"))
           .build()
           .getModeValue();
   private final FloatValue noxzNew_attacks = ValueBuilder.create(this, "Attack Count")
           .setDefaultFloatValue(5.0F)
           .setMinFloatValue(1.0F)
           .setMaxFloatValue(5.0F)
           .setFloatStep(1.0F)
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("new"))
           .build()
           .getFloatValue();

   private final ModeValue noxzBest_mode = ValueBuilder.create(this, "Best Mode")
           .setDefaultModeIndex(0)
           .setModes("OneTime", "PerTick")
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("best"))
           .build()
           .getModeValue();
   private final FloatValue noxzBest_attacks = ValueBuilder.create(this, "Attack Count")
           .setDefaultFloatValue(4.0F)
           .setMinFloatValue(1.0F)
           .setMaxFloatValue(5.0F)
           .setFloatStep(1.0F)
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("best"))
           .build()
           .getFloatValue();
   private final FloatValue noxzBest_fovAngle = ValueBuilder.create(this, "FOV Angle")
           .setDefaultFloatValue(120.0F)
           .setMinFloatValue(30.0F)
           .setMaxFloatValue(180.0F)
           .setFloatStep(1.0F)
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("best"))
           .build()
           .getFloatValue();
   private final FloatValue noxzBest_blinkDuration = ValueBuilder.create(this, "Blink Duration (ms)")
           .setDefaultFloatValue(10.0F)
           .setMinFloatValue(5.0F)
           .setMaxFloatValue(100.0F)
           .setFloatStep(1.0F)
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("best"))
           .build()
           .getFloatValue();
   private final FloatValue noxzBest_attackRange = ValueBuilder.create(this, "Attack Range")
           .setDefaultFloatValue(2.0F)
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(4.0F)
           .setFloatStep(0.1F)
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("best"))
           .build()
           .getFloatValue();
   private final BooleanValue noxzBest_blinkEnabled = ValueBuilder.create(this, "Blink")
           .setDefaultBooleanValue(false)
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("best"))
           .build()
           .getBooleanValue();
   private final BooleanValue noxzBest_onlyInCombat = ValueBuilder.create(this, "Only In Combat")
           .setDefaultBooleanValue(false)
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("best"))
           .build()
           .getBooleanValue();

   private final BooleanValue grimNoXZ_noTargetJumpReset = ValueBuilder.create(this, "NoTargetJumpReset")
           .setDefaultBooleanValue(false)
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ"))
           .build()
           .getBooleanValue();

   private final BooleanValue grimNoXZ_autoSprint = ValueBuilder.create(this, "AutoSprint")
           .setDefaultBooleanValue(false)
           .setVisibility(() -> mode.isCurrentMode("GrimNoXZ"))
           .build()
           .getBooleanValue();


   private final BooleanValue onAttackRelease = ValueBuilder.create(this, "On Attack Release")
           .setDefaultBooleanValue(true)
           .setVisibility(() -> mode.isCurrentMode("Delay"))
           .build()
           .getBooleanValue();
   private final BooleanValue onGroundRelease = ValueBuilder.create(this, "On Ground Release")
           .setDefaultBooleanValue(true)
           .setVisibility(() -> mode.isCurrentMode("Delay"))
           .build()
           .getBooleanValue();

   private BlockHitResult result = null;
   private BlockPos waterPos = null;
   private Entity targetEntity;
   private boolean velocityInput = false;
   private boolean attacked = false;
   private int jumpResetTicks = 0;
   private double currentKnockbackSpeed = 0.0;
   private Entity grimNoXZ_targetEntity;
   private boolean grimNoXZ_velocityInput = false;
   private boolean grimNoXZ_attacked = false;
   private int grimNoXZ_jumpResetTicks = 0;
   private double grimNoXZ_currentKnockbackSpeed = 0.0;
   private int noxzNew_attackQueue = 0;
   private boolean noxzNew_receiveDamage = false;
   private int jumpReset_ticks = 0;
   private int web_originalSlot = -1;
   private int web_placeTick = 0;
   private int noTargetJumpResetTimer = 0;
   private boolean autoSprintActive = false;

   private Entity noxzBest_targetEntity = null;
   private int noxzBest_attackQueue = 0;
   private boolean noxzBest_receiveDamage = false;
   private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
   private ScheduledFuture<?> blinkEndTask = null;
   private boolean isBlinking = false;
   private final Queue<Packet<?>> blinkPackets = new LinkedList<>();


   @Override
   public void onEnable() {
      checkAndHandleBackTrackConflict();
      this.reset();
   }

   @Override
   public void onDisable() {
      if (DelayManager.getInstance().stopDelay(false, DelayManager.DelayModules.VELOCITY)) {
         log("Delay mode stopped on disable.");
      }
      this.reset();
   }

   private void checkAndHandleBackTrackConflict() {
      if (this.backTrackModule == null) {
         this.backTrackModule = (BackTrack) Naven.getInstance().getModuleManager().getModule(BackTrack.class);
         if (this.backTrackModule == null) return;
      }

      if (mode.isCurrentMode("Delay") && this.backTrackModule.isEnabled()) {
         this.backTrackModule.setEnabled(false);
         ChatUtils.addChatMessage("[Velocity] Delay与BackTrack冲突, 已自动禁用BackTrack。");
      }
   }

   public void reset() {
      if (mc.getConnection() != null) {
         stage = Velocity.Stage.IDLE;
         grimTick = -1;
         debugTick = 0;
         this.processPackets();
      }
      this.waterPos = null;
      this.result = null;
      this.velocityInput = false;
      this.attacked = false;
      this.jumpResetTicks = 0;
      this.targetEntity = null;
      this.currentKnockbackSpeed = 0.0;
      this.grimNoXZ_velocityInput = false;
      this.grimNoXZ_attacked = false;
      this.grimNoXZ_jumpResetTicks = 0;
      this.grimNoXZ_targetEntity = null;
      this.grimNoXZ_currentKnockbackSpeed = 0.0;
      this.noxzNew_attackQueue = 0;
      this.noxzNew_receiveDamage = false;
      this.jumpReset_ticks = 0;
      this.delay_reverseFlag = false;
      this.delay_chanceCounter = 0;
      this.web_originalSlot = -1;
      this.web_placeTick = 0;
      this.noTargetJumpResetTimer = 0;
      this.autoSprintActive = false;

      this.noxzBest_targetEntity = null;
      this.noxzBest_attackQueue = 0;
      this.noxzBest_receiveDamage = false;
      this.isBlinking = false;
      this.blinkPackets.clear();
      if (this.blinkEndTask != null && !this.blinkEndTask.isDone()) {
         this.blinkEndTask.cancel(false);
      }
   }

   @EventTarget
   public void onWorld(EventRespawn eventRespawn) {
      this.reset();
   }

   @EventTarget
   public void onPacket(EventHandlePacket e) {
      if (mode.isCurrentMode("Delay")) {
         checkAndHandleBackTrackConflict();
      }
      this.setSuffix(mode.getCurrentMode());
      if (mc.player == null || mc.getConnection() == null || mc.gameMode == null) return;

      Packet<?> packet = e.getPacket();

      if (mode.isCurrentMode("GrimNoXZ")) {
         if (noxzmode.isCurrentMode("new")) {
            if (packet instanceof ClientboundDamageEventPacket) {
               ClientboundDamageEventPacket damagePacket = (ClientboundDamageEventPacket) packet;
               if (damagePacket.entityId() == mc.player.getId()) {
                  this.noxzNew_receiveDamage = true;
               }
            }
         } else if (noxzmode.isCurrentMode("best")) {
            if (packet instanceof ClientboundDamageEventPacket) {
               ClientboundDamageEventPacket damagePacket = (ClientboundDamageEventPacket) packet;
               if (damagePacket.entityId() == mc.player.getId()) {
                  this.noxzBest_receiveDamage = true;
               }
            }
         }
      }

      if (mc.player.tickCount < 20) {
         this.reset();
         return;
      }

      if (mc.player.isDeadOrDying() || !mc.player.isAlive() || mc.player.getHealth() <= 0.0F || mc.screen instanceof ProgressScreen || mc.screen instanceof DeathScreen) {
         this.reset();
         return;
      }

      if (packet instanceof ClientboundLoginPacket) {
         this.reset();
         return;
      }

      if (mode.isCurrentMode("GrimFull")) {
         if (debugTick > 0 && mc.player.tickCount > 20) {
            if (stage == Velocity.Stage.BLOCK && packet instanceof ClientboundBlockUpdatePacket cbu && this.result != null && this.result.getBlockPos().equals(cbu.getPos())) {
               this.processPackets();
               Naven.skipTasks.clear();
               debugTick = 0;
               this.result = null;
               if (fixMode.isCurrentMode("PlaceWater") && waterPos != null) {
                  pickupWater();
               }
               return;
            }
            if (!(packet instanceof ClientboundSystemChatPacket) && !(packet instanceof ClientboundSetTimePacket)) {
               e.setCancelled(true);
               this.inBound.add((Packet<ClientGamePacketListener>) packet);
               return;
            }
         }
      }

      if (packet instanceof ClientboundSetEntityMotionPacket) {
         ClientboundSetEntityMotionPacket velocityPacket = (ClientboundSetEntityMotionPacket) packet;
         if (velocityPacket.getId() != mc.player.getId()) {
            return;
         }

         if (mode.isCurrentMode("Delay")) {
            if (canTriggerDelay()) {
               this.delay_chanceCounter += (int) this.delay_chance.getCurrentValue();
               if (this.delay_chanceCounter >= 100) {
                  this.delay_chanceCounter -= 100;

                  DelayManager.getInstance().stopDelay(true, DelayManager.DelayModules.VELOCITY);
                  DelayManager.getInstance().delayedPacketQueue.offer(velocityPacket);
                  e.setCancelled(true);
                  this.delay_reverseFlag = true;
                  log("Delay triggered. Knockback packet queued.");
                  return;
               } else {
                  log("Delay chance failed.");
               }
            }
         }
         else if (mode.isCurrentMode("GrimFull")) {
            if (onlyGround.getCurrentValue() && !mc.player.onGround()) {
               log("不在地面, 取消执行GrimFull");
               return;
            }
            if (velocityPacket.getYa() < 0 || mc.player.getMainHandItem().getItem() instanceof EnderpearlItem) {
               return;
            }
            if (fixMode.isCurrentMode("PlaceWater")) {
               if (mc.player.getOffhandItem().getItem() != Items.WATER_BUCKET) {
                  log("副手未检测到水桶，取消执行GrimFull逻辑。");
                  return;
               }
               this.placeWater();
            }
            grimTick = 2;
            debugTick = 100;
            stage = Velocity.Stage.TRANSACTION;
            e.setCancelled(true);
         }

         else if (mode.isCurrentMode("Web")) {
            if (web_placeTick > 0) return;

            if (mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.COBWEB)) {
               log("已经在蜘蛛网中，取消放置。");
               return;
            }

            int webSlot = InventoryUtils.getItemSlot(Items.COBWEB);
            if (webSlot == -1) {
               log("物品栏中未找到蜘蛛网。");
               return;
            }

            this.web_originalSlot = mc.player.getInventory().selected;
            mc.player.getInventory().selected = webSlot;
            this.web_placeTick = 2;

            log("Web模式触发，将在 " + web_placeTick + " ticks后执行。");
         }

         else if (mode.isCurrentMode("JumpReset")) {
            this.jumpReset_ticks = (int) this.jumpReset_jumpTick.getCurrentValue();
            log("JumpReset scheduled in " + this.jumpReset_ticks + " ticks");
         }
         else if (mode.isCurrentMode("GrimReduce")) {
            double x = (double)velocityPacket.getXa() / 8000.0;
            double z = (double)velocityPacket.getZa() / 8000.0;
            double speed = Math.sqrt(x * x + z * z);
            this.currentKnockbackSpeed = speed;

            if (speed < speedThresholdValue.getCurrentValue()) {
               log("Knockback too weak: " + String.format("%.2f", speed) + " < " + String.format("%.2f", speedThresholdValue.getCurrentValue()));
               return;
            }

            this.velocityInput = true;
            this.targetEntity = Aura.target;

            boolean inFOV = false;
            if (this.targetEntity != null) {
               Vec3 playerLookVec = mc.player.getLookAngle();
               Vec3 toTargetVec = new Vec3(targetEntity.getX() - mc.player.getX(), 0, targetEntity.getZ() - mc.player.getZ()).normalize();
               double dot = playerLookVec.x * toTargetVec.x + playerLookVec.z * toTargetVec.z;
               double angleRad = Math.acos(Mth.clamp(dot, -1.0, 1.0));
               double angleDeg = Math.toDegrees(angleRad);
               inFOV = angleDeg <= fovLimitValue.getCurrentValue();
               log("FOV Check: " + String.format("%.1f°", angleDeg) + (inFOV ? " <= " + String.format("%.1f°", fovLimitValue.getCurrentValue()) : " > " + String.format("%.1f°", fovLimitValue.getCurrentValue())));
            }

            if (this.targetEntity != null && inFOV) {
               boolean wasSprintingBefore = mc.player.isSprinting();
               if (!wasSprintingBefore) {
                  mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(mc.player.onGround()));
                  mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, Action.START_SPRINTING));
               }
               int attackCount = (int)this.attacks.getCurrentValue();
               for (int i = -1; i < attackCount; i++) {
                  if (mc.hitResult != null && mc.hitResult.getType() == Type.ENTITY) {
                     mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(this.targetEntity, false));
                     mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                  }
               }
               this.attacked = true;
               if (!wasSprintingBefore) {
                  mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, Action.STOP_SPRINTING));
               }
               log("Reduce: " + String.format("%.2f", speed));
            }
            if (this.jumpReset.getCurrentValue()) {
               this.jumpResetTicks = (int)this.jumpTick.getCurrentValue();
            }
         }
         else if (mode.isCurrentMode("GrimNoXZ")) {
            if (grimNoXZ_autoSprint.getCurrentValue()) {
               if (!mc.player.isSprinting()) {
                  mc.player.setSprinting(true);
                  this.autoSprintActive = true;
               }
            }

            if (grimNoXZ_noTargetJumpReset.getCurrentValue()) {
               Entity potentialTarget = findGrimNoXZTargetInternal();
               if (potentialTarget == null) {
                  this.noTargetJumpResetTimer = 2;
               }
            }

            if (noxzmode.isCurrentMode("old")) {
               double x = (double) velocityPacket.getXa() / 8000.0;
               double z = (double) velocityPacket.getZa() / 8000.0;
               double speed = Math.sqrt(x * x + z * z);
               this.grimNoXZ_currentKnockbackSpeed = speed;

               if (speed < grimNoXZ_old_speedThresholdValue.getCurrentValue()) {
                  log("Knockback too weak: " + String.format("%.2f", speed) + " < " + String.format("%.2f", grimNoXZ_old_speedThresholdValue.getCurrentValue()));
                  return;
               }

               this.grimNoXZ_velocityInput = true;
               this.grimNoXZ_targetEntity = Aura.target;

               boolean inFOV = false;
               if (this.grimNoXZ_targetEntity != null) {
                  Vec3 playerLookVec = mc.player.getLookAngle();
                  Vec3 toTargetVec = new Vec3(grimNoXZ_targetEntity.getX() - mc.player.getX(), 0, grimNoXZ_targetEntity.getZ() - mc.player.getZ()).normalize();
                  double dot = playerLookVec.x * toTargetVec.x + playerLookVec.z * toTargetVec.z;
                  double angleRad = Math.acos(Mth.clamp(dot, -1.0, 1.0));
                  double angleDeg = Math.toDegrees(angleRad);
                  inFOV = angleDeg <= grimNoXZ_old_fovLimitValue.getCurrentValue();
                  log("FOV Check: " + String.format("%.1f°", angleDeg) + (inFOV ? " <= " + String.format("%.1f°", grimNoXZ_old_fovLimitValue.getCurrentValue()) : " > " + String.format("%.1f°", grimNoXZ_old_fovLimitValue.getCurrentValue())));
               }

               if (this.grimNoXZ_targetEntity != null && inFOV) {
                  boolean wasSprintingBefore = mc.player.isSprinting();
                  if (!wasSprintingBefore) {
                     mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(mc.player.onGround()));
                     mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, Action.START_SPRINTING));
                  }
                  int attackCount = (int) this.grimNoXZ_old_attacks.getCurrentValue();
                  for (int i = -1; i < attackCount; i++) {
                     if (mc.hitResult != null && mc.hitResult.getType() == Type.ENTITY) {
                        mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(this.grimNoXZ_targetEntity, false));
                        mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                     }
                  }
                  this.grimNoXZ_attacked = true;
                  if (!wasSprintingBefore) {
                     mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, Action.STOP_SPRINTING));
                  }
                  log("Reduce: " + String.format("%.2f", speed));
               }
               if (this.grimNoXZ_old_jumpReset.getCurrentValue()) {
                  this.grimNoXZ_jumpResetTicks = (int) this.grimNoXZ_old_jumpTick.getCurrentValue();
               }
            }
            else if (noxzmode.isCurrentMode("new")) {
               this.velocityInput = true;
               this.targetEntity = Aura.target;
               if (this.noxzNew_receiveDamage) {
                  this.noxzNew_receiveDamage = false;
                  this.noxzNew_attackQueue = (int) this.noxzNew_attacks.getCurrentValue();
                  log("NoXZ Queue set: " + this.noxzNew_attackQueue + " attacks");
               }
            }
            else if (noxzmode.isCurrentMode("best")) {
               this.noxzBest_targetEntity = null;
               ClientLevel world = mc.level;
               if (world != null && mc.player != null) {
                  Vec3 playerPos = mc.player.position();
                  Vec3 lookVec = mc.player.getLookAngle();
                  float fov = this.noxzBest_fovAngle.getCurrentValue();
                  double halfFovRad = Math.toRadians((double)fov / 2.0);
                  halfFovRad = Math.min(halfFovRad, 1.5707963267948966);
                  Entity closestPlayer = null;
                  double closestDistance = Double.MAX_VALUE;
                  double range = (double)this.noxzBest_attackRange.getCurrentValue() + 2.0;
                  AABB searchArea = new AABB(playerPos.x - range, playerPos.y - range, playerPos.z - range, playerPos.x + range, playerPos.y + range, playerPos.z + range);
                  List<Entity> entities = world.getEntitiesOfClass(Entity.class, searchArea);
                  for (Entity entity : entities) {
                     Vec3 horizontalToEntity;
                     Vec3 horizontalLook;
                     double dot;
                     double angleRad;
                     double maxAttackRange;
                     Vec3 entityPos;
                     Vec3 relativePos;
                     double distance;
                     if (entity == mc.player || entity.isSpectator() || !entity.isAlive() || !this.isValidPlayerTarget(entity)) continue;

                     entityPos = entity.position();
                     relativePos = entityPos.subtract(playerPos);
                     distance = relativePos.length();
                     maxAttackRange = (double)this.noxzBest_attackRange.getCurrentValue();

                     if (distance > maxAttackRange) continue;

                     horizontalLook = new Vec3(lookVec.x, 0.0, lookVec.z).normalize();
                     horizontalToEntity = new Vec3(relativePos.x, 0.0, relativePos.z).normalize();
                     dot = horizontalLook.dot(horizontalToEntity);
                     angleRad = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));

                     if (angleRad <= halfFovRad && distance < closestDistance) {
                        closestDistance = distance;
                        closestPlayer = entity;
                     }
                  }
                  this.noxzBest_targetEntity = closestPlayer;
               }

               if (this.noxzBest_onlyInCombat.getCurrentValue() && this.noxzBest_targetEntity == null) {
                  log("Velocity cancelled: No target in FOV");
                  return;
               }

               if (this.noxzBest_targetEntity != null) {
                  double maxDistance = (double)this.noxzBest_attackRange.getCurrentValue();
                  double distance = mc.player.distanceTo(this.noxzBest_targetEntity);
                  if (distance > maxDistance) {
                     log("Velocity cancelled: Target out of range (" + distance + " > " + maxDistance + ")");
                     return;
                  }
               } else {
                  log("Velocity cancelled: No valid player found within FOV and range.");
                  return;
               }

               if (this.noxzBest_receiveDamage) {
                  this.noxzBest_receiveDamage = false;
                  this.noxzBest_attackQueue = (int)this.noxzBest_attacks.getCurrentValue();
                  log("NoXZ Queue set: " + this.noxzBest_attackQueue + " attacks, Target: " + (this.noxzBest_targetEntity != null ? this.noxzBest_targetEntity.getDisplayName().getString() : "None"));
               }
            }
         }
      }
   }

   private boolean canTriggerDelay() {
      if (mc.player == null || mc.level == null) return false;

      if (mc.player.onGround()) return false;

      if (mc.player.isInWater() || mc.player.isInLava() || mc.player.isInPowderSnow || mc.player.isVisuallyCrawling()) return false;

      if (Naven.getInstance().getModuleManager().getModule(LongJump.class).isEnabled()){
         return false;
      }
      if (this.delay_reverseFlag) return false;

      return true;
   }


   @EventTarget
   public void onSendPacket(EventPacket e) {
      if (mode.isCurrentMode("Delay") && this.delay_reverseFlag && onAttackRelease.getCurrentValue()) {
         if (e.getPacket() instanceof ServerboundInteractPacket interactPacket) {
            Aura aura = (Aura) Naven.getInstance().getModuleManager().getModule(Aura.class);
            if (aura.isEnabled() && aura.target != null) {
               stopDelayAndResetFlag("Attack");
            }
         }
      }

      if (mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("best")) {
         if (this.isBlinking && this.noxzBest_blinkEnabled.getCurrentValue() && e.getPacket() instanceof ServerboundMovePlayerPacket) {
            e.setCancelled(true);
            this.blinkPackets.offer(e.getPacket());
            log("Blink: Intercepted a move packet.");
         }
      }
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null) return;

      if (mode.isCurrentMode("Delay")) {
         checkAndHandleBackTrackConflict();
      }

      if (mode.isCurrentMode("Delay")) {
         if (this.delay_reverseFlag) {
            if (shouldStopDelay()) {
               stopDelayAndResetFlag("Condition Met");
            }
         }
      }
      if (this.noTargetJumpResetTimer > 0) {
         this.noTargetJumpResetTimer--;
      }
      if (mode.isCurrentMode("JumpReset")) {
         if (this.jumpReset_ticks > 0) {
            this.jumpReset_ticks--;
         }
      }

      if (mode.isCurrentMode("GrimReduce")) {
         if (mc.player.hurtTime == 0) {
            this.velocityInput = false;
            this.currentKnockbackSpeed = 0.0;
         }
         if (this.jumpResetTicks > 0) {
            this.jumpResetTicks--;
         }
         if (this.velocityInput && this.attacked) {
            if (this.targetEntity != null && mc.hitResult != null && mc.hitResult.getType() == Type.ENTITY) {
               mc.player.setDeltaMovement(
                       mc.player.getDeltaMovement().x * knockbackReductionValue.getCurrentValue(),
                       mc.player.getDeltaMovement().y,
                       mc.player.getDeltaMovement().z * knockbackReductionValue.getCurrentValue()
               );
               log("Applied velocity reduction");
            }
            this.attacked = false;
         }
      }
      else if (mode.isCurrentMode("GrimNoXZ")) {
         if (noxzmode.isCurrentMode("old")) {
            if (mc.player.hurtTime == 0) {
               this.grimNoXZ_velocityInput = false;
               this.grimNoXZ_currentKnockbackSpeed = 0.0;
            }
            if (this.grimNoXZ_jumpResetTicks > 0) {
               this.grimNoXZ_jumpResetTicks--;
            }
            if (this.grimNoXZ_velocityInput && this.grimNoXZ_attacked) {
               if (this.grimNoXZ_targetEntity != null && mc.hitResult != null && mc.hitResult.getType() == Type.ENTITY) {
                  mc.player.setDeltaMovement(mc.player.getDeltaMovement().x * 0.07776, mc.player.getDeltaMovement().y, mc.player.getDeltaMovement().z * 0.07776);
                  log("Applied velocity reduction");
               }
               this.grimNoXZ_attacked = false;
            }
         }
         else if (noxzmode.isCurrentMode("new")) {
            if (mc.player.hurtTime == 0) {
               this.velocityInput = false;
            }
            if (this.targetEntity != null && this.noxzNew_attackQueue > 0) {
               if (this.noxzNew_mode.isCurrentMode("OneTime")) {
                  for (; this.noxzNew_attackQueue >= 1; this.noxzNew_attackQueue--) {
                     mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(this.targetEntity, mc.player.isShiftKeyDown()));
                     mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(0.6, 1, 0.6));
                     mc.player.setSprinting(false);
                     mc.player.swing(InteractionHand.MAIN_HAND);
                  }
                  log("NoXZ OneTime attacks executed");
               } else if (this.noxzNew_mode.isCurrentMode("PerTick")) {
                  if (this.noxzNew_attackQueue >= 1) {
                     mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(this.targetEntity, mc.player.isShiftKeyDown()));
                     mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(0.6, 1, 0.6));
                     mc.player.setSprinting(false);
                     mc.player.swing(InteractionHand.MAIN_HAND);
                     log("NoXZ PerTick attack executed, remaining: " + (this.noxzNew_attackQueue - 1));
                  }
                  this.noxzNew_attackQueue--;
               }
            }
         }

         if (this.autoSprintActive) {
            if (!mc.options.keyUp.isDown()) {
               mc.player.setSprinting(false);
            }
            this.autoSprintActive = false;
         }
      }
   }

   @EventTarget
   public void onMoveInput(EventMoveInput event) {
      if (mode.isCurrentMode("JumpReset")) {
         if (mc.player != null && mc.player.onGround() && this.jumpReset_ticks == 1) {
            event.setJump(true);
            this.jumpReset_ticks = 0;
            log("Jump reset activated");
         }
      }
      else if (mode.isCurrentMode("GrimReduce")) {
         if (mc.player != null && this.jumpReset.getCurrentValue() && mc.player.onGround() && this.jumpResetTicks == 1) {
            event.setJump(true);
            this.jumpResetTicks = 0;
            log("Jump reset activated");
         }
      }
      else if (mode.isCurrentMode("GrimNoXZ")) {
         if (noxzmode.isCurrentMode("old")) {
            if (mc.player != null && this.grimNoXZ_old_jumpReset.getCurrentValue() && mc.player.onGround() && this.grimNoXZ_jumpResetTicks == 1) {
               event.setJump(true);
               this.grimNoXZ_jumpResetTicks = 0;
               log("Jump reset activated");
            }
         }
         if (this.grimNoXZ_noTargetJumpReset.getCurrentValue()) {
            if (this.noTargetJumpResetTimer == 1 && mc.player.onGround()) {
               event.setJump(true);
            }
         }
      }
   }

   @EventTarget
   public void onTick(EventRunTicks eventRunTicks) {
      if (mode.isCurrentMode("GrimNoXZ") && noxzmode.isCurrentMode("best") && this.noxzBest_targetEntity != null && this.noxzBest_attackQueue > 0) {
         if (!this.isValidPlayerTarget(this.noxzBest_targetEntity)) {
            log("NoXZ cancelled: Target is not a valid player - " + (this.noxzBest_targetEntity != null ? this.noxzBest_targetEntity.getDisplayName().getString() : "null"));
            this.noxzBest_attackQueue = 0;
            this.noxzBest_targetEntity = null;
            if (this.noxzBest_blinkEnabled.getCurrentValue()) {
               this.endInternalBlinkAfterDelay();
            }
            return;
         }

         if (this.noxzBest_mode.isCurrentMode("OneTime")) {
            while (this.noxzBest_attackQueue >= 1) {
               if (this.noxzBest_blinkEnabled.getCurrentValue()) {
                  this.startInternalBlink();
               }
               if (mc.getConnection() != null) {
                  mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(this.noxzBest_targetEntity, false));
               }
               if (mc.player != null) {
                  mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(0.6, 1.0, 0.6));
                  mc.player.setSprinting(false);
                  mc.player.swing(InteractionHand.MAIN_HAND);
               }
               this.noxzBest_attackQueue--;
            }
            if (this.noxzBest_blinkEnabled.getCurrentValue()) {
               this.endInternalBlinkAfterDelay();
            }
            log("NoXZ OneTime attacks executed");
         } else if (this.noxzBest_mode.isCurrentMode("PerTick")) {
            if (this.noxzBest_attackQueue >= 1) {
               if (this.noxzBest_blinkEnabled.getCurrentValue()) {
                  this.startInternalBlink();
               }
               if (mc.getConnection() != null) {
                  mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(this.noxzBest_targetEntity, false));
               }
               if (mc.player != null) {
                  mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(0.6, 1.0, 0.6));
                  mc.player.setSprinting(false);
                  mc.player.swing(InteractionHand.MAIN_HAND);
               }
               log("NoXZ PerTick attack executed, remaining: " + (this.noxzBest_attackQueue - 1));
            }
            this.noxzBest_attackQueue--;
            if (this.noxzBest_attackQueue <= 0 && this.noxzBest_blinkEnabled.getCurrentValue()) {
               this.endInternalBlinkAfterDelay();
            }
         }
      }

      if (mode.isCurrentMode("Web")) {
         if (web_placeTick > 0) {
            web_placeTick--;

            if (web_placeTick == 1) {
               float yaw = mc.player.getYRot();
               float pitch = 89.79F;
               mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround()));
               RotationManager.setRotations(new Rotation(yaw, pitch).toVec2f());

               BlockHitResult placeResult = (BlockHitResult) PlayerUtils.pickCustom(4.0F, yaw, pitch);
               if (placeResult != null && placeResult.getType() == HitResult.Type.BLOCK) {
                  mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, placeResult);
                  mc.player.swing(InteractionHand.MAIN_HAND);
                  log("成功放置蜘蛛网。");
               } else {
                  log("无法找到可以放置蜘蛛网的方块。");
               }
            }

            if (web_placeTick == 0) {
               if (this.web_originalSlot != -1) {
                  mc.player.getInventory().selected = this.web_originalSlot;
                  this.web_originalSlot = -1;
                  log("已切换回原物品槽。");
               }
            }
         }
      }
      if (mode.isCurrentMode("GrimFull")) {
         if (mc.player != null && mc.getConnection() != null && mc.gameMode != null && eventRunTicks.getType() != EventType.POST) {
            if (!Naven.getInstance().getModuleManager().getModule(LongJump.class).isEnabled()) {
               if (mc.player.isDeadOrDying() || !mc.player.isAlive() || mc.player.getHealth() <= 0.0F || mc.screen instanceof ProgressScreen || mc.screen instanceof DeathScreen) {
                  this.reset();
               }
               if (debugTick > 0) {
                  debugTick--;
                  if (debugTick == 0) {
                     this.processPackets();
                     stage = Velocity.Stage.IDLE;
                  }
               } else {
                  stage = Velocity.Stage.IDLE;
               }
               if (grimTick > 0) {
                  log("GrimTick: " + grimTick);
                  grimTick--;
               }
               float yaw = RotationManager.rotations.getX();
               float pitch = 89.79F;
               BlockHitResult blockRayTraceResult = (BlockHitResult) PlayerUtils.pickCustom(3.7F, yaw, pitch);
               if (stage == Velocity.Stage.TRANSACTION && grimTick == 0 && blockRayTraceResult != null && !BlockUtils.isAirBlock(blockRayTraceResult.getBlockPos()) && mc.player.getBoundingBox().intersects(new AABB(blockRayTraceResult.getBlockPos().above()))) {
                  Block targetBlock = mc.level.getBlockState(blockRayTraceResult.getBlockPos()).getBlock();
                  if (shouldAvoidInteraction(targetBlock)) {
                     return;
                  }
                  this.result = new BlockHitResult(blockRayTraceResult.getLocation(), blockRayTraceResult.getDirection(), blockRayTraceResult.getBlockPos(), false);
                  ((LocalPlayerAccessor) mc.player).setYRotLast(yaw);
                  ((LocalPlayerAccessor) mc.player).setXRotLast(pitch);
                  RotationManager.setRotations(new Rotation(yaw, pitch).toVec2f());
                  Aura aura = (Aura) Naven.getInstance().getModuleManager().getModule(Aura.class);
                  if (aura != null && aura.rotation != null) {
                     aura.rotation = new Rotation(yaw, pitch).toVec2f();
                  }
                  this.processPackets();
                  mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround()));
                  mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, this.result);
                  Naven.skipTasks.add(() -> {});
                  for (int i = 2; i <= 8; i++) {
                     Naven.skipTasks.add(() -> {
                        EventMotion event1 = new EventMotion(EventType.PRE, mc.player.position().x, mc.player.position().y, mc.player.position().z, yaw, pitch, mc.player.onGround());
                        Naven.getInstance().getRotationManager().onPre(event1);
                        if (event1.getYaw() != yaw || event1.getPitch() != pitch) {
                           mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(event1.getYaw(), event1.getPitch(), mc.player.onGround()));
                        }
                     });
                  }
                  log("GrimFull success");
                  debugTick = 20;
                  stage = Velocity.Stage.BLOCK;
                  grimTick = 0;
               }
            }
         }
      }
   }

   public void processPackets() {
      ClientPacketListener connection = mc.getConnection();
      if (connection == null) {
         this.inBound.clear();
      } else {
         Packet<ClientGamePacketListener> packet;
         while ((packet = this.inBound.poll()) != null) {
            try {
               packet.handle(connection);
            } catch (Exception var4) {
               var4.printStackTrace();
               this.inBound.clear();
               break;
            }
         }
      }
   }

   private boolean shouldAvoidInteraction(Block block) {
      return block instanceof ChestBlock
              || block instanceof CraftingTableBlock
              || block instanceof FurnaceBlock
              || block instanceof EnderChestBlock
              || block instanceof BarrelBlock
              || block instanceof ShulkerBoxBlock
              || block instanceof AnvilBlock
              || block instanceof EnchantmentTableBlock
              || block instanceof BrewingStandBlock
              || block instanceof BeaconBlock
              || block instanceof HopperBlock
              || block instanceof DispenserBlock
              || block instanceof DropperBlock
              || block instanceof LecternBlock
              || block instanceof CartographyTableBlock
              || block instanceof FletchingTableBlock
              || block instanceof SmithingTableBlock
              || block instanceof StonecutterBlock
              || block instanceof LoomBlock
              || block instanceof GrindstoneBlock
              || block instanceof ComposterBlock
              || block instanceof CauldronBlock
              || block instanceof BedBlock
              || block instanceof DoorBlock
              || block instanceof TrapDoorBlock
              || block instanceof FenceGateBlock
              || block instanceof ButtonBlock
              || block instanceof LeverBlock
              || block instanceof NoteBlock;
   }

   private void placeWater() {
      float yaw = mc.player.getYRot();
      float pitch = 89.79F;
      mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround()));
      RotationManager.setRotations(new Rotation(yaw, pitch).toVec2f());
      Aura aura = (Aura) Naven.getInstance().getModuleManager().getModule(Aura.class);
      if (aura != null && aura.rotation != null) {
         aura.rotation = new Rotation(yaw, pitch).toVec2f();
      }
      BlockHitResult placeResult = (BlockHitResult) PlayerUtils.pickCustom(4.0F, yaw, pitch);
      if (placeResult != null && placeResult.getType() == HitResult.Type.BLOCK && placeResult.getDirection() == Direction.UP) {
         this.waterPos = placeResult.getBlockPos().above();
         this.useItem(mc.player, mc.level, InteractionHand.OFF_HAND);
         log("放水成功，已记录位置.");
      } else {
         log("无法找到放置水的方块。");
      }
   }

   private void pickupWater() {
      float yaw = mc.player.getYRot();
      float pitch = 89.79F;
      mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround()));
      RotationManager.setRotations(new Rotation(yaw, pitch).toVec2f());
      Aura aura = (Aura) Naven.getInstance().getModuleManager().getModule(Aura.class);
      if (aura != null && aura.rotation != null) {
         aura.rotation = new Rotation(yaw, pitch).toVec2f();
      }
      this.useItem(mc.player, mc.level, InteractionHand.OFF_HAND);
      log("成功回收水桶.");
      this.waterPos = null;
   }

   private boolean shouldStopDelay() {
      if (mc.player == null) return true;
      if (onGroundRelease.getCurrentValue() && mc.player.onGround()) {
         log("Stopping delay due to OnGroundRelease.");
         return true;
      }
      if (DelayManager.getInstance().getDelayTicks() >= this.delay_ticks.getCurrentValue()) {
         log("Stopping delay due to timeout...");
         return true;
      }
      return false;
   }

   private boolean isPlayerInCobweb() {
      if (mc.player == null || mc.level == null) {
         return false;
      }
      BlockPos posFeet = mc.player.blockPosition();
      return mc.level.getBlockState(posFeet).is(Blocks.COBWEB) || mc.level.getBlockState(posFeet.above()).is(Blocks.COBWEB);
   }

   private void stopDelayAndResetFlag(String reason) {
      DelayManager.getInstance().stopDelay(false, DelayManager.DelayModules.VELOCITY);
      this.delay_reverseFlag = false;
      log("Delay stopped (" + reason + "). Packets processed.");
   }

   public InteractionResult useItem(Player pPlayer, Level pLevel, InteractionHand pHand) {
      MultiPlayerGameModeAccessor gameMode = (MultiPlayerGameModeAccessor)mc.gameMode;
      if (gameMode.getLocalPlayerMode() == GameType.SPECTATOR) {
         return InteractionResult.PASS;
      } else {
         gameMode.invokeEnsureHasSentCarriedItem();
         PacketUtils.sendSequencedPacket(id -> new ServerboundUseItemPacket(pHand, id));
         ItemStack itemstack = pPlayer.getItemInHand(pHand);
         if (pPlayer.getCooldowns().isOnCooldown(itemstack.getItem())) {
            return InteractionResult.PASS;
         } else {
            InteractionResult cancelResult = ForgeHooks.onItemRightClick(pPlayer, pHand);
            if (cancelResult != null) {
               return cancelResult;
            } else {
               InteractionResultHolder<ItemStack> interactionresultholder = itemstack.use(pLevel, pPlayer, pHand);
               ItemStack itemstack1 = (ItemStack)interactionresultholder.getObject();
               if (itemstack1 != itemstack) {
                  pPlayer.setItemInHand(pHand, itemstack1);
                  if (itemstack1.isEmpty()) {
                     ForgeEventFactory.onPlayerDestroyItem(pPlayer, itemstack, pHand);
                  }
               }
               return interactionresultholder.getResult();
            }
         }
      }
   }

   private void log(String message) {
      if (this.log.getCurrentValue()) {
         ChatUtils.addChatMessage("[Velocity] " + message);
      }
   }

   private boolean isValidPlayerTarget(Entity entity) {
      String entityName;
      if (entity == null) {
         return false;
      }
      if (!(entity instanceof Player)) {
         return false;
      }
      if (this.log.getCurrentValue()) {
         entityName = entity.getDisplayName().getString();
         if (entityName.contains("水晶") || entityName.contains("Crystal") || entityName.contains("NPC") || entityName.contains("npc")) {
            ChatUtils.addChatMessage("Skipping non-player entity: " + entityName);
            return false;
         }
      }
      return true;
   }

   private void startInternalBlink() {
      if (!this.isBlinking) {
         this.isBlinking = true;
         this.blinkPackets.clear();
         if (this.blinkEndTask != null && !this.blinkEndTask.isDone()) {
            this.blinkEndTask.cancel(false);
         }
         log("Internal Blink started.");
      }
   }

   private void endInternalBlinkAfterDelay() {
      if (this.isBlinking) {
         if (this.blinkEndTask != null && !this.blinkEndTask.isDone()) {
            this.blinkEndTask.cancel(false);
         }
         long blinkMs = Math.round(this.noxzBest_blinkDuration.getCurrentValue());
         this.blinkEndTask = this.scheduler.schedule(() -> mc.execute(() -> {
            if (this.isBlinking) {
               this.isBlinking = false;
               while (!this.blinkPackets.isEmpty()) {
                  Packet<?> p = this.blinkPackets.poll();
                  if (p == null || mc.getConnection() == null) continue;
                  mc.getConnection().send((Packet<?>) p);
                  log("Blink: Sent a cached move packet.");
               }
               log("Internal Blink ended after " + blinkMs + "ms.");
            }
         }), blinkMs, TimeUnit.MILLISECONDS);
      }
   }

   private Entity findGrimNoXZTargetInternal() {
      if (mc.level == null || mc.player == null) return null;
      Vec3 playerPos = mc.player.position();
      Vec3 lookVec = mc.player.getLookAngle();
      double range = 3.5;
      float fov = 180.0f;

      double halfFovRad = Math.toRadians((double)fov / 2.0);
      AABB searchArea = mc.player.getBoundingBox().inflate(range);
      List<Entity> entities = mc.level.getEntitiesOfClass(Entity.class, searchArea);

      Entity bestTarget = null;
      double closestDist = Double.MAX_VALUE;

      for (Entity entity : entities) {
         if (entity == mc.player || !entity.isAlive() || !this.isValidPlayerTarget(entity)) continue;

         double dist = mc.player.distanceTo(entity);
         if (dist > range) continue;

         Vec3 toEntity = entity.position().subtract(playerPos).normalize();
         Vec3 horizontalLook = new Vec3(lookVec.x, 0.0, lookVec.z).normalize();
         Vec3 horizontalToEntity = new Vec3(toEntity.x, 0.0, toEntity.z).normalize();

         double angleRad = Math.acos(Mth.clamp(horizontalLook.dot(horizontalToEntity), -1.0, 1.0));

         if (angleRad <= halfFovRad && dist < closestDist) {
            closestDist = dist;
            bestTarget = entity;
         }
      }
      return bestTarget;
   }

   public static enum Stage {
      TRANSACTION,
      ROTATION,
      BLOCK,
      IDLE
   }
}