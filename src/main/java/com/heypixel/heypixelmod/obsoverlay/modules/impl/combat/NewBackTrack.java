package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.Naven ;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.*;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.LongJump;
import com.heypixel.heypixelmod.obsoverlay.utils.PacketSnapshot;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.SmoothAnimationTimer;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

@ModuleInfo(
        name = "NewBackTrack",
        description = "new",
        category = Category.COMBAT
)
public class NewBackTrack extends Module {
    // --- 核心逻辑设置 ---
    private final FloatValue delay = ValueBuilder.create(this, "Delay(ms)")
            .setDefaultFloatValue(500.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(10000.0F)
            .setFloatStep(1.0F)
            .build()
            .getFloatValue();
    private final FloatValue maxDistance = ValueBuilder.create(this, "Max Range")
            .setDefaultFloatValue(3.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(10.0F)
            .setFloatStep(0.01F)
            .build()
            .getFloatValue();
    private final FloatValue minDistance = ValueBuilder.create(this, "Range")
            .setDefaultFloatValue(0.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(10.0F)
            .setFloatStep(0.01F)
            .build()
            .getFloatValue();
    private final BooleanValue onlyKillaura = ValueBuilder.create(this, "OnlyKillaura")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    // --- 视觉效果设置 (从旧版BackTrack迁移) ---
    public BooleanValue btrender = ValueBuilder.create(this, "Render")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();
    public ModeValue btrendermode = ValueBuilder.create(this, "Render Mode")
            .setVisibility(this.btrender::getCurrentValue)
            .setDefaultModeIndex(0)
            .setModes("Naven") // 只保留Naven模式，因为它是要求的样式
            .build()
            .getModeValue();
    public FloatValue boxRed = ValueBuilder.create(this, "Box Red")
            .setVisibility(this.btrender::getCurrentValue)
            .setDefaultFloatValue(0F)
            .setFloatStep(5F)
            .setMinFloatValue(0F)
            .setMaxFloatValue(255F)
            .build()
            .getFloatValue();
    public FloatValue boxGreen = ValueBuilder.create(this, "Box Green")
            .setVisibility(this.btrender::getCurrentValue)
            .setDefaultFloatValue(150F)
            .setFloatStep(5F)
            .setMinFloatValue(0F)
            .setMaxFloatValue(255F)
            .build()
            .getFloatValue();
    public FloatValue boxBlue = ValueBuilder.create(this, "Box Blue")
            .setVisibility(this.btrender::getCurrentValue)
            .setDefaultFloatValue(255F)
            .setFloatStep(5F)
            .setMinFloatValue(0F)
            .setMaxFloatValue(255F)
            .build()
            .getFloatValue();

    // --- 内部变量 ---
    private final LinkedBlockingQueue<PacketSnapshot> packets = new LinkedBlockingQueue<>();
    private final SmoothAnimationTimer progress = new SmoothAnimationTimer(0.0F, 0.2F);
    private final Map<Integer, Vec3> serverPositions = new HashMap<>();
    private boolean working;
    private boolean foundTarget;
    public boolean alink = false;
    private static final int PROGRESS_COLOR_LIGHT_BLUE = 0xFF66CCFF;


    @EventTarget
    public void onPreTick(EventRunTicks event) {
        // Only operate on PRE and when world/player are available
        if (event.getType() != EventType.PRE) return;
        if (mc.player == null || mc.level == null) return;
        this.setSuffix((int) delay.getCurrentValue() + " ms");
        if (onlyKillaura.currentValue && !Naven.getInstance().getModuleManager().getModule(Aura.class).isEnabled()) {
            clear();
            return;
        }

        float min = 0.0f;
        float max = this.minDistance.getCurrentValue();
        double minSq = (double) min * (double) min;
        double maxSq = (double) max * (double) max;

        boolean found = false;
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player || AntiBots.isBot(player) && AntiBots.isBedWarsBot(player)) {
                continue;
            }
            double distSq = mc.player.distanceToSqr(player.getX(), player.getY(), player.getZ());
            if (distSq >= minSq && distSq <= maxSq) {
                found = true;
                break;
            }
        }

        this.foundTarget = found;
        this.working = this.foundTarget;
    }

    @EventTarget
    public void onPacket(EventPacket e) {
        if (mc.player != null && mc.level != null) {
            if (e.getType() == EventType.RECEIVE) {
                if (working && !Naven.getInstance().getModuleManager().getModule(LongJump.class).isEnabled() && !alink) {
                    Packet<?> packet = e.getPacket();
                    Velocity velocityModule = (Velocity) Naven.getInstance().getModuleManager().getModule(Velocity.class);
                    if (packet instanceof ClientboundSetEntityMotionPacket setEntityMotionPacket) {
                        if (setEntityMotionPacket.getId() != mc.player.getId()) return;
                        if (velocityModule.mode.isCurrentMode("GrimReduce")) {
                            clear();
                            alink = true;
                            return;
                        }
                    }
                    if (packet instanceof ClientboundSetHealthPacket healthPacket) {
                        if (healthPacket.getHealth() <= 0) {
                            clear();
                        }
                        return;
                    }
                    if (packet instanceof ClientboundRespawnPacket) {
                        clear();
                        return;
                    }
                    if (packet instanceof ClientboundPlayerPositionPacket playerPositionPacket) {
                        if (playerPositionPacket.getId() == mc.player.getId()) {
                            clear();
                            return;
                        }
                    }

                    // --- 实体位置更新逻辑 (核心) ---
                    Entity targetEntity = null;
                    Vec3 newServerPos = null;

                    if (packet instanceof ClientboundTeleportEntityPacket teleportPacket) {
                        targetEntity = mc.level.getEntity(teleportPacket.getId());
                        if (targetEntity instanceof Player && targetEntity != mc.player) {
                            newServerPos = new Vec3(teleportPacket.getX(), teleportPacket.getY(), teleportPacket.getZ());
                        }
                    } else if (packet instanceof ClientboundMoveEntityPacket movePacket) {
                        targetEntity = movePacket.getEntity(mc.level);
                        if (targetEntity instanceof Player && targetEntity != mc.player) {
                            Vec3 currentServerPos = serverPositions.getOrDefault(targetEntity.getId(), targetEntity.position());
                            double newX = currentServerPos.x + (movePacket.getXa() / 4096.0);
                            double newY = currentServerPos.y + (movePacket.getYa() / 4096.0);
                            double newZ = currentServerPos.z + (movePacket.getZa() / 4096.0);
                            newServerPos = new Vec3(newX, newY, newZ);
                        }
                    }

                    if (targetEntity != null && newServerPos != null) {
                        Vec3 eyePos = mc.player.getEyePosition();
                        double distance = eyePos.distanceTo(newServerPos);

                        if (distance > maxDistance.getCurrentValue()) {
                            if (serverPositions.containsKey(targetEntity.getId())) {
                                Vec3 oldPos = serverPositions.get(targetEntity.getId());
                                if (eyePos.distanceTo(oldPos) <= maxDistance.getCurrentValue()) {
                                    clear();
                                    return;
                                }
                            }
                            return;
                        }
                        serverPositions.put(targetEntity.getId(), newServerPos);
                    }


                    if (packet instanceof ClientboundPlayerPositionPacket || packet instanceof ClientboundMoveEntityPacket || packet instanceof ClientboundTeleportEntityPacket || packet instanceof ClientboundPingPacket || packet instanceof ClientboundSetEntityMotionPacket) {
                        packets.add(new PacketSnapshot(packet, System.currentTimeMillis()));
                        e.setCancelled(true);
                    }
                } else {
                    clear();
                }
            }
        }
    }

    private void clear() {
        while (!this.packets.isEmpty()) {
            try {
                Packet<?> packet = this.packets.poll().packet;
                if (packet != null && mc.getConnection() != null) {
                    EventBackrackPacket eventBackrackPacket = new EventBackrackPacket(packet);
                    Naven.getInstance().getEventManager().call(eventBackrackPacket);
                    if (eventBackrackPacket.cancelled) return;
                    Packet<? super ClientPacketListener> clientPacket = (Packet<? super ClientPacketListener>) eventBackrackPacket.getPacket();
                    clientPacket.handle(mc.getConnection());
                }
            } catch (Exception var3) {
                var3.printStackTrace();
            }
        }
        serverPositions.clear();
        foundTarget = false;
        working = false;
        progress.target = 0.0f;
    }


    @EventTarget
    public void onRender(EventRender e) {
        if (!btrender.getCurrentValue() || serverPositions.isEmpty()) {
            return;
        }

        PoseStack stack = e.getPMatrixStack();
        stack.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionShader);

        RenderUtils.applyRegionalRenderOffset(stack);

        float red = boxRed.getCurrentValue() / 255.0f;
        float green = boxGreen.getCurrentValue() / 255.0f;
        float blue = boxBlue.getCurrentValue() / 255.0f;
        RenderSystem.setShaderColor(red, green, blue, 0.3f);

        for (Map.Entry<Integer, Vec3> entry : serverPositions.entrySet()) {
            Entity entity = mc.level.getEntity(entry.getKey());
            if (entity instanceof Player) {
                Vec3 serverPos = entry.getValue();
                AABB bb = entity.getBoundingBox()
                        .move(-entity.getX(), -entity.getY(), -entity.getZ())
                        .move(serverPos.x, serverPos.y, serverPos.z);
                RenderUtils.drawSolidBox(bb, stack);
            }
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        stack.popPose();
    }

    @EventTarget
    public void onRender2d(EventRender2D event) {
        renderTrackingProgress(event);
        releasePacket();
    }

    private void renderTrackingProgress(EventRender2D event) {
        if (!btrender.getCurrentValue() || !this.working) {
            return;
        }

        float progressTarget = 0.0F;
        if (!packets.isEmpty()) {
            PacketSnapshot oldestPacket = packets.peek();
            if (oldestPacket != null) {
                long elapsedTime = System.currentTimeMillis() - oldestPacket.tick;
                progressTarget = Mth.clamp((float)elapsedTime / delay.getCurrentValue() * 100.0F, 0.0F, 100.0F);
            }
        }

        this.progress.target = progressTarget;
        this.progress.update(true);

        int barX = mc.getWindow().getGuiScaledWidth() / 2 - 50;
        int barY = mc.getWindow().getGuiScaledHeight() / 2 + 15;
        float barWidth = 100.0F;
        float barHeight = 5.0F;
        float cornerRadius = 2.0F;

        String trackingText = "Tracking...";
        float textScale = 0.35f;
        float textWidth = Fonts.harmony.getWidth(trackingText, textScale);
        float textHeight = (float)Fonts.harmony.getHeight(false, textScale);
        float textX = barX + (barWidth - textWidth) / 2.0f;
        float textY = barY - textHeight - 2;

        Fonts.harmony.render(
                event.getGuiGraphics().pose(),
                trackingText,
                textX,
                textY,
                Color.WHITE,
                false,
                textScale
        );

        RenderUtils.drawRoundedRect(event.getGuiGraphics().pose(), (float)barX, (float)barY, barWidth, barHeight, cornerRadius, Integer.MIN_VALUE);
        if (this.progress.value > 0) {
            RenderUtils.drawRoundedRect(event.getGuiGraphics().pose(), (float)barX, (float)barY, this.progress.value, barHeight, cornerRadius, PROGRESS_COLOR_LIGHT_BLUE);
        }
    }


    @Override
    public void onDisable() {
        clear();
        alink = false;
    }

    public void releasePacket() {
        if (mc.player == null) return;
        this.packets.removeIf(it -> {
            if (System.currentTimeMillis() - it.tick >= delay.getCurrentValue()) {
                Packet<?> packet = it.packet;
                if (packet != null && mc.getConnection() != null) {
                    // 更新 serverPositions
                    updateServerPosOnRelease(packet);

                    EventBackrackPacket eventBackrackPacket = new EventBackrackPacket(packet);
                    Naven.getInstance().getEventManager().call(eventBackrackPacket);
                    if (eventBackrackPacket.cancelled) return true;
                    Packet<? super ClientPacketListener> clientPacket = (Packet<? super ClientPacketListener>) eventBackrackPacket.getPacket();
                    clientPacket.handle(mc.getConnection());
                    return true;
                }
            }
            return false;
        });

        // 清理过期的serverPositions
        if (packets.isEmpty()) {
            serverPositions.clear();
        }
    }

    private void updateServerPosOnRelease(Packet<?> packet) {
        if (mc.level == null) return;

        Entity targetEntity = null;
        Vec3 newServerPos = null;

        if (packet instanceof ClientboundTeleportEntityPacket teleportPacket) {
            targetEntity = mc.level.getEntity(teleportPacket.getId());
            if (targetEntity instanceof Player) {
                newServerPos = new Vec3(teleportPacket.getX(), teleportPacket.getY(), teleportPacket.getZ());
            }
        } else if (packet instanceof ClientboundMoveEntityPacket movePacket) {
            targetEntity = movePacket.getEntity(mc.level);
            if (targetEntity instanceof Player) {
                // 当数据包被真正处理时，实体的客户端位置应该已经更新了
                // 所以我们直接用实体的新位置
                newServerPos = targetEntity.position().add(
                        (double)movePacket.getXa() / 4096.0,
                        (double)movePacket.getYa() / 4096.0,
                        (double)movePacket.getZa() / 4096.0
                );
            }
        }

        if (targetEntity != null && newServerPos != null && serverPositions.containsKey(targetEntity.getId())) {
            serverPositions.put(targetEntity.getId(), newServerPos);
        }
    }

    @EventTarget
    public void onRespawn(EventRespawn e) {
        clear();
        alink = false;
    }
}