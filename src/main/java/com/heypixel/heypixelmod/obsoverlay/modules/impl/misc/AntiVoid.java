package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventUpdate;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
// --- 新增导入 ---
import com.heypixel.heypixelmod.obsoverlay.modules.impl.combat.Aura;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.Scaffold;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.stream.StreamSupport;

@ModuleInfo(
        name = "AntiVoid",
        description = "Automatically saves you from the void using Scaffold and Timer.",
        category = Category.MISC
)
public class AntiVoid extends Module {

    private final FloatValue timerMultiplier = ValueBuilder.create(this, "Timer Multiplier")
            .setDefaultFloatValue(0.1F).setFloatStep(0.05F).setMinFloatValue(0.05F).setMaxFloatValue(1.0F).build().getFloatValue();

    private final BooleanValue simulation = ValueBuilder.create(this, "Simulation")
            .setDefaultBooleanValue(true).build().getBooleanValue();

    private final ModeValue simulationMode = ValueBuilder.create(this, "Mode")
            .setVisibility(simulation::getCurrentValue)
            .setDefaultModeIndex(1)
            .setModes("Fast", "Accurate")
            .build()
            .getModeValue();

    private final FloatValue timeoutSeconds = ValueBuilder.create(this, "Timeout (Seconds)")
            .setDefaultFloatValue(2.0F).setFloatStep(0.5F).setMinFloatValue(1.0F).setMaxFloatValue(5.0F).build().getFloatValue();

    private final BooleanValue logging = ValueBuilder.create(this, "Logging")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    private boolean isRescuing = false;
    private long rescueStartTime = 0L;
    private boolean hasGivenUp = false;

    // --- 新增: 模块实例引用 ---
    private Scaffold scaffoldModule;
    private Aura auraModule;

    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;

    @Override
    public void onEnable() {
        // 获取所有需要的模块实例
        scaffoldModule = (Scaffold) Naven.getInstance().getModuleManager().getModule(Scaffold.class);
        auraModule = (Aura) Naven.getInstance().getModuleManager().getModule(Aura.class);

        if (scaffoldModule == null || auraModule == null) {
            log("Error: A required module (Scaffold or Aura) was not found!");
            this.setEnabled(false);
            return;
        }
        this.hasGivenUp = false;
        reset(false);
    }

    @Override
    public void onDisable() {
        reset(true);
        this.hasGivenUp = false;
    }

    private void reset(boolean notify) {
        if (this.isRescuing) {
            if (notify) log("Reset.");
            Naven.TICK_TIMER = 1.0F;
            if (scaffoldModule != null && scaffoldModule.isEnabled()) {
                scaffoldModule.setEnabled(false);
            }
        }
        this.isRescuing = false;
        this.rescueStartTime = 0L;
    }

    @EventTarget
    public void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.level == null || scaffoldModule == null || auraModule == null) return;

        if (this.hasGivenUp) {
            if (mc.player.onGround()) this.hasGivenUp = false;
            return;
        }

        if (this.isRescuing) {
            if (!isOverVoid()) {
                log("Rescue successful!");
                reset(false);
                return;
            }
            long elapsedTime = System.currentTimeMillis() - this.rescueStartTime;
            long timeoutMillis = (long) (this.timeoutSeconds.getCurrentValue() * 1000);
            if (elapsedTime > timeoutMillis) {
                log("Rescue timed out! Giving up on this fall.");
                reset(true);
                this.hasGivenUp = true;
                return;
            }
        } else {
            // --- 核心改动: 添加战斗检测 ---
            // 如果 Aura 模块开启并且有目标，则不进行任何救援检测
            if (auraModule.isEnabled() && Aura.target != null) {
                return;
            }
            // -----------------------------

            if (mc.player.onGround()) return;
            if (scaffoldModule.isEnabled() || mc.player.getDeltaMovement().y >= 0) return;

            if (isOverVoid()) {
                if (!simulation.getCurrentValue() || willLandInVoid()) {
                    triggerRescue();
                }
            }
        }
    }

    private void triggerRescue() {
        log("Triggered!");
        this.isRescuing = true;
        this.rescueStartTime = System.currentTimeMillis();
        Naven.TICK_TIMER = this.timerMultiplier.getCurrentValue();
        scaffoldModule.setEnabled(true);
    }

    private void log(String message) {
        if (this.logging.getCurrentValue()) {
            ChatUtils.addChatMessage("[AntiVoid] " + message);
        }
    }

    // ... 其他方法保持不变 ...
    private boolean willLandInVoid() {
        if (mc.player == null || mc.level == null) return true;
        Vec3 position = mc.player.position();
        Vec3 velocity = mc.player.getDeltaMovement();
        for (int i = 0; i < 80; i++) {
            position = position.add(velocity);
            boolean landed;
            if (simulationMode.isCurrentMode("Accurate")) {
                landed = isLandingPositionSafe(position);
            } else {
                landed = isLandingPositionSafeSimple(position);
            }
            if (landed) return false;
            velocity = velocity.multiply(DRAG, DRAG, DRAG).subtract(0, GRAVITY, 0);
            if (position.y < mc.level.getMinBuildHeight()) return true;
        }
        return true;
    }

    private boolean isLandingPositionSafe(Vec3 predictedPos) {
        if (mc.level == null) return false;
        double playerWidthRadius = mc.player.getBbWidth() / 2.0;
        AABB landingArea = new AABB(
                predictedPos.x - playerWidthRadius, predictedPos.y - 0.1, predictedPos.z - playerWidthRadius,
                predictedPos.x + playerWidthRadius, predictedPos.y, predictedPos.z + playerWidthRadius
        );
        return StreamSupport.stream(BlockPos.betweenClosed(
                BlockPos.containing(landingArea.minX, landingArea.minY, landingArea.minZ),
                BlockPos.containing(landingArea.maxX, landingArea.maxY, landingArea.maxZ)
        ).spliterator(), false).anyMatch(pos -> !mc.level.getBlockState(pos).isAir());
    }

    private boolean isLandingPositionSafeSimple(Vec3 predictedPos) {
        if (mc.level == null) return false;
        BlockPos blockBelow = BlockPos.containing(predictedPos).below();
        return !mc.level.getBlockState(blockBelow).isAir();
    }

    private boolean isOverVoid() {
        if (mc.player == null || mc.level == null) return false;
        Vec3 top = mc.player.position().add(0, 0.1, 0);
        Vec3 bottom = new Vec3(top.x, mc.level.getMinBuildHeight() - 1.0, top.z);
        HitResult result = mc.level.clip(new ClipContext(top, bottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return result.getType() == HitResult.Type.MISS;
    }
}