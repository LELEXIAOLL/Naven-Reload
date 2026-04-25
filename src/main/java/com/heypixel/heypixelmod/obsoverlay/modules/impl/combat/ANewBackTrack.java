package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.*;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.move.LongJump;
import com.heypixel.heypixelmod.obsoverlay.utils.PacketSnapshot;
import com.heypixel.heypixelmod.obsoverlay.utils.SmoothAnimationTimer;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
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
        name = "ANewBackTrack",
        description = "Delays packets to hit targets that are further away.",
        category = Category.COMBAT
)
public class ANewBackTrack extends Module {
    // --- Core Logic Settings ---
    private final FloatValue delay = ValueBuilder.create(this, "Delay (ms)")
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

    private final FloatValue minDistance = ValueBuilder.create(this, "Activation Range")
            .setDefaultFloatValue(0.0F)
            .setMinFloatValue(0.0F)
            .setMaxFloatValue(10.0F)
            .setFloatStep(0.01F)
            .build()
            .getFloatValue();

    private final BooleanValue onlyKillaura = ValueBuilder.create(this, "Only When Aura Enabled")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    // --- Visual Settings ---
    public BooleanValue renderEnabled = ValueBuilder.create(this, "Render")
            .setDefaultBooleanValue(true)
            .build()
            .getBooleanValue();

    public ModeValue renderMode = ValueBuilder.create(this, "Render Mode")
            .setVisibility(this.renderEnabled::getCurrentValue)
            .setDefaultModeIndex(0)
            .setModes("Naven")
            .build()
            .getModeValue();

    public FloatValue boxRed = ValueBuilder.create(this, "Box Red")
            .setVisibility(this.renderEnabled::getCurrentValue)
            .setDefaultFloatValue(0F).setFloatStep(5F).setMinFloatValue(0F).setMaxFloatValue(255F).build().getFloatValue();

    public FloatValue boxGreen = ValueBuilder.create(this, "Box Green")
            .setVisibility(this.renderEnabled::getCurrentValue)
            .setDefaultFloatValue(150F).setFloatStep(5F).setMinFloatValue(0F).setMaxFloatValue(255F).build().getFloatValue();

    public FloatValue boxBlue = ValueBuilder.create(this, "Box Blue")
            .setVisibility(this.renderEnabled::getCurrentValue)
            .setDefaultFloatValue(255F).setFloatStep(5F).setMinFloatValue(0F).setMaxFloatValue(255F).build().getFloatValue();

    // --- Internal Variables ---
    private final LinkedBlockingQueue<PacketSnapshot> packets = new LinkedBlockingQueue<>();
    private final SmoothAnimationTimer progress = new SmoothAnimationTimer(0.0F, 0.2F);
    private final Map<Integer, Vec3> serverPositions = new HashMap<>();
    private boolean working;
    private boolean foundTarget;
    public boolean alink = false; // To prevent conflicts with other modules like Velocity
    private static final int PROGRESS_COLOR_LIGHT_BLUE = 0xFF66CCFF;


    @EventTarget
    public void onPreTick(EventRunTicks event) {
        if (event.getType() != EventType.PRE || mc.player == null || mc.level == null) {
            return;
        }

        this.setSuffix((int) delay.getCurrentValue() + " ms");

        // Use the Aura class for the check as seen in your reference code
        if (onlyKillaura.getCurrentValue() && !Naven.getInstance().getModuleManager().getModule(Aura.class).isEnabled()) {
            clear();
            return;
        }

        float min = this.minDistance.getCurrentValue();
        float max = this.maxDistance.getCurrentValue(); // Use maxDistance for target finding
        double minSq = min * min;
        double maxSq = max * max;

        boolean found = false;
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player || (AntiBots.isBot(player) && AntiBots.isBedWarsBot(player))) {
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
        if (mc.player == null || mc.level == null || e.getType() != EventType.RECEIVE) {
            return;
        }

        if (working && !Naven.getInstance().getModuleManager().getModule(LongJump.class).isEnabled() && !alink) {
            Packet<?> packet = e.getPacket();
            Velocity velocityModule = (Velocity) Naven.getInstance().getModuleManager().getModule(Velocity.class);

            if (packet instanceof ClientboundSetEntityMotionPacket setEntityMotionPacket && setEntityMotionPacket.getId() == mc.player.getId()) {
                if (velocityModule.mode.isCurrentMode("GrimReduce")) {
                    clear();
                    alink = true;
                    return;
                }
            }

            if (packet instanceof ClientboundSetHealthPacket healthPacket && healthPacket.getHealth() <= 0) {
                clear();
                return;
            }

            if (packet instanceof ClientboundRespawnPacket || (packet instanceof ClientboundPlayerPositionPacket)) {
                clear();
                return;
            }

            // --- Entity Position Update Logic ---
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
                    newServerPos = currentServerPos.add(
                            (double) movePacket.getXa() / 4096.0,
                            (double) movePacket.getYa() / 4096.0,
                            (double) movePacket.getZa() / 4096.0
                    );
                }
            }

