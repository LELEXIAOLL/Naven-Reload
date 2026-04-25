package com.heypixel.heypixelmod.obsoverlay.events.impl;

import com.heypixel.heypixelmod.obsoverlay.events.api.events.callables.EventCancellable;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

public class EventBackrackPacket extends EventCancellable {
    private Packet<?> packet;

    public Packet<?> getPacket() {
        return this.packet;
    }

    public void setPacket(Packet<?> packet) {
        this.packet = packet;
    }

    public EventBackrackPacket(Packet<?> packet) {
        this.packet = packet;
    }
}
