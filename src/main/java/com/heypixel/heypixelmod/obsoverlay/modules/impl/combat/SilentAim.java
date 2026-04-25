package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.Teams;
import com.heypixel.heypixelmod.obsoverlay.utils.BlinkingPlayer;
import com.heypixel.heypixelmod.obsoverlay.utils.FriendManager;
import com.heypixel.heypixelmod.obsoverlay.utils.Vector2f;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationManager;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


@ModuleInfo(
        name = "SilentAim",
        description = "Automatically aims at the nearest entity",
        category = Category.COMBAT
)
public class SilentAim extends Module {
   BooleanValue attackPlayer = ValueBuilder.create(this, "Attack Player").setDefaultBooleanValue(true).build().getBooleanValue();
   BooleanValue attackInvisible = ValueBuilder.create(this, "Attack Invisible").setDefaultBooleanValue(false).build().getBooleanValue();
   BooleanValue attackAnimals = ValueBuilder.create(this, "Attack Animals").setDefaultBooleanValue(false).build().getBooleanValue();
   BooleanValue attackMobs = ValueBuilder.create(this, "Attack Mobs").setDefaultBooleanValue(false).build().getBooleanValue();
   BooleanValue clickonly = ValueBuilder.create(this, "Click Only").setDefaultBooleanValue(true).build().getBooleanValue();
   FloatValue rotateSpeed = ValueBuilder.create(this, "Rotation Speed")
           .setDefaultFloatValue(20.0F)
           .setFloatStep(1.0F)
           .setMinFloatValue(1.0F)
           .setMaxFloatValue(90.0F)
           .build()
           .getFloatValue();
   FloatValue aimRange = ValueBuilder.create(this, "Aim Range")
           .setDefaultFloatValue(5.0F)
           .setFloatStep(0.1F)
           .setMinFloatValue(1.0F)
           .setMaxFloatValue(6.0F)
           .build()
           .getFloatValue();
   FloatValue fov = ValueBuilder.create(this, "FoV")
           .setDefaultFloatValue(360.0F)
           .setFloatStep(1.0F)
           .setMinFloatValue(10.0F)
           .setMaxFloatValue(360.0F)
           .build()
           .getFloatValue();
   FloatValue smoothingFactor = ValueBuilder.create(this, "Smoothing")
           .setDefaultFloatValue(0.3F)
           .setFloatStep(0.05F)
           .setMinFloatValue(0.1F)
           .setMaxFloatValue(1.0F)
           .build()
           .getFloatValue();
   FloatValue wobbleAmount = ValueBuilder.create(this, "Wobble Amount")
           .setDefaultFloatValue(1.5F)
           .setFloatStep(0.1F)
           .setMinFloatValue(0.5F)
           .setMaxFloatValue(3.0F)
           .build()
           .getFloatValue();

   FloatValue wobbleSpeed = ValueBuilder.create(this, "Wobble Speed")
           .setDefaultFloatValue(0.5F)
           .setFloatStep(0.1F)
           .setMinFloatValue(0.1F)
           .setMaxFloatValue(2.0F)
           .build()
           .getFloatValue();
   private final FloatValue resetTickDelay = ValueBuilder.create(this, "Reset Tick")
           .setDefaultFloatValue(5)
           .setFloatStep(1)
           .setMinFloatValue(0)
           .setMaxFloatValue(60)
           .build()
           .getFloatValue();

   BooleanValue smoothWobble = ValueBuilder.create(this, "Smooth Wobble")
           .setDefaultBooleanValue(true)
           .build()
           .getBooleanValue();

   ModeValue priority = ValueBuilder.create(this, "Priority").setModes("Health", "FoV", "Range", "None").build().getModeValue();
   private float wobbleTimer = 0;
   private long lastUpdateTime = System.currentTimeMillis();
   private Vector2f currentWobble = new Vector2f();
   private Vector2f targetWobble = new Vector2f();
   private int ticksSinceLastTarget = 0;
   private boolean isResetting = false;
   private final Vector2f targetRotation = new Vector2f();
   private boolean working = false;
   private Vector2f lastRotation = new Vector2f();
   private Entity currentTarget = null;
   private Vec3 targetDirection = Vec3.ZERO;

   public Vector2f getTargetRotation() {
      return new Vector2f(targetRotation.getX(), targetRotation.getY());
   }

   public boolean isWorking() {
      return working;
   }

