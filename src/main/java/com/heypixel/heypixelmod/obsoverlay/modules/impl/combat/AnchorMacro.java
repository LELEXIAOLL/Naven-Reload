package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventKey;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMouseClick;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Random;

@ModuleInfo(
        name = "AnchorMacro",
        category = Category.COMBAT,
        description = "Places an anchor on the block your looking at and glowstones it."
)
public class AnchorMacro extends Module {
    private enum State {
        FIND_ANCHOR, PLACE_ANCHOR,
        FIND_GLOWSTONE, CHARGE_ANCHOR,
        SWITCH_FOR_DETONATE, DETONATE_ANCHOR,
        SWITCH_BACK, DONE
    }

    private static final int STATE_TIMEOUT_TICKS = 40;
    private static final Random random = new Random();
    private static final long RE_ATTEMPT_DELAY_MS = 100L;

    private final ModeValue mode;
    private final FloatValue minDelayMs;
    private final FloatValue maxDelayMs;
    private final BooleanValue chargeAnchors;
    private final BooleanValue detonateAnchors;
    private final FloatValue detonateItemSlot;
    private final BooleanValue cancelOnUse;

    private State currentState;
    private long lastActionTime;
    private long currentDelay;
    private int stateTicks;
    private int originalSlot = -1;
    private BlockPos placedAnchorPos;
    private boolean hasAttemptedPlace;
    private BlockPos searchCenterPos;

    private int detonateAttempts;
    private static final int MAX_DETONATE_ATTEMPTS = 5;

    private boolean macroIsActive = false;
    private long lastPlaceAttemptTime = 0;

    public AnchorMacro() {
        this.setToggleableWithKey(false);
        mode = ValueBuilder.create(this, "Mode").setModes("Press", "Hold").setDefaultModeIndex(0).build().getModeValue();
        minDelayMs = ValueBuilder.create(this, "Min Delay (ms)").setDefaultFloatValue(50.0F).setFloatStep(10.0F).setMinFloatValue(0.0F).setMaxFloatValue(500.0F).build().getFloatValue();
        maxDelayMs = ValueBuilder.create(this, "Max Delay (ms)").setDefaultFloatValue(100.0F).setFloatStep(10.0F).setMinFloatValue(0.0F).setMaxFloatValue(500.0F).build().getFloatValue();
        chargeAnchors = ValueBuilder.create(this, "Charge Anchors").setDefaultBooleanValue(true).build().getBooleanValue();
        detonateAnchors = ValueBuilder.create(this, "Detonate Anchors").setDefaultBooleanValue(false).setVisibility(chargeAnchors::getCurrentValue).build().getBooleanValue();
        detonateItemSlot = ValueBuilder.create(this, "Detonate Item (Slot)").setDefaultFloatValue(1.0F).setFloatStep(1.0F).setMinFloatValue(1.0F).setMaxFloatValue(9.0F).setVisibility(() -> chargeAnchors.getCurrentValue() && detonateAnchors.getCurrentValue()).build().getFloatValue();
        cancelOnUse = ValueBuilder.create(this, "Cancel On Use").setDefaultBooleanValue(false).build().getBooleanValue();
    }

    private void startMacro() {
        if (mc.player == null || macroIsActive) return;
        macroIsActive = true;
        this.currentState = State.FIND_ANCHOR;
        this.stateTicks = 0;
        this.originalSlot = mc.player.getInventory().selected;
        this.placedAnchorPos = null;
        this.hasAttemptedPlace = false;
        this.searchCenterPos = null;
        this.detonateAttempts = 0;
        this.lastPlaceAttemptTime = 0;
    }

    private void stopMacro() {
        if (!macroIsActive) return;
        if (mc.player != null && mc.player.getInventory() != null && originalSlot != -1) {
            mc.player.getInventory().selected = originalSlot;
        }
        macroIsActive = false;
        currentState = null;
        originalSlot = -1;
    }

    @Override
    public void onDisable() {
        stopMacro();
    }

