package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;
import java.util.stream.StreamSupport;

@ModuleInfo(
        name = "AutoCrystal",
        category = Category.COMBAT,
        description = "Automatically places and explodes crystals."
)
public class AutoCrystal extends Module {

    private final FloatValue placeDelay;
    private final FloatValue breakDelay;
    private final BooleanValue headBob;
    private final BooleanValue inAir;
    private final BooleanValue autoSwitch;
    private final BooleanValue damageTick;
    private final BooleanValue pauseOnKill;
    private final BooleanValue rightClickHold;

    private long lastPlaceTime;
    private long lastBreakTime;

    public AutoCrystal() {
        placeDelay = ValueBuilder.create(this, "Place Delay (Ticks)")
                .setDefaultFloatValue(1.0f)
                .setFloatStep(1.0f)
                .setMinFloatValue(0.0f)
                .setMaxFloatValue(20.0f)
                .build()
                .getFloatValue();
        breakDelay = ValueBuilder.create(this, "Break Delay (Ticks)")
                .setDefaultFloatValue(1.0f)
                .setFloatStep(1.0f)
                .setMinFloatValue(0.0f)
                .setMaxFloatValue(20.0f)
                .build()
                .getFloatValue();
        headBob = ValueBuilder.create(this, "Head Bob")
                .setDefaultBooleanValue(false)
                .build()
                .getBooleanValue();
        inAir = ValueBuilder.create(this, "In Air")
                .setDefaultBooleanValue(false)
                .build()
                .getBooleanValue();
        autoSwitch = ValueBuilder.create(this, "Auto Switch")
                .setDefaultBooleanValue(true)
                .build()
                .getBooleanValue();
        damageTick = ValueBuilder.create(this, "Damage Tick")
                .setDefaultBooleanValue(false)
                .build()
                .getBooleanValue();
        pauseOnKill = ValueBuilder.create(this, "Pause On Kill")
                .setDefaultBooleanValue(false)
                .build()
                .getBooleanValue();
        rightClickHold = ValueBuilder.create(this, "Only When Right Hold")
                .setDefaultBooleanValue(true)
                .build()
                .getBooleanValue();
    }

    @Override
    public void onEnable() {
        long currentTime = System.currentTimeMillis();
        lastPlaceTime = currentTime;
        lastBreakTime = currentTime;
    }

    @EventTarget
    public void onMotion(EventMotion e) {
        if (e.getType() != EventType.PRE) return;

        if (mc.screen != null || !mc.isWindowActive() || (pauseOnKill.getCurrentValue() && isInvalidPlayer())) {
            return;
        }

        if (rightClickHold.getCurrentValue() && !mc.options.keyUse.isDown()) {
            return;
        }

        if (!mc.player.onGround() && !inAir.getCurrentValue()) {
            return;
        }

        if (System.currentTimeMillis() - lastBreakTime >= (breakDelay.getCurrentValue() * 50)) {
            performBreakLogic();
        }

        if (System.currentTimeMillis() - lastPlaceTime >= (placeDelay.getCurrentValue() * 50)) {
            performPlaceLogic();
        }
    }

    private void performBreakLogic() {
        HitResult hitResult = mc.hitResult;
        Entity entityToBreak = null;

        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hitResult).getEntity();
            if (entity instanceof EndCrystal || entity instanceof MagmaCube || entity instanceof Slime) {
                if (!damageTick.getCurrentValue() || (entity instanceof Player && ((Player) entity).hurtTime > 0)) {
                    entityToBreak = entity;
                }
            }
        }

        if (entityToBreak == null && headBob.getCurrentValue() && (hitResult == null || hitResult.getType() == HitResult.Type.MISS)) {
            Optional<EndCrystal> nearestCrystalOpt = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                    .filter(entityx -> entityx instanceof EndCrystal && mc.player.distanceTo(entityx) <= 4.5f)
                    .map(entityx -> (EndCrystal) entityx)
                    .min((c1, c2) -> Float.compare(mc.player.distanceTo(c1), mc.player.distanceTo(c2)));
            if (nearestCrystalOpt.isPresent()) {
                entityToBreak = nearestCrystalOpt.get();
            }
        }

        if (entityToBreak != null) {
            int crystalSlot = findItemInHotbar(Items.END_CRYSTAL);
            if (crystalSlot == -1) return;

            if (autoSwitch.getCurrentValue()) {
                setSlot(crystalSlot);
            } else if (mc.player.getInventory().selected != crystalSlot) {
                return;
            }

            mc.gameMode.attack(mc.player, entityToBreak);
            mc.player.swing(InteractionHand.MAIN_HAND);
            lastBreakTime = System.currentTimeMillis();
        }
    }

    private void performPlaceLogic() {
        HitResult hitResult = mc.hitResult;

        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            BlockPos blockPos = blockHitResult.getBlockPos();
            if (mc.level.getBlockState(blockPos).is(Blocks.BEDROCK) || mc.level.getBlockState(blockPos).is(Blocks.OBSIDIAN)) {
                if (!isCollidesWithEntity(blockPos.above())) {
                    int crystalSlot = findItemInHotbar(Items.END_CRYSTAL);
                    if (crystalSlot == -1) return;

                    if (autoSwitch.getCurrentValue()) {
                        setSlot(crystalSlot);
                    } else if (mc.player.getInventory().selected != crystalSlot) {
                        return;
                    }

                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHitResult);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    lastPlaceTime = System.currentTimeMillis();
                }
            }
        }
    }

    private void setSlot(int slot) {
        if (mc.player != null && slot >= 0 && slot < 9) {
            mc.player.getInventory().selected = slot;
        }
    }

    private int findItemInHotbar(net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; ++i) {
            if (mc.player.getInventory().getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isCollidesWithEntity(BlockPos pos) {
        AABB boundingBox = new AABB(pos);
        for (Entity entity : mc.level.getEntities(null, boundingBox)) {
            if (entity instanceof EndCrystal || entity instanceof Player) {
                return true;
            }
        }
        return false;
    }

    private boolean isInvalidPlayer() {
        return mc.level.players().stream()
                .noneMatch(player -> player != mc.player && player.isAlive());
    }
}