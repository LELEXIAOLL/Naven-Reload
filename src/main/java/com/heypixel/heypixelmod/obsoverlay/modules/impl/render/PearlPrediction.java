package com.heypixel.heypixelmod.obsoverlay.modules.impl.render;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMouseClick;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender2D;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventShader;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.*;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationManager;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.*;
import java.util.List;

@ModuleInfo(
        name = "PearlPrediction",
        description = "Renders prediction and can automatically counter thrown ender pearls.",
        category = Category.RENDER
)
public class PearlPrediction extends Module {

    public static Vector2f rotations;
    public static boolean isThrowing;

    private final BooleanValue renderHud = ValueBuilder.create(this, "Render HUD").setDefaultBooleanValue(true).build().getBooleanValue();
    private final FloatValue hudSize = ValueBuilder.create(this, "HUD Size")
            .setVisibility(renderHud::getCurrentValue)
            .setDefaultFloatValue(0.4f).setFloatStep(0.01f).setMinFloatValue(0.1f).setMaxFloatValue(1.0f).build().getFloatValue();
    private final BooleanValue hideMyOwnPearls = ValueBuilder.create(this, "Hide My Own Pearls").setVisibility(renderHud::getCurrentValue).setDefaultBooleanValue(true).build().getBooleanValue();
    private final BooleanValue pearlCounter = ValueBuilder.create(this, "Pearl Counter").setDefaultBooleanValue(false).build().getBooleanValue();

    private final BooleanValue entityCheck = ValueBuilder.create(this, "Entity Check").setVisibility(pearlCounter::getCurrentValue).setDefaultBooleanValue(true).build().getBooleanValue();
    private final ModeValue triggerMode = ValueBuilder.create(this, "Trigger Key").setVisibility(pearlCounter::getCurrentValue).setModes("Mid Button", "Mouse4", "Mouse5", "None").setDefaultModeIndex(3).build().getModeValue();
    private final FloatValue rotationTicks = ValueBuilder.create(this, "Rotation Ticks").setVisibility(pearlCounter::getCurrentValue).setDefaultFloatValue(1.0f).setFloatStep(1.0f).setMinFloatValue(1.0f).setMaxFloatValue(20.0f).build().getFloatValue();
    private final FloatValue timeOut = ValueBuilder.create(this, "TimeOut")
            .setVisibility(pearlCounter::getCurrentValue)
            .setDefaultFloatValue(5.0f)
            .setFloatStep(0.25f)
            .setMinFloatValue(0.1f)
            .setMaxFloatValue(30.0f)
            .build()
            .getFloatValue();
    private final FloatValue fov = ValueBuilder.create(this, "Fov").setVisibility(pearlCounter::getCurrentValue).setDefaultFloatValue(180.0F).setFloatStep(1.0f).setMinFloatValue(10.0f).setMaxFloatValue(360.0F).build().getFloatValue();
    private final FloatValue minRange = ValueBuilder.create(this, "Min Range").setVisibility(pearlCounter::getCurrentValue).setFloatStep(1.0F).setDefaultFloatValue(5.0f).setMinFloatValue(0.0f).setMaxFloatValue(50.0f).build().getFloatValue();
    private final FloatValue maxRange = ValueBuilder.create(this, "Max Range").setVisibility(pearlCounter::getCurrentValue).setFloatStep(1.0F).setDefaultFloatValue(100.0f).setMinFloatValue(10.0f).setMaxFloatValue(200.0f).build().getFloatValue();
    private final BooleanValue debug = ValueBuilder.create(this, "Debug Messages").setVisibility(pearlCounter::getCurrentValue).setDefaultBooleanValue(false).build().getBooleanValue();

