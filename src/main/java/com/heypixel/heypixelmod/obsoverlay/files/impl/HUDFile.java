package com.heypixel.heypixelmod.obsoverlay.files.impl;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.files.ClientFile;
import com.heypixel.heypixelmod.obsoverlay.files.FileManager;
import com.heypixel.heypixelmod.obsoverlay.ui.hudeditor.HUDEditor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;

public class HUDFile extends ClientFile {
    private static final Logger logger = LogManager.getLogger(HUDFile.class);

    public HUDFile() {
        // 使用一个固定的文件名，例如 "hud.cfg"，并将其放在主客户端文件夹下
        super("hud.cfg");
        this.file = new File(FileManager.clientFolder, "hud.cfg");
    }

    /**
     * 从 hud.cfg 文件中读取HUD组件的位置信息
     *
     * @param reader BufferedReader
     * @throws IOException
     */
    @Override
    public void read(BufferedReader reader) throws IOException {
        HUDEditor hudEditor = Naven.getInstance().getHudEditor();
        if (hudEditor == null) {
            logger.error("HUDEditor is not initialized, cannot read HUD config.");
            return;
        }

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || !line.contains(":")) continue;

            try {
                String[] split = line.split(":", 3);
                if (split.length != 3) {
                    logger.warn("Invalid HUD config line format: {}", line);
                    continue;
                }

                String elementName = split[0];
                double x = Double.parseDouble(split[1]);
                double y = Double.parseDouble(split[2]);

                // 在 HUDEditor 中查找对应的元素并更新其位置
                HUDEditor.HUDElement element = hudEditor.getHUDElement(elementName);
                if (element != null) {
                    element.x = x;
                    element.y = y;
                } else {
                    logger.warn("Could not find HUD element with name: {}", elementName);
                }
            } catch (NumberFormatException e) {
                logger.error("Failed to parse coordinates in HUD config line: {}", line, e);
            } catch (Exception e) {
                logger.error("An unexpected error occurred while reading HUD config line: {}", line, e);
            }
        }
    }

    /**
     * 将当前HUD组件的位置信息保存到 hud.cfg 文件
     *
     * @param writer BufferedWriter
     * @throws IOException
     */
    @Override
    public void save(BufferedWriter writer) throws IOException {
        HUDEditor hudEditor = Naven.getInstance().getHudEditor();
        if (hudEditor == null) {
            logger.error("HUDEditor is not initialized, cannot save HUD config.");
            return;
        }

        // 遍历所有在HUDEditor中注册的元素
        for (HUDEditor.HUDElement element : hudEditor.getAllElements()) {
            if (element != null) {
                // 格式: elementName:x:y
                String line = String.format("%s:%.2f:%.2f\n", element.name, element.x, element.y);
                writer.write(line);
            }
        }
    }
}