    @EventTarget
    public void onKey(EventKey event) {
        if (!this.isEnabled() || this.getKey() != event.getKey()) return;
        if (mode.isCurrentMode("Press")) {
            if (event.isState()) {
                if (macroIsActive) {
                    stopMacro();
                } else {
                    startMacro();
                }
            }
        } else if (mode.isCurrentMode("Hold")) {
            if (event.isState()) {
                if (!macroIsActive) startMacro();
            } else {
                if (macroIsActive) stopMacro();
            }
        }
    }

    @EventTarget
    public void onMouseClick(EventMouseClick event) {
        if (!this.isEnabled() || this.getKey() != -event.getKey()) return;
        if (mode.isCurrentMode("Press")) {
            if (event.isState()) {
                if (macroIsActive) {
                    stopMacro();
                } else {
                    startMacro();
                }
            }
        } else if (mode.isCurrentMode("Hold")) {
            if (event.isState()) {
                if (!macroIsActive) startMacro();
            } else {
                if (macroIsActive) stopMacro();
            }
        }
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (event.getType() != EventType.PRE || !macroIsActive) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            stopMacro();
            return;
        }
        if (mc.screen != null || !mc.isWindowActive()) return;

        if (currentState == null) {
            stopMacro();
            return;
        }

        if (System.currentTimeMillis() < lastActionTime + currentDelay) {
            stateTicks++;
            if (stateTicks > STATE_TIMEOUT_TICKS) {
                stopMacro();
            }
            return;
        }

        stateTicks = 0;

