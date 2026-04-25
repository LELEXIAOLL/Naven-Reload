package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.Vector2f;
import com.heypixel.heypixelmod.obsoverlay.utils.rotation.RotationUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

@ModuleInfo(
        name = "AntiWeb",
        category = Category.MISC,
        description = "Automatically handles being stuck in webs by using water."
)
public class AntiWeb extends Module {

    private enum State {
        IDLE, FIND_WATER_BUCKET, ROTATE_TO_PLACE, PLACE_WATER,
        WAIT_FOR_WEB_BREAK, FIND_EMPTY_BUCKET, ROTATE_TO_COLLECT,
        COLLECT_WATER, VERIFY_COLLECTION, ROTATE_BACK, SWITCH_BACK
    }

    private static final int STATE_TIMEOUT_TICKS = 80;
    private static final int MAX_COLLECTION_RETRIES = 5;
    private static final Random random = new Random();
    private final FloatValue minDelayMs, maxDelayMs, turnSpeed, rotationTicks;
    private State currentState = State.IDLE;
    private BlockPos actionPos = null;
    private int originalSlot = -1;
    private Item originalItem = null;
    private int waterBucketSlot = -1, emptyBucketSlot = -1;
    private int collectionRetries = 0;
    private long lastActionTime, currentDelay;
    private int stateTicks;
    private int ticksSinceRotationComplete = 0;
    public Vector2f rots = new Vector2f();
    private Vector2f targetRots = new Vector2f();
    private float originalYaw, originalPitch;

    public AntiWeb() {
        minDelayMs = ValueBuilder.create(this, "Min Delay (ms)").setDefaultFloatValue(50.0F).setFloatStep(10.0F).setMinFloatValue(0.0F).setMaxFloatValue(500.0F).build().getFloatValue();
        maxDelayMs = ValueBuilder.create(this, "Max Delay (ms)").setDefaultFloatValue(100.0F).setFloatStep(10.0F).setMinFloatValue(0.0F).setMaxFloatValue(500.0F).build().getFloatValue();
        turnSpeed = ValueBuilder.create(this, "Turn Speed").setDefaultFloatValue(50.0F).setFloatStep(1.0F).setMinFloatValue(1.0F).setMaxFloatValue(180.0F).build().getFloatValue();
        rotationTicks = ValueBuilder.create(this, "Rotation Ticks").setDefaultFloatValue(3.0F).setFloatStep(1.0F).setMinFloatValue(0.0F).setMaxFloatValue(20.0F).build().getFloatValue();
    }

    private void resetState() {
        currentState = State.IDLE;
        originalSlot = -1;
        originalItem = null;
        waterBucketSlot = -1;
        emptyBucketSlot = -1;
        ticksSinceRotationComplete = 0;
        collectionRetries = 0;
        actionPos = null;
    }

    @Override
    public void onEnable() {
        resetState();
        if (mc.player != null) {
            rots.set(mc.player.getYRot(), mc.player.getXRot());
            targetRots.set(mc.player.getYRot(), mc.player.getXRot());
        }
    }

    @Override
    public void onDisable() {
        if (currentState != State.IDLE && originalSlot != -1 && mc.player != null) {
            handleSwitchBack();
        }
        resetState();
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (mc.player == null || mc.level == null || event.getType() != EventType.PRE) return;
        if (currentState == State.IDLE) {
            rots.set(mc.player.getYRot(), mc.player.getXRot());
            BlockPos intersectingWeb = getIntersectingWebPos();
            if (intersectingWeb != null) {
                originalSlot = mc.player.getInventory().selected;
                originalItem = mc.player.getMainHandItem().getItem();
                originalYaw = mc.player.getYRot();
                originalPitch = mc.player.getXRot();
                actionPos = intersectingWeb;
                transitionTo(State.FIND_WATER_BUCKET);
            }
        } else {
            if (!enforceHeldItem()) {
                transitionTo(State.SWITCH_BACK);
                return;
            }
            updateSmoothRotations();
            if (System.currentTimeMillis() < lastActionTime + currentDelay) {
                stateTicks++;
                if (stateTicks > STATE_TIMEOUT_TICKS) transitionTo(State.SWITCH_BACK);
                return;
            }
            stateTicks = 0;
            executeStateLogic();
        }
    }

    private void executeStateLogic() {
        switch (currentState) {
            case ROTATE_TO_PLACE, ROTATE_TO_COLLECT, ROTATE_BACK -> handleRotationStates();
            case FIND_WATER_BUCKET -> handleFindWaterBucket();
            case PLACE_WATER -> handlePlaceWater();
            case WAIT_FOR_WEB_BREAK -> handleWaitForWebBreak();
            case FIND_EMPTY_BUCKET -> handleFindEmptyBucket();
            case COLLECT_WATER -> handleCollectWater();
            case VERIFY_COLLECTION -> handleVerifyCollection();
            case SWITCH_BACK -> handleSwitchBack();
        }
    }

