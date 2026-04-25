package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.IRCModule.IrcClientManager;
import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.*;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.KillSay;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.Teams;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.Blink;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.Stuck;
import com.heypixel.heypixelmod.obsoverlay.ui.hudeditor.HUDEditor;
import com.heypixel.heypixelmod.obsoverlay.utils.*;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationManager;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@ModuleInfo(
        name = "KillAura",
        description = "Automatically attacks entities",
        category = Category.COMBAT
)
public class Aura extends Module {
   public static Entity target;
   public static Entity aimingTarget;
   public static List<Entity> targets = new ArrayList<>();
   public static Vector2f rotation;
   private LivingEntity lastRenderedTarget = null;

   public BooleanValue targetEsp = ValueBuilder.create(this, "Target ESP").setDefaultBooleanValue(true).build().getBooleanValue();
   BooleanValue targetHud = ValueBuilder.create(this, "Target HUD").setDefaultBooleanValue(true).build().getBooleanValue();
   public ModeValue TargetHUDStyle = ValueBuilder.create(this, "TargetHUD Style").setVisibility(this.targetHud::getCurrentValue).setDefaultModeIndex(0).setModes("Naven", "New", "MoonLightV2", "Rise","Exhibition","Xinxin").build().getModeValue();
   public ModeValue TargetESPStyle = ValueBuilder.create(this, "TargetEsp Style").setVisibility(this.targetEsp::getCurrentValue).setDefaultModeIndex(0).setModes("Naven", "Nitro", "RainbowNitro").build().getModeValue();

   public BooleanValue shouldAutoBlock = ValueBuilder.create(this, "FakeAutoBlock").setDefaultBooleanValue(true).build().getBooleanValue();
   BooleanValue attackPlayer = ValueBuilder.create(this, "Attack Player").setDefaultBooleanValue(true).build().getBooleanValue();
   BooleanValue attackInvisible = ValueBuilder.create(this, "Attack Invisible").setDefaultBooleanValue(false).build().getBooleanValue();
   BooleanValue attackAnimals = ValueBuilder.create(this, "Attack Animals").setDefaultBooleanValue(false).build().getBooleanValue();
   BooleanValue attackMobs = ValueBuilder.create(this, "Attack Mobs").setDefaultBooleanValue(false).build().getBooleanValue();
   BooleanValue multi = ValueBuilder.create(this, "Multi Attack").setDefaultBooleanValue(false).build().getBooleanValue();
   BooleanValue infSwitch = ValueBuilder.create(this, "Infinity Switch").setDefaultBooleanValue(false).build().getBooleanValue();
   BooleanValue preferBaby = ValueBuilder.create(this, "Prefer Baby").setDefaultBooleanValue(false).build().getBooleanValue();
   BooleanValue moreParticles = ValueBuilder.create(this, "More Particles").setDefaultBooleanValue(false).build().getBooleanValue();

   public BooleanValue aimRangeRender = ValueBuilder.create(this, "AimRangeRender").setDefaultBooleanValue(true).build().getBooleanValue();
   FloatValue rangeLineR = ValueBuilder.create(this, "RangeLineR")
           .setDefaultFloatValue(255.0F)
           .setFloatStep(1.0F)
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(255.0F)
           .setVisibility(() -> this.aimRangeRender.getCurrentValue())
           .build()
           .getFloatValue();
   FloatValue rangeLineG = ValueBuilder.create(this, "RangeLineG")
           .setDefaultFloatValue(255.0F)
           .setFloatStep(1.0F)
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(255.0F)
           .setVisibility(() -> this.aimRangeRender.getCurrentValue())
           .build()
           .getFloatValue();
   FloatValue rangeLineB = ValueBuilder.create(this, "RangeLineB")
           .setDefaultFloatValue(255.0F)
           .setFloatStep(1.0F)
           .setMinFloatValue(0.0F)
           .setMaxFloatValue(255.0F)
           .setVisibility(() -> this.aimRangeRender.getCurrentValue())
           .build()
           .getFloatValue();

