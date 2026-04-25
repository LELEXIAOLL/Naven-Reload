package com.heypixel.heypixelmod.obsoverlay.utils.musicplayer;

import net.minecraft.resources.ResourceLocation;

public class PlaylistItem {
    public final long id;
    public final String name;
    public final String coverUrl;
    public final int trackCount;
    public final String creator;

    // 用于渲染封面
    public transient ResourceLocation coverTexture = null;

    public PlaylistItem(long id, String name, String coverUrl, int trackCount, String creator) {
        this.id = id;
        this.name = name;
        this.coverUrl = coverUrl;
        this.trackCount = trackCount;
        this.creator = creator;
    }
}