package com.heypixel.heypixelmod.obsoverlay.events.impl;

import com.heypixel.heypixelmod.obsoverlay.events.api.events.Event;
import com.heypixel.heypixelmod.obsoverlay.events.api.types.EventType;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

public class EventRenderTabOverlay implements Event {
   private final EventType type;
   private Component component;
   private PlayerInfo playerInfo; // 新增字段，用于存储玩家信息

   // 为 Header 和 Footer 保留的构造函数
   public EventRenderTabOverlay(EventType type, Component component) {
      this.type = type;
      this.component = component;
      this.playerInfo = null;
   }

   // --- 核心修复：新增一个能接收 PlayerInfo 的构造函数 ---
   public EventRenderTabOverlay(EventType type, Component component, PlayerInfo playerInfo) {
      this.type = type;
      this.component = component;
      this.playerInfo = playerInfo;
   }

   public EventType getType() {
      return this.type;
   }

   public Component getComponent() {
      return this.component;
   }

   public void setComponent(Component component) {
      this.component = component;
   }

   // --- 新增 Getter 方法，让模块可以获取到 PlayerInfo ---
   public PlayerInfo getPlayerInfo() {
      return this.playerInfo;
   }
}