    private final Map<UUID, PredictedPearlInfo> predictedPearls = new HashMap<>();
    private final Set<UUID> processedPearls = new HashSet<>();
    private Vec3 lastSafePosition = null;
    private long lockedPositionExpireTime = 0; // [新逻辑] 存储锁定位置的未来过期时间戳
    private Vector2f targetRotations = null;
    private boolean isAiming = false;
    private int aimingTicks = 0;
    private boolean triggerAction = false;
    private static final List<Vec3> SAMPLE_POINTS = Arrays.asList(new Vec3(0.5, 0.5, 0.5), new Vec3(0.2, 0.5, 0.5), new Vec3(0.8, 0.5, 0.5), new Vec3(0.5, 0.5, 0.2), new Vec3(0.5, 0.5, 0.8));
    private static final double PEARL_INITIAL_VELOCITY = 1.5, PEARL_GRAVITY = 0.03, PEARL_DRAG = 0.99;

    @Override public void onEnable() { super.onEnable(); reset(); }
    @Override public void onDisable() {
        super.onDisable();
        predictedPearls.values().forEach(PredictedPearlInfo::resetAnimation);
        reset();
    }

    @EventTarget
    public void onShader(EventShader e) {
        if (!renderHud.getCurrentValue() || predictedPearls.isEmpty()) return;
        for(PredictedPearlInfo info : predictedPearls.values()) {
            if (info.currentWidth > 0.1F && info.currentHeight > 0.1F) {
                RenderUtils.drawRoundedRect(e.getStack(), info.animX, info.animY, info.currentWidth, info.currentHeight, 5.0F, Integer.MIN_VALUE);
            }
        }
    }

    @EventTarget
    public void onMouseClick(EventMouseClick event) {
        if (!pearlCounter.getCurrentValue() || mc.player == null || triggerMode.isCurrentMode("None")) return;
        int key = triggerMode.isCurrentMode("Mid Button") ? 2 : triggerMode.isCurrentMode("Mouse4") ? 3 : 4;
        if (event.getKey() == key && !event.isState()) { this.triggerAction = true; }
    }

    @EventTarget
    public void onMotion(EventRunTicks event) {
        if (event.getType() != EventType.PRE || mc.player == null || mc.level == null) return;
        updatePearlLogicStates();
        if (pearlCounter.getCurrentValue()) {
            handleCounterLogic();
        } else {
            if (isAiming) reset();
            isThrowing = false;
        }
    }

    @EventTarget
    public void onRender2D(EventRender2D e) {
        if (renderHud.getCurrentValue()) {
            for (PredictedPearlInfo info : this.predictedPearls.values()) {
                info.calculateAnimation();
                if (info.currentWidth > 0.1F && info.currentHeight > 0.1F) {
                    renderLandingMark(e.getStack(), info);
                }
            }
        }
    }

