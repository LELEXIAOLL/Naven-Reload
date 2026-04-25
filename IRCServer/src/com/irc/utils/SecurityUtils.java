package com.irc.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 安全工具类
 * 包含：
 * 1. 基于预共享密钥的AES-256对称加密/解密。
 * 2. SHA-256 哈希计算功能。
 */
public class SecurityUtils {

    // --- 关键：预共享的AES密钥 ---
    private static final String SHARED_KEY_STRING = "人生有梦各自精彩";

    private static SecretKey sharedKey;

    /**
     * 初始化安全模块。
     * 将硬编码的密钥字符串转换成一个可用的 SecretKey 对象。
     */
    public static void init() {
        byte[] keyBytes = SHARED_KEY_STRING.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("错误：预共享密钥必须是32字节长，以用于AES-256加密。");
        }
        sharedKey = new SecretKeySpec(keyBytes, "AES");
        LogManager.info("安全模块已初始化，使用预共享AES密钥。");
    }

    /**
     * 使用共享密钥加密数据。
     * @param plainText 明文数据。
     * @return Base64编码的加密字符串 (其内容为：IV + 密文)。
     */
    public static String encryptAES(String plainText) throws Exception {
        if (sharedKey == null) {
            throw new IllegalStateException("安全模块未初始化，请先调用 SecurityUtils.init()。");
        }

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        cipher.init(Cipher.ENCRYPT_MODE, sharedKey, ivSpec);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * 使用共享密钥解密数据。
     * @param encryptedDataAsBase64 Base64编码的加密字符串 (包含IV)。
     * @return 解密后的明文数据。
     */
    public static String decryptAES(String encryptedDataAsBase64) throws Exception {
        if (sharedKey == null) {
            throw new IllegalStateException("安全模块未初始化，请先调用 SecurityUtils.init()。");
        }

        byte[] combined = Base64.getDecoder().decode(encryptedDataAsBase64);

        byte[] iv = new byte[16];
        System.arraycopy(combined, 0, iv, 0, iv.length);

        byte[] encryptedBytes = new byte[combined.length - iv.length];
        System.arraycopy(combined, iv.length, encryptedBytes, 0, encryptedBytes.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, sharedKey, ivSpec);

        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    // --- 已补全的方法 ---
    /**
     * 计算字符串的SHA-256哈希值。
     * 用于在登录时验证密码，以及在注册时处理客户端已哈希过的密码。
     * @param input 需要计算哈希的原始字符串。
     * @return 64个字符的十六进制哈希字符串。
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // 这个异常在现代Java环境中几乎不可能发生。
            throw new RuntimeException("SHA-256 算法未找到", e);
        }
    }
}
