// 文件路径: com/heypixel/heypixelmod/obsoverlay/utils/DelayManager.java
package com.heypixel.heypixelmod.obsoverlay.utils;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventHandlePacket;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRunTicks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class DelayManager {

    private static DelayManager instance;
    private final Minecraft mc = Minecraft.getInstance();

    public enum DelayModules {
        NONE,
        VELOCITY
    }

    private DelayModules delayModule = DelayModules.NONE;
    private long delayTicks = 0L;
    public final Deque<Packet<?>> delayedPacketQueue = new ConcurrentLinkedDeque<>();

    private DelayManager() {
        Naven.getInstance().getEventManager().register(this);
    }

    public static DelayManager getInstance() {
        if (instance == null) {
            instance = new DelayManager();
        }
        return instance;
    }

    private boolean shouldDelay(Packet<?> packet) {
        if (this.delayModule == DelayModules.NONE) {
            return false;
        }
        if (packet instanceof ClientboundKeepAlivePacket) {
            return false;
        }
        if (packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket) {
            stopDelay(false, this.delayModule);
            return false;
        }
        if (packet instanceof ClientboundEntityEventPacket eventPacket) {
            // --- 修复点 #1 ---
            // 1. 先获取实体对象
            Entity entity = eventPacket.getEntity(mc.level);
            // 2. 再进行判断
            if (entity != null && (!entity.equals(mc.player) || eventPacket.getEventId() != 2)) {
                return false;
            }
        }
        this.delayedPacketQueue.offer(packet);
        return true;
    }

    public boolean stopDelay(boolean start, DelayModules module) {
        if (start) {
            this.delayModule = module;
            this.delayTicks = 0L;
        } else {
            this.delayModule = DelayModules.NONE;
            ClientPacketListener connection = mc.getConnection();
            if (connection != null && !this.delayedPacketQueue.isEmpty()) {
                while (!this.delayedPacketQueue.isEmpty()) {
                    Packet<?> packet = this.delayedPacketQueue.poll();
                    if (packet != null) {
                        // --- 修复点 #2 ---
                        // 使用Minecraft原生的 handle 方法来处理接收到的数据包
                        // 需要进行类型转换来满足 handle 方法的参数要求
                        try {
                            @SuppressWarnings("unchecked") // 抑制类型转换警告，因为我们知道队列里都是ClientGamePacketListener的包
                            Packet<ClientGamePacketListener> clientPacket = (Packet<ClientGamePacketListener>) packet;
                            clientPacket.handle(connection);
                        } catch (Exception e) {
                            System.err.println("Error handling delayed packet: " + packet.getClass().getSimpleName());
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return this.delayModule != DelayModules.NONE;
    }

    public long getDelayTicks() {
        return this.delayTicks;
    }

    @EventTarget
    public void onPacket(EventHandlePacket event) {
        if (shouldDelay(event.getPacket())) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onTick(EventRunTicks event) {
        if (event.getType() == EventType.POST) {
            if (mc.player != null && mc.player.isDeadOrDying()) {
                stopDelay(false, this.delayModule);
            }
            if (this.delayModule != DelayModules.NONE) {
                this.delayTicks++;
            }
        }
    }
}