    private void updatePearlLogicStates() {
        Map<UUID, PredictedPearlInfo> updatedPearls = new HashMap<>();
        List<UUID> toRemove = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ThrownEnderpearl pearl) {
                if (hideMyOwnPearls.getCurrentValue() && Objects.equals(pearl.getOwner(), mc.player)) continue;
                UUID uuid = pearl.getUUID();
                PredictedPearlInfo info = predictedPearls.computeIfAbsent(uuid, k -> new PredictedPearlInfo());
                info.update(pearl);
                updatedPearls.put(uuid, info);
            }
        }
        for (UUID uuid : predictedPearls.keySet()) {
            if (!updatedPearls.containsKey(uuid)) toRemove.add(uuid);
        }
        toRemove.forEach(predictedPearls::remove);
    }

    private void handleCounterLogic() {
        if (isAiming) {
            aimingTicks++;
            PearlPrediction.isThrowing = true;
            PearlPrediction.rotations = this.targetRotations;
            float yawDiff = RotationUtils.getAngleDifference(RotationManager.lastRotations.x, this.targetRotations.x);
            float pitchDiff = Math.abs(RotationManager.lastRotations.y - this.targetRotations.y);
            if (yawDiff < 1.0f && pitchDiff < 1.0f && aimingTicks >= rotationTicks.getCurrentValue()) {
                executeThrow();
                reset();
            }
            return;
        }
        PearlPrediction.isThrowing = false;
        updateLastKnownSafePosition();

        // [新逻辑] 检查当前时间是否已经超过了预设的过期时间
        if (lastSafePosition != null && System.currentTimeMillis() > lockedPositionExpireTime) {
            if (debug.getCurrentValue()) {
                ChatUtils.addChatMessage("§e[INFO]§7 Locked position expired.");
            }
            lastSafePosition = null; // 如果已过期，则清空该位置
        }

        boolean shouldTrigger = triggerMode.isCurrentMode("None") || this.triggerAction;
        if (shouldTrigger && lastSafePosition != null) {
            startAiming();
        }
        this.triggerAction = false;
    }

    private void updateLastKnownSafePosition() {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ThrownEnderpearl pearl && !processedPearls.contains(pearl.getUUID())) {
                if (Objects.equals(pearl.getOwner(), mc.player)) continue;
                Object[] data = predictPearlLandingWithTicks(pearl, mc.level);
                Vec3 originalPos = (Vec3) data[0];
                int ticksToLand = (int) data[1]; // 获取珍珠落地所需ticks
                if (originalPos == null) continue;
                processedPearls.add(pearl.getUUID());
                if (!isEnemyPearlThreatening(originalPos) || !isInFov(originalPos) || !(mc.player.position().distanceTo(originalPos) >= minRange.getCurrentValue() && mc.player.position().distanceTo(originalPos) <= maxRange.getCurrentValue())) {
                    if (debug.getCurrentValue() && !isEnemyPearlThreatening(originalPos)) ChatUtils.addChatMessage("§e[INFO]§7 Ignored non-threatening pearl.");
                    continue;
                }
                Vec3 safePos = findBestSafeLandingSpot(originalPos);
                if (safePos != null) {
                    if (debug.getCurrentValue()) ChatUtils.addChatMessage(String.format("§a[LOCKED]§7 New safe spot: %.1f, %.1f, %.1f", safePos.x, safePos.y, safePos.z));
                    this.lastSafePosition = safePos;

                    // [新逻辑] 计算超时时间：从现在起，直到珍珠落地，再加上用户设置的TimeOut时长
                    long landingDurationMs = (long) ticksToLand * 50; // 1 tick = 50ms
                    long timeoutMs = (long) (timeOut.getCurrentValue() * 1000);
                    this.lockedPositionExpireTime = System.currentTimeMillis() + landingDurationMs + timeoutMs;
                }
            }
        }
    }

    private void startAiming() {
        this.targetRotations = calculateOptimalRotations(lastSafePosition);
        if (this.targetRotations != null) {
            this.isAiming = true;
            this.aimingTicks = 0;
            if (debug.getCurrentValue()) ChatUtils.addChatMessage(String.format("§e[AIMING]§7 Yaw: %.1f, Pitch: %.1f", this.targetRotations.x, this.targetRotations.y));
            if (triggerMode.isCurrentMode("None")) { this.lastSafePosition = null; }
        } else {
            if (debug.getCurrentValue()) ChatUtils.addChatMessage("§c[FAIL]§7 Cannot aim, calculation failed.");
        }
    }

    private void executeThrow() {
        int pearlSlot = findPearlSlot();
        if (pearlSlot == -1) {
            if (debug.getCurrentValue()) ChatUtils.addChatMessage("§c[FAIL]§7 No pearl found in hotbar.");
            return;
        }
        if (debug.getCurrentValue()) ChatUtils.addChatMessage("§b[THROW]§7 Rotation synced. Firing pearl!");
        int originalInvSlot = mc.player.getInventory().selected;
        mc.player.getInventory().selected = pearlSlot;
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.getInventory().selected = originalInvSlot;
    }

    private void reset() {
        this.lastSafePosition = null; this.triggerAction = false; this.isAiming = false;
        this.aimingTicks = 0; this.targetRotations = null;
        this.lockedPositionExpireTime = 0; // [新逻辑] 重置过期时间戳
        PearlPrediction.rotations = null; PearlPrediction.isThrowing = false;
        if (processedPearls.size() > 50) processedPearls.clear();
    }

    private Vec3 findBestSafeLandingSpot(Vec3 target) {
        BlockPos originalPos = BlockPos.containing(target);
        Vec3 viablePoint = findViablePointInBlock(originalPos);
        if (viablePoint != null) return viablePoint;
        int radius=8, x=0, z=0, dx=0, dz=-1;
        for (int i=0; i<Math.pow(radius*2+1,2); i++) {
            if ((-radius/2<=x && x<=radius/2) && (-radius/2<=z && z<=radius/2))
                for (int yOff = 3; yOff >= -3; yOff--) {
                    BlockPos finalPos = originalPos.offset(x, yOff, z);
                    Vec3 finalViablePoint = findViablePointInBlock(finalPos);
                    if (finalViablePoint != null) return finalViablePoint;
                }
            if(x==z||(x<0&&x==-z)||(x>0&&x==1-z)){int tmp=dx;dx=-dz;dz=tmp;} x+=dx;z+=dz;
        }
        if(debug.getCurrentValue()) ChatUtils.addChatMessage(String.format("§6[SEARCH FAIL]§7 No clear spot found near (%.0f, %.0f, %.0f).", target.x, target.y, target.z));
        return null;
    }

    private Vec3 findViablePointInBlock(BlockPos pos) {
        if (!isSafeFloor(pos.below()) || !hasHeadroom(pos)) return null;
        for (Vec3 sampleOffset : SAMPLE_POINTS) {
            Vec3 targetPoint = new Vec3(pos.getX() + sampleOffset.x, pos.getY() + sampleOffset.y, pos.getZ() + sampleOffset.z);
            if (hasLineOfSight(targetPoint) && (!entityCheck.getCurrentValue() || isTrajectoryClear(targetPoint))) {
                return targetPoint;
            }
        }
        return null;
    }

    private boolean isTrajectoryClear(Vec3 target) {
        Vector2f rots = calculateOptimalRotations(target);
        if (rots == null) return false;
        Vec3 eyePos = mc.player.getEyePosition();
        double pX=eyePos.x, pY=eyePos.y, pZ=eyePos.z;
        float yawRad=(float)Math.toRadians(rots.x+90.0f), pitchRad=(float)Math.toRadians(-rots.y);
        double mX=Math.cos(yawRad)*Math.cos(pitchRad)*PEARL_INITIAL_VELOCITY, mY=Math.sin(pitchRad)*PEARL_INITIAL_VELOCITY, mZ=Math.sin(yawRad)*Math.cos(pitchRad)*PEARL_INITIAL_VELOCITY;
        for (int i=0; i<300; i++) {
            Vec3 currentPos = new Vec3(pX, pY, pZ), nextPos = new Vec3(pX+mX, pY+mY, pZ+mZ);
            if (currentPos.distanceToSqr(eyePos) > target.distanceToSqr(eyePos)) break;
            if (RayTraceUtils.rayTraceBlocks(currentPos, nextPos, false, false, false, mc.player).getType() != HitResult.Type.MISS) break;
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity.equals(mc.player) || !entity.isPickable() || !(entity instanceof LivingEntity)) continue;
                if (entity.getBoundingBox().inflate(0.3).clip(currentPos, nextPos).isPresent()) return false;
            }
            pX+=mX; pY+=mY; pZ+=mZ;
            mX*=PEARL_DRAG; mY*=PEARL_DRAG; mZ*=PEARL_DRAG; mY-=PEARL_GRAVITY;
        }
        return true;
    }

    private boolean isEnemyPearlThreatening(Vec3 pos) {
        if (pos.y() < mc.level.getMinBuildHeight()) return false;
        return mc.level.getBlockState(BlockPos.containing(pos).below()).isCollisionShapeFullBlock(mc.level, BlockPos.containing(pos).below());
    }

    private boolean isInFov(Vec3 point) {
        float angleDiff = RotationUtils.getAngleDifference(mc.player.getYRot(), (float)(Math.toDegrees(Math.atan2(point.z - mc.player.getEyePosition().z, point.x - mc.player.getEyePosition().x)) - 90.0));
        return Math.abs(angleDiff) <= fov.getCurrentValue() / 2.0;
    }

    private boolean isSafeFloor(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.isCollisionShapeFullBlock(mc.level, pos) && !state.is(Blocks.LAVA) && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.CACTUS);
    }

    private boolean hasHeadroom(BlockPos pos) {
        return !mc.level.getBlockState(pos).isSolidRender(mc.level, pos) && !mc.level.getBlockState(pos.above()).isSolidRender(mc.level, pos.above());
    }

    private boolean hasLineOfSight(Vec3 target) {
        return mc.level.clip(new ClipContext(mc.player.getEyePosition(), target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    private int findPearlSlot() {
        for (int i=0;i<9;i++) if(mc.player.getInventory().getItem(i).getItem()==Items.ENDER_PEARL) return i; return -1;
    }

    private Vector2f calculateOptimalRotations(Vec3 targetPos) {
        Vec3 eyePos = mc.player.getEyePosition();
        double dX=targetPos.x-eyePos.x, dY=targetPos.y-eyePos.y, dZ=targetPos.z-eyePos.z;
        float yaw=(float)(Math.toDegrees(Math.atan2(dZ,dX))-90.0F);
        double hD=Math.sqrt(dX*dX+dZ*dZ); if(hD==0)return new Vector2f(yaw, dY > 0 ? -90f:90f);
        double v=PEARL_INITIAL_VELOCITY, g=PEARL_GRAVITY, disc=v*v*v*v-g*(g*hD*hD+2*dY*v*v);
        if(disc<0)return null;
        float pitch=(float)-Math.toDegrees(Math.atan((v*v-Math.sqrt(disc))/(g*hD)));
        return new Vector2f(yaw,pitch);
    }

    private Object[] predictPearlLandingWithTicks(Entity pearl, ClientLevel level) {
        double pX=pearl.getX(), pY=pearl.getY(), pZ=pearl.getZ();
        double mX=pearl.getDeltaMovement().x, mY=pearl.getDeltaMovement().y, mZ=pearl.getDeltaMovement().z;
        int ticks=0;
        for(int i=0;i<1000;i++){
            Vec3 curr=new Vec3(pX,pY,pZ), next=new Vec3(pX+mX,pY+mY,pZ+mZ);
            ticks++;
            HitResult hit=RayTraceUtils.rayTraceBlocks(curr,next,false,false,false,pearl);
            if(hit.getType()!=HitResult.Type.MISS)return new Object[]{hit.getLocation(),ticks};
            List<Entity> entities=level.getEntities(pearl, pearl.getBoundingBox().expandTowards(mX,mY,mZ).inflate(1.0), e->e instanceof LivingEntity&&!(e instanceof EnderMan)&&e.canBeCollidedWith()&&e!=((ThrownEnderpearl)pearl).getOwner());
            for(Entity e:entities){
                HitResult entityHit=RayTraceUtils.calculateIntercept(e.getBoundingBox(),curr,next);
                if(entityHit!=null)return new Object[]{entityHit.getLocation(),ticks};
            }
            pX+=mX;pY+=mY;pZ+=mZ;
            mX*=PEARL_DRAG;mY*=PEARL_DRAG;mZ*=PEARL_DRAG;mY-=PEARL_GRAVITY;
        }
        return new Object[]{null,ticks};
    }

    private void renderLandingMark(PoseStack matrix, PredictedPearlInfo info) {
        if (info.projectedPos == null) return;

        matrix.pushPose();
        StencilUtils.write(false);
        RenderUtils.drawRoundedRect(matrix, info.animX, info.animY, info.currentWidth, info.currentHeight, 5.0F, -1);
        StencilUtils.erase(true);

        RenderUtils.fill(matrix, info.finalX, info.finalY, info.finalX + info.finalWidth, info.finalY + info.headerHeight, HUD.headerColor);
        RenderUtils.fill(matrix, info.finalX, info.finalY + info.headerHeight, info.finalX + info.finalWidth, info.finalY + info.finalHeight, HUD.bodyColor);

        float tY = info.finalY + info.headerHeight + 2.0f;
        float textRowHeight = (float)Fonts.harmony.getHeight(true, hudSize.getCurrentValue());

        for (String l : info.lines) {
            float textXOffset = (info.finalWidth - Fonts.harmony.getWidth(l, hudSize.getCurrentValue())) / 2;
            Fonts.harmony.render(matrix, l, info.finalX + textXOffset, tY, Color.WHITE, true, hudSize.getCurrentValue());
            tY += textRowHeight * 0.875F;
        }

        StencilUtils.dispose();
        matrix.popPose();
    }

    private class PredictedPearlInfo {
        Vec3 pos; Vector2f projectedPos; int ticksToLand; String ownerName;
        List<String> lines = new ArrayList<>();
        float totalTextHeight, headerHeight = 3.0f;
        float finalWidth, finalHeight, currentWidth, currentHeight, finalX, finalY, animX, animY;
        long lastUpdateTime;

        public PredictedPearlInfo() { this.lastUpdateTime = System.currentTimeMillis(); }

        public void update(ThrownEnderpearl pearl) {
            Object[] data = predictPearlLandingWithTicks(pearl, mc.level);
            this.pos = (Vec3) data[0];
            this.ticksToLand = (int) data[1];
            if (this.pos != null) {
                Entity owner = pearl.getOwner();
                String name = (owner != null) ? owner.getDisplayName().getString() : "Unknown";
                this.ownerName = NameProtect.getName(name);
            }
        }

        public void calculateAnimation() {
            if (this.pos == null) {
                this.finalWidth = 0; this.finalHeight = 0;
                this.currentWidth *= 0.85f; this.currentHeight *= 0.85f;
                return;
            }

            this.projectedPos = ProjectionUtils.project(pos.x, pos.y, pos.z, mc.getFrameTime());
            if (projectedPos == null) { this.finalWidth = 0; this.finalHeight = 0; } else {
                lines.clear();
                lines.add("Ender Pearl");
                lines.add("Thrown by: "+this.ownerName);
                lines.add(String.format("Lands in: %.1f s",this.ticksToLand/20.0));
                lines.add(String.format("Distance: %.1f m", mc.player.position().distanceTo(pos)));

                float textHeight = (float)Fonts.harmony.getHeight(true, hudSize.getCurrentValue());
                this.totalTextHeight = (textHeight * 0.875f) * (lines.size() - 1) + textHeight;

                float w = 0.0f;
                for(String l : lines) w = Math.max(w, (float)Fonts.harmony.getWidth(l, hudSize.getCurrentValue()));

                this.finalWidth = w + 8.0f;
                this.finalHeight = this.totalTextHeight + headerHeight + 4.0f;
            }

            long currentTime = System.currentTimeMillis();
            if(this.lastUpdateTime==0) this.lastUpdateTime=currentTime;
            float deltaTime = (currentTime - this.lastUpdateTime) / 1000.0F;
            float animationSpeed = 10.0F;
            this.currentWidth += (this.finalWidth-this.currentWidth)*animationSpeed*deltaTime;
            this.currentHeight += (this.finalHeight-this.currentHeight)*animationSpeed*deltaTime;
            this.lastUpdateTime = currentTime;
            if(Math.abs(this.finalWidth-this.currentWidth)<0.01f) this.currentWidth=this.finalWidth;
            if(Math.abs(this.finalHeight-this.currentHeight)<0.01f) this.currentHeight=this.finalHeight;

            if (this.projectedPos != null) {
                this.finalX = this.projectedPos.x - this.finalWidth/2.0f;
                this.finalY = this.projectedPos.y - this.finalHeight/2.0f;
                this.animX = this.finalX+(this.finalWidth-this.currentWidth)/2.0f;
                this.animY = this.finalY+(this.finalHeight-this.currentHeight)/2.0f;
            }
        }

        public void resetAnimation() {
            this.currentWidth=0; this.currentHeight=0; this.finalWidth=0; this.finalHeight=0; this.lastUpdateTime=0;
        }
    }
}