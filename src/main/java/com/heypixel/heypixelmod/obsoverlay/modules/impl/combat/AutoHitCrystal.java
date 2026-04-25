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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Random;

@ModuleInfo(
        name = "AutoHitCrystal",
        category = Category.COMBAT,
        description = "Automatically places and explodes crystals."
)
public class AutoHitCrystal extends Module {

    private static final Random random = new Random();

    private final FloatValue minDelayMs;
    private final FloatValue maxDelayMs;
    private final ModeValue mode;
    private final BooleanValue perfectTiming;
    private final BooleanValue pauseOnKill;
    private final ModeValue activationMode;

    private long lastActionTime;
    private long currentDelay;
    private int originalSlot = -1;
    private int progress;
    private boolean macroIsActive = false;

    public AutoHitCrystal() {
        this.setToggleableWithKey(false);
        activationMode = ValueBuilder.create(this, "Activation Mode")
                .setModes("Press", "Hold")
                .setDefaultModeIndex(0)
                .build()
                .getModeValue();
        minDelayMs = ValueBuilder.create(this, "Min Delay (ms)")
                .setDefaultFloatValue(50.0f)
                .setFloatStep(10.0f)
                .setMinFloatValue(0.0f)
                .setMaxFloatValue(500.0f)
                .build()
                .getFloatValue();
        maxDelayMs = ValueBuilder.create(this, "Max Delay (ms)")
                .setDefaultFloatValue(100.0f)
                .setFloatStep(10.0f)
                .setMinFloatValue(0.0f)
                .setMaxFloatValue(500.0f)
                .build()
                .getFloatValue();
        mode = ValueBuilder.create(this, "Crystal")
                .setModes("None", "Single Tap", "Double Tap")
                .setDefaultModeIndex(1)
                .build()
                .getModeValue();
        perfectTiming = ValueBuilder.create(this, "Perfect Timing")
                .setDefaultBooleanValue(false)
                .build()
                .getBooleanValue();
        pauseOnKill = ValueBuilder.create(this, "Pause On Kill")
                .setDefaultBooleanValue(false)
                .build()
                .getBooleanValue();
    }

