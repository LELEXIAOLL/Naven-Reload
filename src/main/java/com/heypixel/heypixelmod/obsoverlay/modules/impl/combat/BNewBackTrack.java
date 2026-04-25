package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.*;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.RenderUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.SmoothAnimationTimer;
import com.heypixel.heypixelmod.obsoverlay.utils.TimerUtils;
import com.heypixel.heypixelmod.obsoverlay.utils.renderer.Fonts;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.concurrent.LinkedBlockingQueue;

@ModuleInfo(name = "BNewBackTrack", description = "111114455511114444", category = Category.COMBAT)
public class BNewBackTrack extends Module {

    // === Settings ===
    ModeValue targetMode = ValueBuilder.create(this, "TargetMode").setModes("Range", "Attack").setDefaultModeIndex(1).build().getModeValue();
    ModeValue renderMode = ValueBuilder.create(this, "RenderMode").setModes("Box", "Wireframe", "None").setDefaultModeIndex(0).build().getModeValue();
    FloatValue range = ValueBuilder.create(this, "Range").setDefaultFloatValue(3.0f).setFloatStep(1.0f).setMinFloatValue(0.1f).setMaxFloatValue(15.0f).build().getFloatValue();
    public BooleanValue sendVelocity = ValueBuilder.create(this, "DelayVelocity").setDefaultBooleanValue(true).build().getBooleanValue();
    public BooleanValue autoReleaseAtMax = ValueBuilder.create(this, "AutoRelease").setDefaultBooleanValue(true).build().getBooleanValue();
    public FloatValue maxPackets = ValueBuilder.create(this, "MaxPackets").setDefaultFloatValue(55.0f).setMinFloatValue(10.0f).setMaxFloatValue(200.0f).setFloatStep(1.0f).build().getFloatValue();
    public BooleanValue releaseOnHurt = ValueBuilder.create(this, "ReleaseInHurtTime").setDefaultBooleanValue(false).build().getBooleanValue();
    public FloatValue hurtCountThreshold = ValueBuilder.create(this, "HurtCount").setDefaultFloatValue(1.0f).setMinFloatValue(1.0f).setMaxFloatValue(10.0f).setFloatStep(1.0f).setVisibility(() -> this.releaseOnHurt.getCurrentValue()).build().getFloatValue();

    // Render Color
    public FloatValue boxColorRed = ValueBuilder.create(this, "Box Red").setDefaultFloatValue(0.3f).setMinFloatValue(0.0f).setMaxFloatValue(1.0f).setFloatStep(0.01f).setVisibility(() -> this.renderMode.isCurrentMode("Box")).build().getFloatValue();
    public FloatValue boxColorGreen = ValueBuilder.create(this, "Box Green").setDefaultFloatValue(0.13f).setMinFloatValue(0.0f).setMaxFloatValue(1.0f).setFloatStep(0.01f).setVisibility(() -> this.renderMode.isCurrentMode("Box")).build().getFloatValue();
    public FloatValue boxColorBlue = ValueBuilder.create(this, "Box Blue").setDefaultFloatValue(0.58f).setMinFloatValue(0.0f).setMaxFloatValue(1.0f).setFloatStep(0.01f).setVisibility(() -> this.renderMode.isCurrentMode("Box")).build().getFloatValue();
    public FloatValue wireframeWidth = ValueBuilder.create(this, "Wireframe Width").setDefaultFloatValue(1.5f).setMinFloatValue(0.5f).setMaxFloatValue(5.0f).setFloatStep(0.1f).setVisibility(() -> this.renderMode.isCurrentMode("Wireframe")).build().getFloatValue();

    // === Fields ===
    private final SmoothAnimationTimer progress = new SmoothAnimationTimer(0.0f, 0.2f);
    private static final int YELLOW_COLOR = new Color(255, 255, 0, 255).getRGB();

