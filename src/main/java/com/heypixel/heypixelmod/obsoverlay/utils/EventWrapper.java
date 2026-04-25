package com.heypixel.heypixelmod.obsoverlay.utils;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventClientChat;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventMotion;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRender;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventRespawn;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ScreenEvent; // 导入新的事件类
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EventWrapper {

    @SubscribeEvent
    public void onRender(ScreenEvent.Render.Post e) {
        try {
            // 获取 PoseStack 和 partialTicks
            Naven.getInstance().getEventManager().call(new EventRender(e.getPartialTick(), e.getGuiGraphics().pose()));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onClientChat(ClientChatEvent e) {
        System.out.println("[DEBUG] 捕获到 Forge 聊天事件。");
        EventClientChat event = new EventClientChat(e.getMessage());
        Naven.getInstance().getEventManager().call(event);
        if (event.isCancelled()) {
            e.setCanceled(true);
        }
    }

    @EventTarget
    public void onMotion(EventMotion e) {
        if (e.getType() == EventType.PRE && Minecraft.getInstance().player.tickCount <= 1) {
            Naven.getInstance().getEventManager().call(new EventRespawn());
        }
    }
}