            if (targetEntity != null && newServerPos != null) {
                double distance = mc.player.getEyePosition().distanceTo(newServerPos);
                if (distance > maxDistance.getCurrentValue()) {
                    if (serverPositions.containsKey(targetEntity.getId())) {
                        Vec3 oldPos = serverPositions.get(targetEntity.getId());
                        if (mc.player.getEyePosition().distanceTo(oldPos) <= maxDistance.getCurrentValue()) {
                            clear();
                            return;
                        }
                    }
                    return;
                }
                serverPositions.put(targetEntity.getId(), newServerPos);
            }

            // Queue relevant packets and cancel them
            packets.add(new PacketSnapshot(packet, System.currentTimeMillis()));
            e.setCancelled(true);

        } else if (!working) { // Clear if we are not supposed to be working
            clear();
        }
    }

    private void clear() {
        while (!this.packets.isEmpty()) {
            try {
                PacketSnapshot packetSnapshot = this.packets.poll();
                if (packetSnapshot != null && packetSnapshot.packet != null && mc.getConnection() != null) {
                    // This custom event is specific to your client, assuming it exists.
                    EventBackrackPacket eventBackrackPacket = new EventBackrackPacket(packetSnapshot.packet);
                    Naven.getInstance().getEventManager().call(eventBackrackPacket);
                    if (eventBackrackPacket.cancelled) continue;

                    @SuppressWarnings("unchecked")
                    Packet<? super ClientPacketListener> clientPacket = (Packet<? super ClientPacketListener>) eventBackrackPacket.getPacket();
                    clientPacket.handle(mc.getConnection());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        serverPositions.clear();
        foundTarget = false;
        working = false;
        progress.target = 0.0f;
    }

    @EventTarget
    public void onRender2D(EventRender2D event) {
        renderTrackingProgress(event);
        releasePacket();
    }

    private void renderTrackingProgress(EventRender2D event) {
        if (!renderEnabled.getCurrentValue() || !this.working) {
            return;
        }

        float progressTarget = 0.0F;
        if (!packets.isEmpty()) {
            PacketSnapshot oldestPacket = packets.peek();
            if (oldestPacket != null) {
                long elapsedTime = System.currentTimeMillis() - oldestPacket.tick;
                progressTarget = Mth.clamp((float) elapsedTime / delay.getCurrentValue(), 0.0F, 1.0F);
            }
        }

        this.progress.target = progressTarget * 100.0F; // Animate from 0 to 100
        this.progress.update(true);

        int barX = mc.getWindow().getGuiScaledWidth() / 2 - 50;
        int barY = mc.getWindow().getGuiScaledHeight() / 2 + 15;
        float barWidth = 100.0F;
        float barHeight = 5.0F;
        float cornerRadius = 2.0F;

        String trackingText = "Tracking...";
        float textScale = 0.35f;
        float textWidth = Fonts.harmony.getWidth(trackingText, textScale);
        float textHeight = (float) Fonts.harmony.getHeight(false, textScale);
        float textX = barX + (barWidth - textWidth) / 2.0f;
        float textY = barY - textHeight - 2;

        Fonts.harmony.render(event.getGuiGraphics().pose(), trackingText, textX, textY, Color.WHITE, false, textScale);
        RenderUtils.drawRoundedRect(event.getGuiGraphics().pose(), barX, barY, barWidth, barHeight, cornerRadius, Integer.MIN_VALUE);

        if (this.progress.value > 0) {
            RenderUtils.drawRoundedRect(event.getGuiGraphics().pose(), barX, barY, this.progress.value, barHeight, cornerRadius, new Color(35, 255, 0, 255).getRGB());
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
                    EventBackrackPacket eventBackrackPacket = new EventBackrackPacket(packet);
                    Naven.getInstance().getEventManager().call(eventBackrackPacket);
                    if (eventBackrackPacket.cancelled) return true;

                    @SuppressWarnings("unchecked")
                    Packet<? super ClientPacketListener> clientPacket = (Packet<? super ClientPacketListener>) eventBackrackPacket.getPacket();
                    clientPacket.handle(mc.getConnection());
                    return true;
                }
            }
            return false;
        });

        if (packets.isEmpty() && !working) { // Clear server positions only when no longer needed
            serverPositions.clear();
        }
    }

    @EventTarget
    public void onRespawn(EventRespawn e) {
        clear();
        alink = false;
    }
}