    private final LinkedBlockingQueue<Packet<PacketListener>> packets = new LinkedBlockingQueue<>();
    private Entity entity = null;
    private Entity oldEntity = null;
    private LocalPlayer fakePlayer = null;
    private TimerUtils timer = new TimerUtils();
    private boolean hasAttacked = false;
    private TimerUtils attackLogTimer = new TimerUtils();
    private int hurtCount = 0;
    private int lastHurtTime = 0;
    private Vector4f blurMatrix;

    private boolean isBlinkEnabled() {
        Module blinkModule = Naven.getInstance().getModuleManager().getModule("Blink");
        return blinkModule != null && blinkModule.isEnabled();
    }

    @Override
    public void onEnable() {
        this.packets.clear();
        this.fakePlayer = null;
        this.entity = null;
        this.progress.value = 0.0f;
        this.progress.target = 0.0f;
        this.hasAttacked = false;
        this.hurtCount = 0;
        this.lastHurtTime = 0;
        this.blurMatrix = null;
    }

    @Override
    public void onDisable() {
        this.send();
    }

    @EventTarget
    public void onAttack(EventClick event) {
        if (this.isBlinkEnabled()) {
            return;
        }
        if (Aura.target != null) {
            if (this.oldEntity != Aura.target) {
                this.send();
            }
            this.entity = Aura.target;
            this.hasAttacked = true;
            if (this.fakePlayer == null) {
                this.fakePlayer = mc.gameMode.createPlayer(mc.level, new StatsCounter(), new ClientRecipeBook());
                this.fakePlayer.moveTo(this.entity.getX(), this.entity.getY(), this.entity.getZ(), this.entity.getYRot(), this.entity.getXRot());
                this.fakePlayer.setPose(this.entity.getPose());
                this.fakePlayer.setBoundingBox(this.entity.getBoundingBox());
            }
            this.oldEntity = Aura.target;
            if (this.attackLogTimer.hasTimePassed(500L)) {
                this.attackLogTimer.reset();
            }
        }
    }

