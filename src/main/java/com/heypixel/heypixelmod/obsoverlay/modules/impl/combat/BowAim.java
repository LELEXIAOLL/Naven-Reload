package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender2D;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.ProjectionUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.Vector2f;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@ModuleInfo(
        name = "BowAim",
        description = "Automatically aims when using a bow",
        category = Category.COMBAT
)
public class BowAim extends Module {

    private final BooleanValue silentValue = ValueBuilder.create(this, "Silent")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private final BooleanValue predictValue = ValueBuilder.create(this, "Predict")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private final BooleanValue throughWallsValue = ValueBuilder.create(this, "ThroughWalls")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private final BooleanValue aimPlayers = ValueBuilder.create(this, "Aim Players")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private final BooleanValue aimMobs = ValueBuilder.create(this, "Aim Mobs")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private final BooleanValue aimAnimals = ValueBuilder.create(this, "Aim Animals")
            .setDefaultBooleanValue(false)
            .build()
            .getBooleanValue();

    private final FloatValue predictSizeValue = ValueBuilder.create(this, "PredictSize")
            .setDefaultFloatValue(2.0F)
            .setMinFloatValue(0.1F)
            .setMaxFloatValue(5.0F)
            .setFloatStep(0.1F)
            .build()
            .getFloatValue();

    private final ModeValue priorityValue = ValueBuilder.create(this, "Priority")
            .setModes("Health", "Distance", "Direction")
            .setDefaultModeIndex(2) // 对应"Direction"
            .build()
            .getModeValue();

    private final BooleanValue markValue = ValueBuilder.create(this, "Mark")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private Entity target = null;

    @Override
    public void onDisable() {
        target = null;
    }

    @EventTarget
    public void onUpdate(com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks event) {
        target = null;

        if (mc.player != null && mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof BowItem) {
            Entity entity = getTarget(throughWallsValue.getCurrentValue(), priorityValue.getCurrentMode());
            if (entity == null) return;
            target = entity;
            aimAtEntityWithPrediction(entity);
        }
    }

    @EventTarget
    public void onRender3D(EventRender event) {
        if (target != null && !priorityValue.getCurrentMode().equals("Multi") && markValue.getCurrentValue()) {
            drawEntityBox(target, new Color(37, 126, 255, 70), event.getPMatrixStack());
        }
    }

    @EventTarget
    public void onRender2D(EventRender2D event) {
        if (target != null && markValue.getCurrentValue()) {
            renderTargetLabel(event.getStack(), target);
        }
    }

    private void renderTargetLabel(PoseStack stack, Entity entity) {

        Vec3 entityPos = entity.position();
        entityPos = entityPos.add(0, entity.getBoundingBox().getYsize() + 0.5, 0);
        Vector2f screenPos = ProjectionUtils.project(entityPos.x, entityPos.y, entityPos.z, 1.0F);
        if (screenPos.x != Float.MAX_VALUE && screenPos.y != Float.MAX_VALUE) {
            String text = "AimTarget";
            float x = screenPos.x - Fonts.harmony.getWidth(text, 0.5) / 2;
            float y = screenPos.y;

            Fonts.harmony.render(stack, text, x, y, Color.WHITE, true, 0.5);
        }
    }

    private void aimAtEntityWithPrediction(Entity entity) {
        if (mc.player == null) return;
        int useDuration = mc.player.getUseItemRemainingTicks();
        float velocity = getArrowVelocity(useDuration);

        if (velocity < 0.1F) {
            aimAtEntity(entity);
            return;
        }

        Vec3 playerPos = mc.player.position().add(0, mc.player.getEyeHeight(), 0);
        Vec3 targetPos = entity.position().add(0, entity.getBbHeight() * 0.5, 0);


        if (!predictValue.getCurrentValue()) {
            aimAtEntity(entity);
            return;
        }
        Vec3 targetMotion = new Vec3(
                entity.getX() - entity.xo,
                entity.getY() - entity.yo,
                entity.getZ() - entity.zo
        );


        float predictTime = predictSizeValue.getCurrentValue();
        Vec3 predictedPos = targetPos.add(targetMotion.scale(predictTime));
        double gravity = 0.05D;
        Vec3 finalPos = calculateArrowImpactPosition(playerPos, predictedPos, velocity, gravity);
        double d0 = finalPos.x - playerPos.x;
        double d1 = finalPos.y - playerPos.y;
        double d2 = finalPos.z - playerPos.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        float rotationYaw = (float) (Math.atan2(d2, d0) * 180.0D / Math.PI) - 90.0F;
        float rotationPitch = (float) -(Math.atan2(d1, d3) * 180.0D / Math.PI);

        mc.player.setYRot(rotationYaw);
        mc.player.setXRot(rotationPitch);
    }

