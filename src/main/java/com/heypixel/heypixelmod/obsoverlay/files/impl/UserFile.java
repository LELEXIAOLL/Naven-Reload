package com.heypixel.heypixelmod.obsoverlay.files.impl;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.files.ClientFile;
import com.heypixel.heypixelmod.obsoverlay.files.FileManager;
import com.heypixel.heypixelmod.obsoverlay.utils.FileCryptoUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;

public class UserFile extends ClientFile {

    private static String storedUsername = null;
    private static String storedPassword = null;

    public UserFile() {
        super("user.cfg");
        this.file = new File(FileManager.clientFolder, this.getFileName());
    }

    @Override
    public void read(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || !line.contains(":")) continue;

            String[] parts = line.split(":", 2);
            if (parts.length < 2) continue;

            String key = parts[0];
            String encryptedValue = parts[1];

            if ("USERNAME".equalsIgnoreCase(key)) {
                storedUsername = FileCryptoUtils.decrypt(encryptedValue);
            } else if ("PASSWORD".equalsIgnoreCase(key)) {
                storedPassword = FileCryptoUtils.decrypt(encryptedValue);
            }
        }
    }

    @Override
    public void save(BufferedWriter writer) throws IOException {
        // 此方法会在FileManager.save()时被调用
        if (storedUsername != null && storedPassword != null) {
            String encryptedUser = FileCryptoUtils.encrypt(storedUsername);
            String encryptedPass = FileCryptoUtils.encrypt(storedPassword);

            if (encryptedUser != null && encryptedPass != null) {
                writer.write("USERNAME:" + encryptedUser);
                writer.newLine();
                writer.write("PASSWORD:" + encryptedPass);
                writer.newLine();
            }
        }
    }

    /**
     * 核心方法：在登录成功后调用此方法来更新并保存凭据。
     * @param username 明文用户名
     * @param password 明文密码
     */
    public static void saveCredentials(String username, String password) {
        storedUsername = username;
        storedPassword = password;
        // 调用全局保存方法，这会触发所有文件的保存，包括我们刚刚更新的凭据
        Naven.getInstance().getFileManager().save();
    }

    /**
     * 提供给登录UI调用的方法，用于获取用户名
     * @return 已保存的用户名，或 null
     */
    public static String getStoredUsername() {
        return storedUsername;
    }

    /**
     * 提供给登录UI调用的方法，用于获取密码
     * @return 已保存的密码，或 null
     */
    public static String getStoredPassword() {
        return storedPassword;
    }
}