    @EventTarget
    public void onKey(EventKey event) {
        if (!this.isEnabled() || this.getKey() != event.getKey()) return;
        if (activationMode.isCurrentMode("Press")) {
            if (event.isState()) {
                if (macroIsActive) {
                    stopMacro();
                } else {
                    startMacro();
                }
            }
        } else if (activationMode.isCurrentMode("Hold")) {
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
        if (activationMode.isCurrentMode("Press")) {
            if (event.isState()) {
                if (macroIsActive) {
                    stopMacro();
                } else {
                    startMacro();
                }
            }
        } else if (activationMode.isCurrentMode("Hold")) {
            if (event.isState()) {
                if (!macroIsActive) startMacro();
            } else {
                if (macroIsActive) stopMacro();
            }
        }
    }

    private void startMacro() {
        if (mc.player == null || macroIsActive) return;
        macroIsActive = true;
        lastActionTime = System.currentTimeMillis();
        currentDelay = getRandomDelay();
        originalSlot = mc.player.getInventory().selected;
        this.progress = 0;
    }

    private void stopMacro() {
        if (!macroIsActive) return;
        macroIsActive = false;
        if (mc.player != null && originalSlot != -1) {
            mc.player.getInventory().selected = originalSlot;
        }
        originalSlot = -1;
    }

    @EventTarget
    public void onMotion(EventMotion e) {
        if (!macroIsActive) return;
        if (e.getType() != EventType.PRE) return;

        if (mc.screen != null || !mc.isWindowActive() || (pauseOnKill.getCurrentValue() && isInvalidPlayer())) {
            return;
        }

        if (System.currentTimeMillis() - lastActionTime < currentDelay) {
            return;
        }

        Player targetPlayer = findTarget();

        switch (progress) {
            case 0: { // 找黑曜石
                if (mc.hitResult instanceof BlockHitResult hitResult) {
                    if (mc.level.getBlockState(hitResult.getBlockPos()).is(Blocks.OBSIDIAN) || mc.level.getBlockState(hitResult.getBlockPos()).is(Blocks.BEDROCK)) {
                        int crystalSlot = findItemInHotbar(Items.END_CRYSTAL);
                        if (crystalSlot != -1) {
                            mc.player.getInventory().selected = crystalSlot; // 直接切换
                            progress = 3;
                            lastActionTime = System.currentTimeMillis();
                            currentDelay = getRandomDelay();
                            return;
                        }
                    }
                }

                int obsidianSlot = findBlockInHotbar(Blocks.OBSIDIAN);
                if (obsidianSlot != -1) {
                    mc.player.getInventory().selected = obsidianSlot; // 直接切换
                    setProgress();
                } else {
                    stopMacro();
                }
                break;
            }
            case 1: { // 放黑曜石
                HitResult hitResult = mc.hitResult;
                if (hitResult instanceof BlockHitResult blockHitResult) {
                    BlockPos blockPos = blockHitResult.getBlockPos();
                    BlockPos placePos = blockPos.relative(blockHitResult.getDirection());
                    if (!isCollidesWithEntity(placePos)) {
                        KeyMapping.click(mc.options.keyUse.getKey());
                        mc.player.swing(InteractionHand.MAIN_HAND);
                        setProgress();
                    }
                }
                break;
            }
            case 2: { // 找水晶
                int crystalSlot = findItemInHotbar(Items.END_CRYSTAL);
                if (crystalSlot != -1) {
                    mc.player.getInventory().selected = crystalSlot; // 直接切换
                    setProgress();
                } else {
                    stopMacro();
                }
                break;
            }
            case 3: { // 放水晶
                HitResult hitResult = mc.hitResult;
                if (hitResult instanceof BlockHitResult blockHitResult) {
                    if (mc.level.getBlockState(blockHitResult.getBlockPos()).is(Blocks.OBSIDIAN) || mc.level.getBlockState(blockHitResult.getBlockPos()).is(Blocks.BEDROCK)) {
                        if (!isCollidesWithEntity(blockHitResult.getBlockPos().above())) {
                            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHitResult);
                            mc.player.swing(InteractionHand.MAIN_HAND);
                            setProgress();
                        }
                    }
                }
                break;
            }
            case 4: { // 第一次打水晶
                e.setPitch(e.getPitch() - 20.0f);
                if (mode.isCurrentMode("None")) {
                    stopMacro();
                    break;
                }

                Entity entityToBreak = null;
                HitResult hitResult = mc.hitResult;

                if (hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof EndCrystal) {
                    entityToBreak = entityHitResult.getEntity();
                }

                if (entityToBreak != null) {
                    if (perfectTiming.getCurrentValue() && targetPlayer != null && targetPlayer.hurtTime > 0) {
                        break;
                    }
                    mc.gameMode.attack(mc.player, entityToBreak);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    if (mode.isCurrentMode("Double Tap")) {
                        setProgress();
                    } else {
                        stopMacro();
                    }
                }
                e.setPitch(e.getPitch() + 20.0f);
                break;
            }
            case 5: { // 第二次放水晶 (Double Tap 模式)
                HitResult hitResult = mc.hitResult;
                if (hitResult instanceof BlockHitResult blockHitResult) {
                    if (mc.level.getBlockState(blockHitResult.getBlockPos()).is(Blocks.OBSIDIAN) || mc.level.getBlockState(blockHitResult.getBlockPos()).is(Blocks.BEDROCK)) {
                        if (!isCollidesWithEntity(blockHitResult.getBlockPos().above())) {
                            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHitResult);
                            mc.player.swing(InteractionHand.MAIN_HAND);

                            // 再次上抬视角
                            e.setPitch(e.getPitch() - 20.0f);

                            setProgress();
                        }
                    }
                }
                break;
            }
            case 6: { // 第二次打水晶 (Double Tap 模式)
                e.setPitch(e.getPitch() - 20.0f);
                Entity entityToBreak = null;
                HitResult hitResult = mc.hitResult;

                if (hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof EndCrystal) {
                    entityToBreak = entityHitResult.getEntity();
                }

                if (entityToBreak != null) {
                    if (perfectTiming.getCurrentValue() && targetPlayer != null && targetPlayer.hurtTime > 0) {
                        break;
                    }
                    mc.gameMode.attack(mc.player, entityToBreak);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    stopMacro();
                }
                e.setPitch(e.getPitch() + 20.0f);
                break;
            }
        }
    }

    private void setProgress() {
        lastActionTime = System.currentTimeMillis();
        currentDelay = getRandomDelay();
        progress++;
    }

    private long getRandomDelay() {
        float min = minDelayMs.getCurrentValue();
        float max = maxDelayMs.getCurrentValue();
        if (min >= max) {
            return (long) min;
        }
        return (long) (min + random.nextFloat() * (max - min));
    }

    private int findItemInHotbar(net.minecraft.world.item.Item item) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; ++i) {
            if (mc.player.getInventory().getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    private int findBlockInHotbar(net.minecraft.world.level.block.Block block) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; ++i) {
            if (mc.player.getInventory().getItem(i).getItem() instanceof BlockItem blockItem && blockItem.getBlock() == block) {
                return i;
            }
        }
        return -1;
    }

    private Player findTarget() {
        if (mc.level == null || mc.player == null) return null;

        Player closestPlayer = null;
        double closestDistance = Double.MAX_VALUE;

        for (Player player : mc.level.players()) {
            if (player != mc.player && player.isAlive() && !player.isSpectator()) {
                double distance = mc.player.distanceTo(player);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestPlayer = player;
                }
            }
        }
        return closestPlayer;
    }

    private boolean isCollidesWithEntity(BlockPos pos) {
        if (mc.level == null) return false;
        AABB boundingBox = new AABB(pos);
        for (Entity entity : mc.level.getEntities(null, boundingBox)) {
            if (entity instanceof EndCrystal || entity instanceof Player) {
                return true;
            }
        }
        return false;
    }

    private boolean isInvalidPlayer() {
        if (mc.level == null) return true;
        return mc.level.players().stream()
                .noneMatch(player -> player != mc.player && player.isAlive());
    }
}
