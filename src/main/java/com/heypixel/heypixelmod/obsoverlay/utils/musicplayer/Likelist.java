package com.heypixel.heypixelmod.obsoverlay.utils.musicplayer;

import net.minecraft.resources.ResourceLocation;
import java.util.concurrent.TimeUnit;

public class Likelist {
    public final long id;
    public final String name;
    public final String artist;
    public final String coverUrl;
    public final long duration;

    public transient ResourceLocation coverTexture = null;

    public Likelist(long id, String name, String artist, String coverUrl, long duration) {
        this.id = id;
        this.name = name;
        this.artist = artist;
        this.coverUrl = coverUrl;
        this.duration = duration;
    }

    public String getFormattedDuration() {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format("%02d:%02d", minutes, seconds);
    }
}