    @EventTarget
    public void onUpdate(EventRunTicks event) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            return;
        }
        if (this.isBlinkEnabled()) {
            return;
        }
        if (this.releaseOnHurt.getCurrentValue()) {
            int currentHurtTime = mc.player.hurtTime;
            if (currentHurtTime > this.lastHurtTime && currentHurtTime > 0) {
                ++this.hurtCount;
                if (this.hurtCount >= (int) this.hurtCountThreshold.getCurrentValue()) {
                    this.send();
                    this.hurtCount = 0;
                }
            }
            this.lastHurtTime = currentHurtTime;
        }

        if (this.entity != null) {
            if (Aura.target == null) {
                this.entity = null;
                this.fakePlayer = null;
                this.hasAttacked = false;
            }
        }
        if (this.fakePlayer != null && this.entity != null) {
            float maxRange = this.range.getCurrentValue() + 1.0f;

            if (mc.player.distanceTo(this.fakePlayer) > maxRange && this.timer.hasTimePassed(50L)) {
                this.timer.reset();
                this.send();
            }

            // Auto release at max packets logic
            if (this.hasAttacked && this.packets.size() >= (int) this.maxPackets.getCurrentValue() && this.autoReleaseAtMax.getCurrentValue()) {
                this.send();
            }
        } else {
            this.send();
        }
    }

    @EventTarget
    public void onPacket(EventPacket event) {
        if (this.isBlinkEnabled()) {
            return;
        }
        if (EventType.RECEIVE == event.getType()) {
            Packet<?> packet = event.getPacket();
            if (mc.player == null || mc.level == null) {
                return;
            }
            if (this.entity != null) {
                ClientboundTeleportEntityPacket teleportEntityPacket;
                ClientboundMoveEntityPacket moveEntityPacket;
                ClientboundSetEntityMotionPacket entityMotionPacket;

                if (packet instanceof ClientboundPingPacket) {
                    event.setCancelled(true);
                    this.packets.add((Packet<PacketListener>) packet);

                    if (this.hasAttacked && this.packets.size() >= (int) this.maxPackets.getCurrentValue() && this.autoReleaseAtMax.getCurrentValue()) {
                        this.send();
                    }
                }
                if (packet instanceof ClientboundPlayerPositionPacket) {
                    // 收到位置矫正时重置
                    this.entity = null;
                    this.fakePlayer = null;
                    this.hasAttacked = false;
                    this.hurtCount = 0;
                    this.send(); // 确保队列清空
                }
                if (packet instanceof ClientboundSetEntityMotionPacket && (entityMotionPacket = (ClientboundSetEntityMotionPacket) packet).getId() == mc.player.getId() && this.sendVelocity.getCurrentValue()) {
                    event.setCancelled(true);
                    this.packets.add((Packet<PacketListener>) packet);

                    if (this.hasAttacked && this.packets.size() >= (int) this.maxPackets.getCurrentValue() && this.autoReleaseAtMax.getCurrentValue()) {
                        this.send();
                    }
                }
                if (packet instanceof ClientboundMoveEntityPacket && (moveEntityPacket = (ClientboundMoveEntityPacket) packet).getEntity((Level) mc.level) == this.entity) {
                    event.setCancelled(true);
                    this.packets.add((Packet<PacketListener>) packet);

                    if (this.hasAttacked && this.packets.size() >= (int) this.maxPackets.getCurrentValue() && this.autoReleaseAtMax.getCurrentValue()) {
                        this.send();
                    }
                    if (this.entity != null && !this.entity.isPassenger() && moveEntityPacket.hasPosition()) {
                        VecDeltaCodec vecdeltacodec = this.fakePlayer.getPositionCodec();
                        Vec3 vec3 = vecdeltacodec.decode((long) moveEntityPacket.getXa(), (long) moveEntityPacket.getYa(), (long) moveEntityPacket.getZa());
                        vecdeltacodec.setBase(vec3);
                        this.fakePlayer.moveTo(vec3.x, vec3.y, vec3.z, (float) moveEntityPacket.getyRot(), (float) moveEntityPacket.getxRot());
                    }
                }
                if (packet instanceof ClientboundTeleportEntityPacket && (teleportEntityPacket = (ClientboundTeleportEntityPacket) packet).getId() == this.entity.getId()) {
                    event.setCancelled(true);
                    this.packets.add((Packet<PacketListener>) packet);

                    if (this.hasAttacked && this.packets.size() >= (int) this.maxPackets.getCurrentValue() && this.autoReleaseAtMax.getCurrentValue()) {
                        this.send();
                    }
                    if (this.entity != null) {
                        double d0 = teleportEntityPacket.getX();
                        double d1 = teleportEntityPacket.getY();
                        double d2 = teleportEntityPacket.getZ();
                        this.fakePlayer.syncPacketPositionCodec(d0, d1, d2);
                        if (!this.entity.isPassenger()) {
                            float f = (float) (teleportEntityPacket.getyRot() * 360) / 256.0f;
                            float f1 = (float) (teleportEntityPacket.getxRot() * 360) / 256.0f;
                            this.fakePlayer.lerpTo(d0, d1, d2, f, f1, 3, true);
                            this.fakePlayer.setOnGround(teleportEntityPacket.isOnGround());
                        }
                    }
                }
                if (packet instanceof ClientboundSetEntityMotionPacket && (entityMotionPacket = (ClientboundSetEntityMotionPacket) packet).getId() == this.entity.getId()) {
                    this.fakePlayer.setDeltaMovement((double) entityMotionPacket.getXa() / 8000.0, (double) entityMotionPacket.getYa() / 8000.0, (double) entityMotionPacket.getZa() / 8000.0);
                }
            }
        }
    }

    @EventTarget
    public void onWorldChange(EventMotion event) {
        if (mc.player == null) {
            return;
        }
        if (mc.player.tickCount <= 1 && EventType.PRE == event.getType()) {
            this.send();
            this.hurtCount = 0;
        }
    }

    @EventTarget
    public void onShader(EventShader e) {
        if (this.blurMatrix != null) {
            RenderUtils.drawRoundedRect(e.getStack(), this.blurMatrix.x(), this.blurMatrix.y(), this.blurMatrix.z(), this.blurMatrix.w(), 3.0f, 0x40000000);
        }
    }

    @EventTarget
    public void onRender2D(EventRender2D e) {
        if (this.isBlinkEnabled()) {
            return;
        }
        if (this.entity != null) {
            this.progress.target = Mth.clamp((float) ((float) this.packets.size() / (float) ((int) this.maxPackets.getCurrentValue()) * 100.0f), (float) 0.0f, (float) 100.0f);
            this.progress.update(true);

            int baseX = mc.getWindow().getGuiScaledWidth() / 2 - 50;
            int baseY = mc.getWindow().getGuiScaledHeight() / 2 + 15;
            int x = (int) ((float) baseX);
            int y = (int) ((float) baseY);

            float barWidth = 100.0F;

            String trackingText = "Tracking...";
            float textScale = 0.35f;
            float textWidth = Fonts.harmony.getWidth(trackingText, textScale);
            float textHeight = (float) Fonts.harmony.getHeight(false, textScale);

            float textX = x + (barWidth - textWidth) / 2.0f;
            float textY = y - textHeight - 2;

            Fonts.harmony.render(
                    e.getStack(),
                    trackingText,
                    textX,
                    textY,
                    Color.WHITE,
                    false,
                    textScale
            );

            RenderUtils.drawRoundedRect(e.getStack(), (float) x, (float) y, barWidth, 5.0F, 2.0F, Integer.MIN_VALUE);
            RenderUtils.drawRoundedRect(e.getStack(), (float) x, (float) y, this.progress.value, 5.0F, 2.0F, YELLOW_COLOR);
        }
    }

    @EventTarget
    public void onRender(EventRender e) {
        if (this.isBlinkEnabled() || this.renderMode.isCurrentMode("None") || this.entity == null || this.fakePlayer == null) {
            return;
        }
        PoseStack stack = e.getPMatrixStack();
        float partialTicks = e.getRenderPartialTicks();
        stack.pushPose();
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);
        GL11.glEnable(2848);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderUtils.applyRegionalRenderOffset(stack);

        double motionX = this.entity.getX() - this.entity.xo;
        double motionY = this.entity.getY() - this.entity.yo;
        double motionZ = this.entity.getZ() - this.entity.zo;

        Vec3 backtrackPos = new Vec3(this.fakePlayer.getX(), this.fakePlayer.getY(), this.fakePlayer.getZ());

        AABB boundingBox = this.entity.getBoundingBox()
                .move(-motionX, -motionY, -motionZ)
                .move((double) partialTicks * motionX, (double) partialTicks * motionY, (double) partialTicks * motionZ)
                .move(backtrackPos.x - this.entity.getX(), backtrackPos.y - this.entity.getY(), backtrackPos.z - this.entity.getZ());

        if (this.renderMode.isCurrentMode("Box")) {
            RenderSystem.setShaderColor(this.boxColorRed.getCurrentValue(), (float) this.boxColorGreen.getCurrentValue(), (float) this.boxColorBlue.getCurrentValue(), (float) 0.5);
            RenderUtils.drawSolidBox(boundingBox, stack);
        } else if (this.renderMode.isCurrentMode("Wireframe")) {
            GL11.glLineWidth((float) this.wireframeWidth.getCurrentValue());
            RenderSystem.setShaderColor((float) 0.3f, (float) 0.13f, (float) 0.58f, (float) 0.8f);
            RenderUtils.drawOutlinedBox(boundingBox, stack);
        }
        RenderSystem.setShaderColor((float) 1.0f, (float) 1.0f, (float) 1.0f, (float) 1.0f);
        GL11.glDisable(3042);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(2848);
        stack.popPose();
    }

    private void send() {
        this.progress.target = 0.0f;
        this.progress.value = 0.0f;
        this.fakePlayer = null;
        this.entity = null;
        this.hasAttacked = false;
        this.hurtCount = 0;
        while (!this.packets.isEmpty()) {
            Packet<PacketListener> packet = this.packets.poll();
            packet.handle((PacketListener) mc.getConnection());
        }
    }
}