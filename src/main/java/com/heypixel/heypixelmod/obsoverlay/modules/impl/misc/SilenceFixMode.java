package com.heypixel.heypixelmod.obsoverlay.modules.impl.misc;

import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.ModeValue;

@ModuleInfo(
        name = "SilenceFixMode",
        description = "Fix some bugs in some game mode.",
        category = Category.MISC
)
public class SilenceFixMode extends Module {

    // 创建一个静态实例，以便于在其他地方访问这个模块
    public static SilenceFixMode instance;

    // 定义模式选项，使用 ModeValue
    public ModeValue silencemode = ValueBuilder.create(this, "Mode")
            .setDefaultModeIndex(0)
            .setModes(
                    "SkyWarsPerformance",
                    "SkyWars",
                    "BedWarsPerformance",
                    "BedWars",
                    "PVPPerformance",
                    "PVP",
                    "Dick",
                    "ChineseKongFu"
            )
            .build()
            .getModeValue();

    // 构造函数，初始化静态实例
    public SilenceFixMode() {
        instance = this;
    }

    /**
     * 重写 getSuffix 方法，以在 ArrayList 中显示当前模式
     *
     * @return 当前选择的模式名称
     */
    @Override
    public String getSuffix() {
        return this.silencemode.getCurrentMode();
    }
}