   FloatValue aimRange = ValueBuilder.create(this, "Aim Range").setDefaultFloatValue(5.0F).setFloatStep(0.1F).setMinFloatValue(1.0F).setMaxFloatValue(6.0F).build().getFloatValue();
   FloatValue aps = ValueBuilder.create(this, "Attack Per Second").setDefaultFloatValue(10.0F).setFloatStep(1.0F).setMinFloatValue(1.0F).setMaxFloatValue(20.0F).build().getFloatValue();
   FloatValue switchSize = ValueBuilder.create(this, "Switch Size").setDefaultFloatValue(1.0F).setFloatStep(1.0F).setMinFloatValue(1.0F).setMaxFloatValue(5.0F).setVisibility(() -> !this.infSwitch.getCurrentValue()).build().getFloatValue();
   FloatValue switchAttackTimes = ValueBuilder.create(this, "Switch Delay (Attack Times)").setDefaultFloatValue(1.0F).setFloatStep(1.0F).setMinFloatValue(1.0F).setMaxFloatValue(10.0F).build().getFloatValue();
   FloatValue fov = ValueBuilder.create(this, "FoV").setDefaultFloatValue(360.0F).setFloatStep(1.0F).setMinFloatValue(10.0F).setMaxFloatValue(360.0F).build().getFloatValue();
   FloatValue hurtTime = ValueBuilder.create(this, "Hurt Time").setDefaultFloatValue(10.0F).setFloatStep(1.0F).setMinFloatValue(0.0F).setMaxFloatValue(10.0F).build().getFloatValue();
   ModeValue priority = ValueBuilder.create(this, "Priority").setModes("Health", "FoV", "Range", "None").build().getModeValue();

   RotationUtils.Data lastRotationData;
   RotationUtils.Data rotationData;
   int attackTimes = 0;
   float attacks = 0.0F;
   private int index;
   private Vector4f blurMatrix;

   public boolean shouldAutoBlock() {
      return this.isEnabled() && this.shouldAutoBlock.getCurrentValue() && Aura.target != null;
   }

   @EventTarget
   public void onShader(EventShader e) {
      if (this.blurMatrix != null && this.targetHud.getCurrentValue()) {
         RenderUtils.drawRoundedRect(e.getStack(), this.blurMatrix.x(), this.blurMatrix.y(), this.blurMatrix.z(), this.blurMatrix.w(), 3.0F, 1073741824);
      }
   }

   @EventTarget
   public void onRender2D(EventRender2D e) {
      if (!this.targetHud.getCurrentValue()) {
         this.blurMatrix = null;
         this.lastRenderedTarget = null;
         return;
      }

      LivingEntity currentTarget = (target instanceof LivingEntity) ? (LivingEntity) target : null;
      String style = this.TargetHUDStyle.getCurrentMode();

      LivingEntity entityToRender = null;
      boolean shouldCallRender = false;

      if (currentTarget != null) {
         shouldCallRender = true;
         entityToRender = currentTarget;
         this.lastRenderedTarget = currentTarget;
      } else {
         if (com.heypixel.heypixelmod.obsoverlay.ui.targethud.TargetHUD.isStyleAnimated(style) && this.lastRenderedTarget != null) {
            shouldCallRender = true;
            entityToRender = null;
         } else {
            shouldCallRender = false;
         }
      }

      if (shouldCallRender) {
         HUDEditor.HUDElement targetHudElement = HUDEditor.getInstance().getHUDElement("targethud");
         if (targetHudElement == null) {
            return;
         }

         e.getStack().pushPose();

         float x = (float) targetHudElement.x;
         float y = (float) targetHudElement.y;

         this.blurMatrix = com.heypixel.heypixelmod.obsoverlay.ui.targethud.TargetHUD.render(e.getGuiGraphics(), entityToRender, style, x, y);

         if (this.blurMatrix != null) {
            targetHudElement.width = this.blurMatrix.z();
            targetHudElement.height = this.blurMatrix.w();
         } else {
            targetHudElement.width = 0;
            targetHudElement.height = 0;
            this.lastRenderedTarget = null;
         }

         e.getStack().popPose();
      } else {
         this.blurMatrix = null;
         this.lastRenderedTarget = null;

         HUDEditor.HUDElement targetHudElement = HUDEditor.getInstance().getHUDElement("targethud");
         if (targetHudElement != null) {
            targetHudElement.width = 0;
            targetHudElement.height = 0;
         }
      }
   }