   public Entity getCurrentTarget() {
      return currentTarget;
   }

   private Vector2f calculateWobble() {
      if (!smoothWobble.getCurrentValue() || wobbleAmount.getCurrentValue() <= 0) {
         return new Vector2f(0, 0);
      }

      long currentTime = System.currentTimeMillis();
      float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
      lastUpdateTime = currentTime;
      wobbleTimer += deltaTime * wobbleSpeed.getCurrentValue();

      if (wobbleTimer >= 1.0F || targetWobble.equals(currentWobble)) {
         wobbleTimer = 0;
         float amount = wobbleAmount.getCurrentValue();
         targetWobble.setX((float) (Math.random() * amount * 2 - amount));
         targetWobble.setY((float) (Math.random() * amount * 2 - amount));
      }

      currentWobble.setX(currentWobble.getX() + (targetWobble.getX() - currentWobble.getX()) * 0.1F);
      currentWobble.setY(currentWobble.getY() + (targetWobble.getY() - currentWobble.getY()) * 0.1F);

      return currentWobble;
   }

   @EventTarget
   public void onMotion(EventRunTicks e) {
      if (e.getType() == EventType.PRE && mc.player != null) {
         if (this.clickonly.currentValue && !mc.options.keyAttack.isDown()) {
            this.working = false;
            currentTarget = null;
            targetDirection = Vec3.ZERO;
            return;
         }

         Entity target = this.getTarget();
         currentTarget = target;

         if (target != null) {
            ticksSinceLastTarget = 0;
            isResetting = false;

            // 计算到目标的水平方向向量
            Vec3 playerPos = mc.player.position();
            Vec3 targetPos = target.position();
            targetDirection = new Vec3(
                    targetPos.x - playerPos.x,
                    0,
                    targetPos.z - playerPos.z
            ).normalize();

            Vector2f rotations = RotationUtils.getRotations(target);
            float targetYaw = rotations.getX();
            float targetPitch = rotations.getY();
            float smoothFactor = smoothingFactor.getCurrentValue();
            targetYaw = lastRotation.getX() + (targetYaw - lastRotation.getX()) * smoothFactor;
            targetPitch = lastRotation.getY() + (targetPitch - lastRotation.getY()) * smoothFactor;

            this.targetRotation.setX(targetYaw);
            this.targetRotation.setY(targetPitch);
            this.working = true;

            // 基于目标方向调整移动
            adjustMovementDirection();
         } else {
            this.working = false;
            targetDirection = Vec3.ZERO;
            if (resetTickDelay.getCurrentValue() > 0) {
               ticksSinceLastTarget++;
               if (ticksSinceLastTarget >= resetTickDelay.getCurrentValue()) {
                  isResetting = true;
                  // 平滑回正
                  float resetSpeed = 0.05f;
                  float currentYaw = mc.player.getYRot();
                  float currentPitch = mc.player.getXRot();
                  targetRotation.setX(
                          targetRotation.getX() + (currentYaw - targetRotation.getX()) * resetSpeed
                  );
                  targetRotation.setY(
                          targetRotation.getY() + (currentPitch - targetRotation.getY()) * resetSpeed
                  );
               }
            } else {
               targetRotation.setX(mc.player.getYRot());
               targetRotation.setY(mc.player.getXRot());
            }
         }

         lastRotation.setX(targetRotation.getX());
         lastRotation.setY(targetRotation.getY());
      }
   }

