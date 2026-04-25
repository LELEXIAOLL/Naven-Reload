package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventClick;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.Teams;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(name = "TriggerBot", description = "Attacks entities when looking at them", category = Category.COMBAT)
public class TriggerBot extends Module {

    private final BooleanValue attackPlayer;
    private final BooleanValue attackMobs;
    private final BooleanValue attackCrystals;
    private final FloatValue aps;
    private final FloatValue chance;
    private final BooleanValue weaponOnly;
    private final BooleanValue inAir;
    private final BooleanValue critTiming;
    private final BooleanValue checkCooldown;
    private final FloatValue cooldownThreshold;
    private Entity currentTarget;
    private float attacks = 0.0F;
    private final Random random = ThreadLocalRandom.current();

    public TriggerBot() {
        attackPlayer = ValueBuilder.create(this, "Attack Player").setDefaultBooleanValue(true).build().getBooleanValue();
        attackMobs = ValueBuilder.create(this, "Attack Mobs").setDefaultBooleanValue(true).build().getBooleanValue();
        attackCrystals = ValueBuilder.create(this, "Attack Crystals").setDefaultBooleanValue(true).build().getBooleanValue();
        aps = ValueBuilder.create(this, "Attack Per Second")
                .setDefaultFloatValue(10.0F).setFloatStep(1.0F).setMinFloatValue(1.0F).setMaxFloatValue(20.0F)
                .build().getFloatValue();
        chance = ValueBuilder.create(this, "Chance")
                .setDefaultFloatValue(100.0F).setFloatStep(1.0F).setMinFloatValue(0.0F).setMaxFloatValue(100.0F)
                .build().getFloatValue();
        weaponOnly = ValueBuilder.create(this, "Weapon Only").setDefaultBooleanValue(true).build().getBooleanValue();
        inAir = ValueBuilder.create(this, "In Air").setDefaultBooleanValue(false).build().getBooleanValue();
        critTiming = ValueBuilder.create(this, "Crit Timing")
                .setVisibility(() -> !inAir.getCurrentValue())
                .setDefaultBooleanValue(false)
                .build().getBooleanValue();
        checkCooldown = ValueBuilder.create(this, "Cooldown").setDefaultBooleanValue(true).build().getBooleanValue();
        cooldownThreshold = ValueBuilder.create(this, "Cooldown Threshold")
                .setVisibility(checkCooldown::getCurrentValue)
                .setDefaultFloatValue(0.9f).setFloatStep(0.05f).setMinFloatValue(0.0f).setMaxFloatValue(1.0f)
                .build().getFloatValue();
    }

    @Override
    public void onEnable() {
        currentTarget = null;
        attacks = 0.0F;
    }

    @Override
    public void onDisable() {
        currentTarget = null;
        attacks = 0.0F;
    }

    @EventTarget
    public void onMotion(EventRunTicks event) {
        if (event.getType() != EventType.PRE || mc.player == null || mc.level == null) {
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hitResult).getEntity();
            if (isValidTarget(entity)) {
                currentTarget = entity;
            } else {
                currentTarget = null;
            }
        } else {
            currentTarget = null;
        }

        this.attacks += this.aps.getCurrentValue() / 20.0F;
    }

    @EventTarget
    public void onClick(EventClick event) {
        while (this.attacks >= 1.0F) {
            if (currentTarget != null && isReadyToAttack()) {
                if (random.nextFloat() * 100.0f <= chance.getCurrentValue()) {
                    attackEntity(currentTarget);
                }
                this.attacks--;
            } else {
                break;
            }
        }
    }

    private boolean isReadyToAttack() {
        if (mc.player == null || mc.screen != null || !mc.isWindowActive()) {
            return false;
        }

        if (mc.player.isUsingItem()) {
            return false;
        }

        if (weaponOnly.getCurrentValue()) {
            if (!(mc.player.getMainHandItem().getItem() instanceof SwordItem ||
                    mc.player.getMainHandItem().getItem() instanceof AxeItem)) {
                return false;
            }
        }

        if (checkCooldown.getCurrentValue()) {
            if (mc.player.getAttackStrengthScale(0.0F) < cooldownThreshold.getCurrentValue()) {
                return false;
            }
        }

        if (critTiming.getCurrentValue()) {
            if (!mc.player.onGround() && mc.player.fallDistance > 0.0f && !mc.player.isPassenger() && !mc.player.isInWater() && !mc.player.isInLava()) {
                return true;
            } else {
                return mc.player.onGround();
            }
        } else if (!inAir.getCurrentValue()) {
            return mc.player.onGround();
        }

        return true;
    }

    public boolean isValidTarget(Entity entity) {
        if (entity == mc.player || !entity.isAlive()) {
            return false;
        }

        AntiBots antiBotsModule = (AntiBots) Naven.getInstance().getModuleManager().getModule(AntiBots.class);
        if (antiBotsModule != null && antiBotsModule.isEnabled()) {
            if (AntiBots.isBot(entity) || AntiBots.isBedWarsBot(entity)) {
                return false;
            }
        }

        if (Teams.isSameTeam(entity)) {
            return false;
        }

        if (entity instanceof Player) {
            return attackPlayer.getCurrentValue();
        }
        if (entity instanceof MagmaCube || entity instanceof Slime) {
            return attackMobs.getCurrentValue();
        }
        if (entity instanceof EndCrystal) {
            return attackCrystals.getCurrentValue();
        }

        return false;
    }

    public void attackEntity(Entity entity) {
        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }
}