        switch (currentState) {
            case FIND_ANCHOR -> handleFindAnchor();
            case PLACE_ANCHOR -> handlePlaceAnchor();
            case FIND_GLOWSTONE -> handleFindGlowstone();
            case CHARGE_ANCHOR -> handleChargeAnchor();
            case SWITCH_FOR_DETONATE -> handleSwitchForDetonate();
            case DETONATE_ANCHOR -> handleDetonateAnchor();
            case SWITCH_BACK -> handleSwitchBack();
            case DONE -> stopMacro();
        }
    }

    private void handleFindAnchor() {
        int anchorSlot = findBlockInHotbar(Blocks.RESPAWN_ANCHOR);
        if (anchorSlot != -1) {
            setSlot(anchorSlot);
            transitionTo(State.PLACE_ANCHOR);
        } else {
            stopMacro();
        }
    }

    private void handlePlaceAnchor() {
        if (System.currentTimeMillis() < lastPlaceAttemptTime + RE_ATTEMPT_DELAY_MS) {
            return;
        }

        if (hasAttemptedPlace) {
            if (verifyAndCorrectAnchorPos()) {
                if (chargeAnchors.getCurrentValue()) {
                    transitionTo(State.FIND_GLOWSTONE);
                } else {
                    if (cancelOnUse.getCurrentValue()) stopMacro();
                    else transitionTo(State.SWITCH_BACK);
                }
            } else {
                this.hasAttemptedPlace = false;
                this.placedAnchorPos = null;
                this.lastPlaceAttemptTime = System.currentTimeMillis();
            }
            return;
        }

        if (!(mc.hitResult instanceof BlockHitResult hitResult)) {
            return;
        }

        BlockPos hitBlockPos = hitResult.getBlockPos();
        BlockPos placeTo = hitBlockPos.relative(hitResult.getDirection());
        if (!isCollidesWithEntity(placeTo)) {
            KeyMapping.click(mc.options.keyUse.getKey());
            this.placedAnchorPos = placeTo;
            this.searchCenterPos = hitBlockPos;
            this.hasAttemptedPlace = true;
            this.lastPlaceAttemptTime = System.currentTimeMillis();
        }
    }

    private void handleChargeAnchor() {
        if (placedAnchorPos == null) {
            stopMacro();
            return;
        }
        BlockState anchorState = mc.level.getBlockState(placedAnchorPos);
        if (anchorState.is(Blocks.RESPAWN_ANCHOR) && anchorState.getValue(RespawnAnchorBlock.CHARGE) > 0) {
            if (detonateAnchors.getCurrentValue()) {
                transitionTo(State.SWITCH_FOR_DETONATE);
            } else {
                if (cancelOnUse.getCurrentValue()) stopMacro();
                else handleSwitchBack();
            }
            return;
        }
        if (mc.hitResult instanceof BlockHitResult hitResult && placedAnchorPos.equals(hitResult.getBlockPos())) {
            KeyMapping.click(mc.options.keyUse.getKey());
        }
    }

    private void handleDetonateAnchor() {
        if (placedAnchorPos == null) {
            stopMacro();
            return;
        }

        BlockState anchorState = mc.level.getBlockState(placedAnchorPos);

        if (!anchorState.is(Blocks.RESPAWN_ANCHOR)) {
            if (cancelOnUse.getCurrentValue()) stopMacro();
            else handleSwitchBack();
            return;
        }

        if (anchorState.getValue(RespawnAnchorBlock.CHARGE) == 0) {
            if (cancelOnUse.getCurrentValue()) stopMacro();
            else transitionTo(State.SWITCH_BACK);
            return;
        }

        if (mc.hitResult instanceof BlockHitResult hitResult && placedAnchorPos.equals(hitResult.getBlockPos())) {
            KeyMapping.click(mc.options.keyUse.getKey());
            detonateAttempts++;
        }

        if (detonateAttempts >= MAX_DETONATE_ATTEMPTS) {
            stopMacro();
        }
    }

    private void handleFindGlowstone() {
        int glowstoneSlot = findItemInHotbar(Items.GLOWSTONE);
        if (glowstoneSlot != -1) {
            setSlot(glowstoneSlot);
            transitionTo(State.CHARGE_ANCHOR);
        } else {
            transitionTo(State.SWITCH_BACK);
        }
    }

    private void handleSwitchForDetonate() {
        setSlot((int) detonateItemSlot.getCurrentValue() - 1);
        transitionTo(State.DETONATE_ANCHOR);
    }

    private void handleSwitchBack() {
        stopMacro();
    }

    private void transitionTo(State nextState) {
        this.currentState = nextState;
        this.stateTicks = 0;
        this.lastActionTime = System.currentTimeMillis();
        this.currentDelay = getRandomDelay();
        if (nextState == State.PLACE_ANCHOR) {
            this.hasAttemptedPlace = false;
            this.searchCenterPos = null;
        }
    }

    private long getRandomDelay() {
        float min = minDelayMs.getCurrentValue();
        float max = maxDelayMs.getCurrentValue();
        if (min >= max) {
            return (long) min;
        }
        return (long) (min + random.nextFloat() * (max - min));
    }

    private boolean verifyAndCorrectAnchorPos() {
        if (mc.level == null) return false;
        if (placedAnchorPos != null && mc.level.getBlockState(placedAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
            return true;
        }
        if (searchCenterPos != null) {
            for (BlockPos pos : BlockPos.betweenClosed(searchCenterPos.offset(-1, -1, -1), searchCenterPos.offset(1, 1, 1))) {
                if (mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) {
                    this.placedAnchorPos = pos.immutable();
                    return true;
                }
            }
        }
        return false;
    }

    private void setSlot(int slot) {
        if (slot >= 0 && slot < 9 && mc.player != null) {
            mc.player.getInventory().selected = slot;
        }
    }

    private int findItemInHotbar(Item item) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; ++i) {
            if (mc.player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private int findBlockInHotbar(net.minecraft.world.level.block.Block block) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; ++i) {
            if (mc.player.getInventory().getItem(i).getItem() instanceof BlockItem blockItem && blockItem.getBlock() == block) return i;
        }
        return -1;
    }

    private boolean isCollidesWithEntity(BlockPos pos) {
        if (mc.level == null) return false;
        AABB boundingBox = new AABB(pos);
        return !mc.level.getEntitiesOfClass(Player.class, boundingBox, player -> !player.isCreative() && !player.isSpectator()).isEmpty();
    }
}