   // 修复移动方向的核心方法
   private void adjustMovementDirection() {
      if (!working || targetDirection.equals(Vec3.ZERO)) return;

      Input input = mc.player.input;
      if (input == null) return;

      // 获取原始输入状态
      float forward = input.up ? 1.0F : (input.down ? -1.0F : 0.0F);
      float strafe = input.left ? 1.0F : (input.right ? -1.0F : 0.0F);

      // 如果没有输入，不需要调整
      if (forward == 0 && strafe == 0) return;

      // 计算目标方向的角度（弧度）
      double targetAngle = Math.atan2(targetDirection.x, targetDirection.z);

      // 创建移动向量（基于目标方向）
      Vec3 moveVec = Vec3.ZERO;

      // 前后移动：基于目标方向
      if (forward != 0) {
         moveVec = moveVec.add(
                 Math.sin(targetAngle) * forward,
                 0,
                 Math.cos(targetAngle) * forward
         );
      }

      // 左右移动：基于目标方向的垂直方向
      if (strafe != 0) {
         // 计算垂直方向（逆时针旋转90度）
         Vec3 perp = new Vec3(-targetDirection.z, 0, targetDirection.x).normalize();
         moveVec = moveVec.add(perp.scale(strafe));
      }

      // 将世界坐标系的移动向量转换为玩家本地坐标系
      float playerYaw = mc.player.getYRot();
      double playerAngle = Math.toRadians(playerYaw);

      // 计算相对于玩家视角的移动分量
      double relativeX = moveVec.x * Math.cos(-playerAngle) - moveVec.z * Math.sin(-playerAngle);
      double relativeZ = moveVec.x * Math.sin(-playerAngle) + moveVec.z * Math.cos(-playerAngle);

      // 更新输入向量
      input.forwardImpulse = (float) relativeZ;
      input.leftImpulse = (float) -relativeX; // 注意符号
   }

   public boolean isValidTarget(Entity entity) {
      if (entity == mc.player) {
         return false;
      } else if (entity instanceof LivingEntity living) {
         if (living instanceof BlinkingPlayer) {
            return false;
         } else {
            AntiBots module = (AntiBots) Naven.getInstance().getModuleManager().getModule(AntiBots.class);
            if (module == null || !module.isEnabled() || !AntiBots.isBot(entity) && !AntiBots.isBedWarsBot(entity)) {
               if (Teams.isSameTeam(living)) {
                  return false;
               } else if (FriendManager.isFriend(living)) {
                  return false;
               } else if (living.isDeadOrDying() || living.getHealth() <= 0.0F) {
                  return false;
               } else if (entity instanceof ArmorStand) {
                  return false;
               } else if (entity.isInvisible() && !this.attackInvisible.getCurrentValue()) {
                  return false;
               } else if (entity instanceof Player && !this.attackPlayer.getCurrentValue()) {
                  return false;
               } else if (!(entity instanceof Player) || !((double) entity.getBbWidth() < 0.5) && !living.isSleeping()) {
                  if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat || entity instanceof AbstractGolem)
                          && !this.attackMobs.getCurrentValue()) {
                     return false;
                  } else if ((entity instanceof Animal || entity instanceof Squid) && !this.attackAnimals.getCurrentValue()) {
                     return false;
                  } else {
                     return entity instanceof Villager && !this.attackAnimals.getCurrentValue() ? false : !(entity instanceof Player) || !entity.isSpectator();
                  }
               } else {
                  return false;
               }
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   public boolean isValidAttack(Entity entity) {
      if (!this.isValidTarget(entity)) {
         return false;
      } else {
         Vec3 closestPoint = RotationUtils.getClosestPoint(mc.player.getEyePosition(), entity.getBoundingBox());
         if (closestPoint.distanceTo(mc.player.getEyePosition()) > (double) this.aimRange.getCurrentValue()) {
            return false;
         } else {
            return RotationUtils.inFoV(entity, this.fov.getCurrentValue() / 2.0F);
         }
      }
   }

   private Entity getTarget() {
      Stream<Entity> stream = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), true)
              .filter(this::isValidAttack);

      List<Entity> possibleTargets = stream.collect(Collectors.toList());

      // 添加AntiBot过滤 - 排除被标记为机器人的实体
      possibleTargets.removeIf(entity -> {
         AntiBots antiBots = (AntiBots) Naven.getInstance().getModuleManager().getModule(AntiBots.class);
         return antiBots != null && antiBots.isEnabled() &&
                 (AntiBots.isBot(entity) || AntiBots.isBedWarsBot(entity));
      });

      if (this.priority.isCurrentMode("Range")) {
         possibleTargets.sort(Comparator.comparingDouble(o -> o.distanceTo(mc.player)));
      } else if (this.priority.isCurrentMode("FoV")) {
         possibleTargets.sort(Comparator.comparingDouble(
                 o -> RotationUtils.getDistanceBetweenAngles(RotationManager.rotations.x, RotationUtils.getRotations(o).x)
         ));
      } else if (this.priority.isCurrentMode("Health")) {
         possibleTargets.sort(Comparator.comparingDouble(
                 o -> o instanceof LivingEntity living ? living.getHealth() : 0.0
         ));
      }

      return possibleTargets.isEmpty() ? null : possibleTargets.get(0);
   }
}