    private Vec3 calculateArrowImpactPosition(Vec3 playerPos, Vec3 targetPos, float velocity, double gravity) {
        double horizontalDistance = Math.sqrt(
                Math.pow(targetPos.x - playerPos.x, 2) +
                        Math.pow(targetPos.z - playerPos.z, 2)
        );
        double verticalDistance = targetPos.y - playerPos.y;
        double time = horizontalDistance / (velocity * 3.0);
        double gravityDrop = 0.5 * gravity * time * time;
        return new Vec3(
                targetPos.x,
                targetPos.y + gravityDrop,
                targetPos.z
        );
    }

    private void aimAtEntity(Entity entity) {
        if (mc.player == null) return;

        Vec3 targetPos = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        Vec3 playerPos = mc.player.position().add(0, mc.player.getEyeHeight(), 0);
        double d0 = targetPos.x - playerPos.x;
        double d1 = targetPos.y - playerPos.y;
        double d2 = targetPos.z - playerPos.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        float rotationYaw = (float) (Math.atan2(d2, d0) * 180.0D / Math.PI) - 90.0F;
        float rotationPitch = (float) -(Math.atan2(d1, d3) * 180.0D / Math.PI);

        mc.player.setYRot(rotationYaw);
        mc.player.setXRot(rotationPitch);
    }

    private float getArrowVelocity(int useDuration) {
        float velocity = (float) useDuration / 20.0F;
        velocity = (velocity * velocity + velocity * 2.0F) / 3.0F;

        if (velocity > 1.0F) {
            velocity = 1.0F;
        }

        return velocity;
    }

    private void drawEntityBox(Entity entity, Color color, PoseStack stack) {
        if (entity instanceof LivingEntity) {
            RenderUtils.drawSolidBox(entity.getBoundingBox(), stack);
        }
    }

    private Entity getTarget(boolean throughWalls, String priorityMode) {
        List<Entity> targets = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> entity instanceof LivingEntity)
                .filter(entity -> {
                    LivingEntity livingEntity = (LivingEntity) entity;

                    boolean isTargetType = (entity instanceof Player && aimPlayers.getCurrentValue()) ||
                            (entity instanceof Monster && aimMobs.getCurrentValue()) ||
                            (entity instanceof Animal && aimAnimals.getCurrentValue());

                    return isTargetType &&
                            livingEntity != mc.player &&
                            livingEntity.isAlive() &&
                            (!throughWalls || canEntityBeSeen(entity));
                })
                .collect(Collectors.toList());

        if (targets.isEmpty()) {
            return null;
        }

        switch (priorityMode.toUpperCase()) {
            case "DISTANCE":
                return targets.stream()
                        .min(Comparator.comparingDouble(entity -> mc.player.distanceTo(entity)))
                        .orElse(null);
            case "DIRECTION":
                return targets.stream()
                        .min(Comparator.comparingDouble(entity -> {
                            Vec3 rotation = new Vec3(
                                    RotationUtils.getRotations(entity).x,
                                    RotationUtils.getRotations(entity).y,
                                    0
                            );
                            return Math.abs(mc.player.getYRot() % 360.0F - rotation.x) +
                                    Math.abs(mc.player.getXRot() % 360.0F - rotation.y);
                        }))
                        .orElse(null);
            case "HEALTH":
                return targets.stream()
                        .min(Comparator.comparingDouble(entity -> ((LivingEntity) entity).getHealth()))
                        .orElse(null);
            default:
                return null;
        }
    }

    private boolean canEntityBeSeen(Entity entity) {
        Vec3 vec3 = new Vec3(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(), mc.player.getZ());
        Vec3 vec31 = new Vec3(entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ());
        return mc.level.clip(new net.minecraft.world.level.ClipContext(vec3, vec31, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    public boolean hasTarget() {
        return target != null && mc.player != null && canEntityBeSeen(target);
    }
}