    private void handleRotationStates() { if (isRotationComplete()) { ticksSinceRotationComplete++; if (ticksSinceRotationComplete >= rotationTicks.getCurrentValue()) { switch (currentState) { case ROTATE_TO_PLACE -> transitionTo(State.PLACE_WATER); case ROTATE_TO_COLLECT -> transitionTo(State.COLLECT_WATER); case ROTATE_BACK -> transitionTo(State.SWITCH_BACK); } } } else { ticksSinceRotationComplete = 0; } }
    private void handleFindWaterBucket() { waterBucketSlot = findItemInHotbar(Items.WATER_BUCKET); if (waterBucketSlot != -1 && actionPos != null) { setSlot(waterBucketSlot); Vec3 targetPoint = new Vec3(actionPos.getX() + 0.5, actionPos.getY(), actionPos.getZ() + 0.5); targetRots = RotationUtils.getRotationsVector(targetPoint); transitionTo(State.ROTATE_TO_PLACE); } else { transitionTo(State.SWITCH_BACK); } }
    private void handlePlaceWater() { KeyMapping.click(mc.options.keyUse.getKey()); transitionTo(State.WAIT_FOR_WEB_BREAK); }
    private void handleWaitForWebBreak() { if (actionPos == null || !mc.level.getBlockState(actionPos).is(Blocks.COBWEB)) { transitionTo(State.FIND_EMPTY_BUCKET); } }
    private void handleFindEmptyBucket() { if (actionPos == null || !mc.level.getBlockState(actionPos).is(Blocks.WATER)) { targetRots.set(originalYaw, originalPitch); transitionTo(State.ROTATE_BACK); return; } emptyBucketSlot = findItemInHotbar(Items.BUCKET); if (emptyBucketSlot != -1) { setSlot(emptyBucketSlot); aimForWater(); transitionTo(State.ROTATE_TO_COLLECT); } else { transitionTo(State.SWITCH_BACK); } }
    private void handleCollectWater() { if (actionPos != null && mc.level.getBlockState(actionPos).is(Blocks.WATER)) { KeyMapping.click(mc.options.keyUse.getKey()); } transitionTo(State.VERIFY_COLLECTION); }
    private void handleVerifyCollection() { if (isHolding(Items.WATER_BUCKET)) { targetRots.set(originalYaw, originalPitch); transitionTo(State.ROTATE_BACK); return; } if (collectionRetries < MAX_COLLECTION_RETRIES) { collectionRetries++; transitionTo(State.ROTATE_TO_COLLECT); } else { targetRots.set(originalYaw, originalPitch); transitionTo(State.ROTATE_BACK); } }
    private void handleSwitchBack() { if (originalItem != null && originalSlot != -1 && mc.player != null) { if (mc.player.getInventory().getItem(originalSlot).is(originalItem)) { setSlot(originalSlot); } else { int newSlot = findItemInHotbar(originalItem); if (newSlot != -1) { setSlot(newSlot); } } } resetState(); }

    // --- Helper & Utility Methods ---

    /** [FIXED] Restored the missing helper method. */
    private boolean isHolding(Item item) {
        return mc.player != null && mc.player.getMainHandItem().is(item);
    }

    private void aimForWater() { if (actionPos == null) { transitionTo(State.SWITCH_BACK); return; } Vec3 targetPoint = new Vec3(actionPos.getX() + 0.5, actionPos.getY() + 0.9, actionPos.getZ() + 0.5); targetRots = RotationUtils.getRotationsVector(targetPoint); }
    private boolean enforceHeldItem() { if (mc.player == null) return false; switch (currentState) { case ROTATE_TO_PLACE, PLACE_WATER: if (waterBucketSlot == -1) return false; if (mc.player.getInventory().selected != waterBucketSlot) { if(!mc.player.getInventory().getItem(waterBucketSlot).is(Items.WATER_BUCKET)) { transitionTo(State.FIND_WATER_BUCKET); return true; } mc.player.getInventory().selected = waterBucketSlot; } break; case ROTATE_TO_COLLECT, COLLECT_WATER, VERIFY_COLLECTION: if (emptyBucketSlot == -1) return false; if (mc.player.getInventory().selected != emptyBucketSlot) { if(!mc.player.getInventory().getItem(emptyBucketSlot).is(Items.BUCKET)) { transitionTo(State.FIND_EMPTY_BUCKET); return true; } mc.player.getInventory().selected = emptyBucketSlot; } break; } return true; }
    private BlockPos getIntersectingWebPos() { if (mc.player == null || mc.level == null) return null; AABB playerBox = mc.player.getBoundingBox(); AABB checkZone = playerBox.inflate(-0.05, 0, -0.05); for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(checkZone.minX, checkZone.minY, checkZone.minZ), BlockPos.containing(checkZone.maxX, checkZone.maxY, checkZone.maxZ))) { if (mc.level.getBlockState(pos).is(Blocks.COBWEB)) { return pos.immutable(); } } return null; }
    private void updateSmoothRotations() { float speed = turnSpeed.getCurrentValue(); float newYaw = RotationUtils.updateRotation(rots.getX(), targetRots.getX(), speed); float deltaPitch = targetRots.getY() - rots.getY(); float turnPitch = Math.min(Math.abs(deltaPitch), speed) * Math.signum(deltaPitch); rots.set(newYaw, rots.getY() + turnPitch); }
    private boolean isRotationComplete() { return RotationUtils.getAngleDifference(rots.getX(), targetRots.getX()) < 1.5f && Math.abs(rots.getY() - targetRots.getY()) < 1.5f; }
    private void transitionTo(State nextState) { currentState = nextState; stateTicks = 0; ticksSinceRotationComplete = 0; lastActionTime = System.currentTimeMillis(); currentDelay = getRandomDelay(); }
    private long getRandomDelay() { return (long) (minDelayMs.getCurrentValue() + random.nextFloat() * (maxDelayMs.getCurrentValue() - minDelayMs.getCurrentValue())); }
    private void setSlot(int slot) { if (slot >= 0 && slot < 9 && mc.player != null) mc.player.getInventory().selected = slot; }
    private int findItemInHotbar(Item item) { if (mc.player == null) return -1; for (int i = 0; i < 9; ++i) if (mc.player.getInventory().getItem(i).is(item)) return i; return -1; }
}