   @Override
   public void onEnable() {
      rotation = null;
      this.index = 0;
      target = null;
      aimingTarget = null;
      targets.clear();
   }

   @Override
   public void onDisable() {
      target = null;
      aimingTarget = null;
      super.onDisable();
   }

   @EventTarget
   public void onRespawn(EventRespawn e) {
      target = null;
      aimingTarget = null;
      this.toggle();
   }

   @EventTarget
   public void onAttackSlowdown(EventAttackSlowdown e) {
      e.setCancelled(true);
   }

   @EventTarget
   public void onRender(EventRender e) {
      if (this.targetEsp.getCurrentValue()) {
         com.heypixel.heypixelmod.obsoverlay.ui.targethud.TargetESP.render(e, targets, target, this.TargetESPStyle.getCurrentMode());
      }

      if (this.aimRangeRender.getCurrentValue()) {
         PoseStack stack = e.getPMatrixStack();
         stack.pushPose();

         GL11.glEnable(GL11.GL_BLEND);
         GL11.glBlendFunc(770, 771);
         GL11.glEnable(GL11.GL_LINE_SMOOTH);
         GL11.glDisable(GL11.GL_TEXTURE_2D);
         GL11.glDisable(GL11.GL_DEPTH_TEST);
         GL11.glDepthMask(false);

         Tesselator tesselator = Tesselator.getInstance();
         BufferBuilder bufferBuilder = tesselator.getBuilder();
         Matrix4f matrix = stack.last().pose();

         RenderSystem.setShader(GameRenderer::getPositionColorShader);

         double playerX = mc.player.xOld + (mc.player.getX() - mc.player.xOld) * e.getRenderPartialTicks();
         double playerY = mc.player.yOld + (mc.player.getY() - mc.player.yOld) * e.getRenderPartialTicks();
         double playerZ = mc.player.zOld + (mc.player.getZ() - mc.player.zOld) * e.getRenderPartialTicks();

         Vec3 camPos = RenderUtils.getCameraPos();
         float range = this.aimRange.getCurrentValue();

         int r = (int) this.rangeLineR.getCurrentValue();
         int g = (int) this.rangeLineG.getCurrentValue();
         int b = (int) this.rangeLineB.getCurrentValue();

         bufferBuilder.begin(Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);

         for (int i = 0; i <= 360; i++) {
            double rad = Math.toRadians(i);
            double x = playerX + Math.cos(rad) * range;
            double z = playerZ + Math.sin(rad) * range;

            bufferBuilder.vertex(matrix, (float)(x - camPos.x), (float)(playerY - camPos.y), (float)(z - camPos.z))
                    .color(r, g, b, 200)
                    .endVertex();
         }

         tesselator.end();

         GL11.glDepthMask(true);
         GL11.glEnable(GL11.GL_DEPTH_TEST);
         GL11.glEnable(GL11.GL_TEXTURE_2D);
         GL11.glDisable(GL11.GL_LINE_SMOOTH);
         GL11.glDisable(GL11.GL_BLEND);

         stack.popPose();
      }
   }

