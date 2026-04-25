package com.heypixel.heypixelmod.obsoverlay.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 本地文件加密工具类
 * 使用用户硬件ID (HWID) 作为动态AES密钥，确保配置文件只能在本机解密。
 */
public class FileCryptoUtils {

    private static SecretKeySpec secretKey;

    // 静态初始化块，在类加载时动态生成密钥
    static {
        try {
            // 1. 获取当前机器的 HWID
            String hwid = HWIDUtils.getHWID();
            if (hwid == null || hwid.isEmpty()) {
                throw new RuntimeException("无法获取 HWID，无法初始化文件加密密钥。");
            }

            // 2. 根据规则处理 HWID，确保其长度为32字节 (AES-256要求)
            StringBuilder keyBuilder = new StringBuilder(hwid);

            // 规则 a: 如果位数不足32，使用字符 '0' 在末尾补足
            while (keyBuilder.length() < 32) {
                keyBuilder.append('0');
            }

            // 规则 b: 如果位数超过32，则截取前面的32位
            if (keyBuilder.length() > 32) {
                keyBuilder.setLength(32);
            }

            // 3. 从处理后的字符串生成最终的密钥字节
            byte[] keyBytes = keyBuilder.toString().getBytes(StandardCharsets.UTF_8);

            // 4. 创建 AES 密钥规范
            secretKey = new SecretKeySpec(keyBytes, "AES");

        } catch (Exception e) {
            // 如果在初始化过程中发生任何错误，则抛出运行时异常，阻止程序继续运行
            throw new RuntimeException("基于 HWID 初始化文件加密密钥时失败", e);
        }
    }

    /**
     * 加密字符串 (AES/ECB/PKCS5Padding)
     * @param strToEncrypt 明文
     * @return Base64编码的密文
     */
    public static String encrypt(String strToEncrypt) {
        if (strToEncrypt == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解密字符串 (AES/ECB/PKCS5Padding)
     * @param strToDecrypt Base64编码的密文
     * @return 明文
     */
    public static String decrypt(String strToDecrypt) {
        if (strToDecrypt == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));
        } catch (Exception e) {
            // 解密失败通常意味着密钥不匹配（例如，文件被移动到另一台电脑）
            return null;
        }
    }
}