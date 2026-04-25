package com.heypixel.heypixelmod.obsoverlay.ui.notification;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleManager;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.render.HUD;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.LingDong.LingDongNotificationStyle;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.Naven.NavenNotificationStyle;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.Reload.ReloadNotificationStyle;
import com.heypixel.heypixelmod.obsoverlay.ui.notification.SouthSide.SouthSideNotificationStyle;

public class NotificationFactory {

    private static final NotificationStyle NAVEN_STYLE = new NavenNotificationStyle();
    private static final NotificationStyle NEW_STYLE = new SouthSideNotificationStyle();
    private static final NotificationStyle RELOAD_STYLE = new ReloadNotificationStyle();
    private static final NotificationStyle LINGDONG_STYLE = new LingDongNotificationStyle();

    public static NotificationStyle getStyle() {
        ModuleManager moduleManager = Naven.getInstance().getModuleManager();
        if (moduleManager != null) {
            Module module = moduleManager.getModule(HUD.class);
            if (module instanceof HUD) {
                HUD hud = (HUD) module;
                String styleName = hud.notificationStyle.getCurrentMode();

                switch (styleName) {
                    // 2. 添加 LingDong 样式的 case
                    case "LingDong":
                        return LINGDONG_STYLE;
                    case "Reload":
                        return RELOAD_STYLE;
                    case "SouthSide":
                        return NEW_STYLE;
                    case "Naven":
                    default:
                        return NAVEN_STYLE;
                }
            }
        }
        return NAVEN_STYLE;
    }
}