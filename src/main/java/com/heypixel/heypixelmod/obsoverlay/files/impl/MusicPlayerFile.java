package com.heypixel.heypixelmod.obsoverlay.files.impl;

import com.heypixel.heypixelmod.obsoverlay.files.ClientFile;
import com.heypixel.heypixelmod.obsoverlay.utils.MusicManager;
import com.heypixel.heypixelmod.obsoverlay.utils.musicplayer.LoginResult;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

public class MusicPlayerFile extends ClientFile {

    public MusicPlayerFile() {
        // 定义配置文件的名称
        super("music_player.cfg");
    }

    @Override
    public void read(BufferedReader reader) throws IOException {
        // 从文件中读取一行作为Cookie
        String line = reader.readLine();
        // 如果行不为null，则更新到MusicManager；否则保持为空字符串
        MusicManager.cookie = (line != null) ? line : "";
    }

    @Override
    public void save(BufferedWriter writer) throws IOException {
        // 将MusicManager中存储的Cookie写入文件
        // 如果cookie是null，则写入空字符串，保证文件内容有效
        writer.write(MusicManager.cookie != null ? MusicManager.cookie : "");
        writer.newLine(); // 写入一个换行符
    }
}