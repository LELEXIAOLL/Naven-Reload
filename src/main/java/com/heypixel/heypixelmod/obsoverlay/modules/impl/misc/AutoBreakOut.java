package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMoveInput;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventPacket;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.FloatValue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.ConcurrentLinkedQueue;

@ModuleInfo(
        name = "AutoBreakOut",
        description = "Automatically blinks and teleports out of cages.",
        category = Category.MISC
)
public class AutoBreakOut extends Module {

    public final FloatValue releaseTicks = ValueBuilder.create(this, "ReleaseAndReset Tick")
            .setDefaultFloatValue(80.0F).setFloatStep(1.0F).setMinFloatValue(10.0F).setMaxFloatValue(120.0F).build().getFloatValue();

    // 状态机
    private enum State {
        IDLE,
        WAITING_FOR_BREAK,
        LOCKED,
        WAITING_FOR_BLINK,
        BLINKING,
        DONE
    }

    private State currentState = State.IDLE;
    private int tickTimer = 0;

    private boolean movementLock = false;
    private boolean isBlinking = false;

    // 数据包队列
    private final ConcurrentLinkedQueue<Packet<?>> packets = new ConcurrentLinkedQueue<>();

    @Override
    public void onEnable() {
        this.reset();
        MinecraftForge.EVENT_BUS.register(this);
        ChatUtils.addChatMessage("§7[AutoBreakOut] §a已开启，自动循环模式。等待加入游戏...");
    }

    @Override
    public void onDisable() {
        releasePackets();
        this.reset();
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    private void reset() {
        this.movementLock = false;
        this.isBlinking = false;
        this.currentState = State.IDLE;
        this.tickTimer = 0;
        this.packets.clear();
    }

    /**
     * 核心方法：释放数据包并重置状态机
     */
    private void releaseAndReset() {
        releasePackets(); // 1. 释放 (内部已包含防死循环逻辑)
        reset();          // 2. 重置状态到 IDLE
        ChatUtils.addChatMessage("§7[AutoBreakOut] §a固定延迟结束，释放数据包并重置。");
    }

    // ------------------- 核心逻辑循环 -------------------

    @EventTarget
    public void onTick(EventRunTicks event) {
        if (mc.player == null || mc.level == null) return;

        if (mc.player.tickCount <= 1 && (movementLock || isBlinking)) {
            this.reset();
            return;
        }

        switch (currentState) {
            case WAITING_FOR_BREAK:
                tickTimer++;
                if (tickTimer >= 10) {
                    ChatUtils.addChatMessage("§7[AutoBreakOut] §e执行出笼，移动已锁定，等待 '3秒'...");
                    this.breakOut();
                    this.movementLock = true;
                    this.currentState = State.LOCKED;
                    this.tickTimer = 0;
                }
                break;

            case WAITING_FOR_BLINK:
                tickTimer++;
                if (tickTimer >= 40) {
                    this.isBlinking = true;
                    ChatUtils.addChatMessage("§7[AutoBreakOut] §a倒计时剩余1秒，开始拦截数据包 (Blink)...");
                    this.currentState = State.BLINKING;
                    this.tickTimer = 0;
                }
                break;

            case BLINKING:
                // 固定等待 50 tick (2.5秒) 后释放
                // 这里不再依赖聊天检测，确保时间固定
                tickTimer++;
                if (tickTimer >= releaseTicks.getCurrentValue()) {
                    this.releaseAndReset();
                }
                break;
        }
    }

    // ------------------- 聊天信息检测 -------------------

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!isEnabled()) return;

        String message = event.getMessage().getString();

        if (message.contains("加入了游戏")) {
            if (currentState == State.IDLE) {
                ChatUtils.addChatMessage("§7[AutoBreakOut] §a检测到加入游戏，准备执行...");
                this.currentState = State.WAITING_FOR_BREAK;
                this.tickTimer = 0;
            }
        }

        if (message.contains("3 秒")) {
            if (this.movementLock || currentState == State.LOCKED) {
                ChatUtils.addChatMessage("§7[AutoBreakOut] §a检测到倒计时(3s)，解除移动锁定。");
                this.movementLock = false;
                this.currentState = State.WAITING_FOR_BLINK;
                this.tickTimer = 0;
            }
        }

        // 【修改】移除了对 "单人模式" 的检测，完全依赖 BLINKING 状态的计时器
    }

    // ------------------- 网络包处理 -------------------

    @EventTarget
    public void onPacketReceive(EventPacket event) {
        if (event.getType() != EventType.RECEIVE) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket) {
            if (this.movementLock || this.isBlinking || this.currentState != State.IDLE) {
                this.reset();
                ChatUtils.addChatMessage("§7[AutoBreakOut] §e检测到世界切换，状态已重置。");
            }
        }
    }

    @EventTarget
    public void onPacketSend(EventPacket event) {
        if (event.getType() != EventType.SEND || !isBlinking) {
            return;
        }

        Packet<?> packet = event.getPacket();

        if (packet instanceof ServerboundChatPacket ||
                packet instanceof ServerboundChatCommandPacket ||
                packet instanceof ServerboundKeepAlivePacket ||
                packet instanceof ServerboundPongPacket ||
                packet instanceof ServerboundResourcePackPacket) {
            return;
        }

        if (packet instanceof ServerboundMovePlayerPacket movePacket) {
            if (movePacket.hasPosition() && movePacket.getY(0) <= 0.0f) {
                this.releaseAndReset();
                return;
            }
        }

        event.setCancelled(true);
        packets.add(packet);
    }

    // ------------------- 功能实现 -------------------

    @EventTarget
    public void onMoveInput(EventMoveInput event) {
        if (movementLock) {
            event.setForward(0);
            event.setStrafe(0);
            event.setJump(false);
            event.setSneak(false);
        }
    }

    private void breakOut() {
        if (mc.player == null || mc.level == null) return;

        BlockPos startPos = mc.player.blockPosition();
        for (int i = 1; i < 6; ++i) {
            BlockPos targetPos = startPos.above(i);
            BlockPos downPos = targetPos.below();

            boolean solidBelow = !mc.level.getBlockState(downPos).getCollisionShape(mc.level, downPos).isEmpty();

            if (solidBelow) {
                boolean feetAir = mc.level.getBlockState(targetPos).isAir();
                if (feetAir) {
                    boolean headAir = mc.level.getBlockState(targetPos.above()).isAir();
                    if (headAir) {
                        mc.player.moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
                        mc.player.setDeltaMovement(0, 0, 0);
                        return;
                    }
                }
            }
        }
    }

    private void releasePackets() {
        this.isBlinking = false;

        if (mc.player == null || mc.player.connection == null) return;

        while (!packets.isEmpty()) {
            Packet<?> packet = packets.poll();
            try {
                mc.player.connection.send(packet);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}