   @EventTarget
   public void onMotion(EventRunTicks event) {
      if (event.getType() == EventType.PRE && mc.player != null) {
         if (mc.screen instanceof AbstractContainerScreen || Naven.getInstance().getModuleManager().getModule(Stuck.class).isEnabled() || InventoryUtils.shouldDisableFeatures()) {
            target = null;
            aimingTarget = null;
            this.rotationData = null;
            rotation = null;
            this.lastRotationData = null;
            targets.clear();
            return;
         }

         boolean isSwitch = this.switchSize.getCurrentValue() > 1.0F;
         this.setSuffix(this.multi.getCurrentValue() ? "Multi" : (isSwitch ? "Switch" : "Single"));
         this.updateAttackTargets();
         aimingTarget = this.shouldPreAim();
         this.lastRotationData = this.rotationData;
         this.rotationData = null;
         if (aimingTarget != null) {
            this.rotationData = RotationUtils.getRotationDataToEntity(aimingTarget);
            if (this.rotationData.getRotation() != null) {
               rotation = this.rotationData.getRotation();
            } else {
               rotation = null;
            }
         }

         if (targets.isEmpty()) {
            target = null;
            return;
         }

         if (this.index > targets.size() - 1) {
            this.index = 0;
         }

         if (targets.size() > 1 && ((float)this.attackTimes >= this.switchAttackTimes.getCurrentValue() || this.rotationData != null && this.rotationData.getDistance() > 3.0)) {
            this.attackTimes = 0;
            for (int i = 0; i < targets.size(); i++) {
               this.index++;
               if (this.index > targets.size() - 1) this.index = 0;
               Entity nextTarget = targets.get(this.index);
               RotationUtils.Data data = RotationUtils.getRotationDataToEntity(nextTarget);
               if (data.getDistance() < 3.0) break;
            }
         }

         if (this.index > targets.size() - 1 || !isSwitch) {
            this.index = 0;
         }

         target = targets.get(this.index);
         this.attacks = this.attacks + this.aps.getCurrentValue() / 20.0F;
      }
   }

   @EventTarget
   public void onClick(EventClick e) {
      if (mc.player.getUseItem().isEmpty() && mc.screen == null && Naven.skipTasks.isEmpty() && !NetworkUtils.isServerLag() && !Naven.getInstance().getModuleManager().getModule(Blink.class).isEnabled()) {
         while (this.attacks >= 1.0F) {
            this.doAttack();
            this.attacks--;
         }
      }
   }

   public static Entity getTarget() {
      return target;
   }

   public Entity shouldPreAim() {
      Entity target = Aura.target;
      if (target == null) {
         List<Entity> aimTargets = this.getTargets();
         if (!aimTargets.isEmpty()) {
            target = aimTargets.get(0);
         }
      }
      return target;
   }

   public void doAttack() {
      if (!targets.isEmpty()) {
         HitResult hitResult = mc.hitResult;
         if (hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult result = (EntityHitResult)hitResult;
            if (AntiBots.isBot(result.getEntity())) {
               ChatUtils.addChatMessage("Attacking Bot!");
               return;
            }
         }

         if (this.multi.getCurrentValue()) {
            int attacked = 0;
            for (Entity entity : targets) {
               if (RotationUtils.getDistance(entity, mc.player.getEyePosition(), RotationManager.rotations) < 3.0) {
                  this.attackEntity(entity);
                  if (++attacked >= 2) break;
               }
            }
         } else if (hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult result = (EntityHitResult)hitResult;
            this.attackEntity(result.getEntity());
         }
      }
   }

   public void updateAttackTargets() {
      targets = this.getTargets();
   }

