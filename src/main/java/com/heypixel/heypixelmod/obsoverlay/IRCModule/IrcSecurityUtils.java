package com.heypixel.heypixelmod.obsoverlay.IRCModule;

import com.heypixel.heypixelmod.obfuscation.JNICObf;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@JNICObf
public final class IrcSecurityUtils {

    // 关键：这个预共享密钥必须与服务器 SecurityUtils.java 中的完全一样！
    private static final String SHARED_KEY_STRING = "再见navenreload";
    private static final SecretKey sharedKey;

    // 静态初始化块，在类加载时就将字符串密钥转换为 SecretKey 对象，高效且安全。
    static {
        byte[] keyBytes = SHARED_KEY_STRING.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            // 这个检查是为了防止意外修改密钥导致长度不符
            throw new RuntimeException("致命错误：预共享密钥长度必须是32字节！");
        }
        sharedKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 使用共享密钥加密数据 (AES-256/CBC/PKCS5Padding)。
     * @param plainText 明文数据。
     * @return Base64编码的加密字符串 (其内容为：IV + 密文)。
     */
    public static String encryptAES(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

        // 生成一个16字节的随机IV (初始化向量)，以确保即使加密相同的明文，每次的结果也不同
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        cipher.init(Cipher.ENCRYPT_MODE, sharedKey, ivSpec);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        // 将IV和加密数据拼接在一起，这样解密时才能找到正确的IV
        byte[] combined = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

        // 使用Base64编码，使其可以安全地作为文本在网络上传输
        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * 使用共享密钥解密数据 (AES-256/CBC/PKCS5Padding)。
     * @param encryptedDataAsBase64 Base64编码的加密字符串 (包含IV)。
     * @return 解密后的明文数据。
     */
    public static String decryptAES(String encryptedDataAsBase64) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedDataAsBase64);

        // 从拼接的数据中分离出IV和加密数据
        // 前16个字节是IV
        byte[] iv = new byte[16];
        System.arraycopy(combined, 0, iv, 0, iv.length);

        // 剩余部分是真正的加密数据
        byte[] encryptedBytes = new byte[combined.length - iv.length];
        System.arraycopy(combined, iv.length, encryptedBytes, 0, encryptedBytes.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, sharedKey, ivSpec);

        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * 计算字符串的SHA-256哈希值。
     * 用于在登录时验证密码。
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
