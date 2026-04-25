package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.ui.screens.MusicPlayerScreen;
import com.heypixel.heypixelmod.obsoverlay.utils.musicplayer.MusicPlayerManager;
import net.minecraft.client.Minecraft;

@ModuleInfo(
        name = "MusicPlayer",
        description = "Opens the NeteaseCloudMusic player GUI", // 描述已更新
        category = Category.MISC
)
public class MusicPlayer extends Module {
    @Override
    public void onEnable() {
        if (Minecraft.getInstance().player != null) {
            MusicPlayerManager.setup();
            Minecraft.getInstance().setScreen(new MusicPlayerScreen(() -> this.setEnabled(false)));
        } else {
            this.setEnabled(false);
        }
    }
}