   public boolean isValidTarget(Entity entity) {
      if (entity instanceof Player) {
         IrcClientManager.IrcUserInfo info = IrcClientManager.INSTANCE.getIrcUserInfo(entity.getName().getString());
         if (info != null && "Dev".equals(info.rank())) {
            return false;
         }
      }

      if (entity == mc.player) return false;
      if (entity instanceof LivingEntity living) {
         if (living instanceof BlinkingPlayer) return false;
         AntiBots module = (AntiBots)Naven.getInstance().getModuleManager().getModule(AntiBots.class);
         if (module == null || !module.isEnabled() || !AntiBots.isBot(entity) && !AntiBots.isBedWarsBot(entity)) {
            if (Teams.isSameTeam(living)) return false;
            if (FriendManager.isFriend(living)) return false;
            if (living.isDeadOrDying() || living.getHealth() <= 0.0F) return false;
            if (entity instanceof ArmorStand) return false;
            if (entity.isInvisible() && !this.attackInvisible.getCurrentValue()) return false;
            if (entity instanceof Player && !this.attackPlayer.getCurrentValue()) return false;
            if (entity instanceof Player && (entity.getBbWidth() < 0.5 || living.isSleeping())) return false;
            if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat || entity instanceof AbstractGolem) && !this.attackMobs.getCurrentValue()) return false;
            if ((entity instanceof Animal || entity instanceof Squid) && !this.attackAnimals.getCurrentValue()) return false;
            return !(entity instanceof Villager) || this.attackAnimals.getCurrentValue();
         }
         return false;
      }
      return false;
   }

   public boolean isValidAttack(Entity entity) {
      if (!this.isValidTarget(entity)) return false;
      if (entity instanceof LivingEntity && (float)((LivingEntity)entity).hurtTime > this.hurtTime.getCurrentValue()) return false;
      Vec3 closestPoint = RotationUtils.getClosestPoint(mc.player.getEyePosition(), entity.getBoundingBox());
      if (closestPoint.distanceTo(mc.player.getEyePosition()) > (double)this.aimRange.getCurrentValue()) return false;
      return RotationUtils.inFoV(entity, this.fov.getCurrentValue() / 2.0F);
   }

   public void attackEntity(Entity entity) {
      this.attackTimes++;
      float currentYaw = mc.player.getYRot();
      float currentPitch = mc.player.getXRot();
      mc.player.setYRot(RotationManager.rotations.x);
      mc.player.setXRot(RotationManager.rotations.y);
      if (entity instanceof Player && !AntiBots.isBot(entity)) {
         KillSay.attackedPlayers.add(entity.getName().getString());
      }
      mc.gameMode.attack(mc.player, entity);
      mc.player.swing(InteractionHand.MAIN_HAND);
      if (this.moreParticles.getCurrentValue()) {
         mc.player.magicCrit(entity);
         mc.player.crit(entity);
      }
      mc.player.setYRot(currentYaw);
      mc.player.setXRot(currentPitch);
   }

   private List<Entity> getTargets() {
      Stream<Entity> stream = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), true)
              .filter(entity -> entity instanceof Entity)
              .filter(this::isValidAttack);
      List<Entity> possibleTargets = stream.collect(Collectors.toList());
      if (this.priority.isCurrentMode("Range")) {
         possibleTargets.sort(Comparator.comparingDouble(o -> o.distanceTo(mc.player)));
      } else if (this.priority.isCurrentMode("FoV")) {
         possibleTargets.sort(Comparator.comparingDouble(o -> RotationUtils.getDistanceBetweenAngles(RotationManager.rotations.x, RotationUtils.getRotations(o).x)));
      } else if (this.priority.isCurrentMode("Health")) {
         possibleTargets.sort(Comparator.comparingDouble(o -> o instanceof LivingEntity living ? living.getHealth() : 0.0));
      }
      if (this.preferBaby.getCurrentValue() && possibleTargets.stream().anyMatch(entity -> entity instanceof LivingEntity && ((LivingEntity)entity).isBaby())) {
         possibleTargets.removeIf(entity -> !(entity instanceof LivingEntity) || !((LivingEntity)entity).isBaby());
      }
      possibleTargets.sort(Comparator.comparing(o -> o instanceof EndCrystal ? 0 : 1));
      return this.infSwitch.getCurrentValue() ? possibleTargets : possibleTargets.subList(0, (int)Math.min(possibleTargets.size(), this.switchSize.getCurrentValue()));
   }
}