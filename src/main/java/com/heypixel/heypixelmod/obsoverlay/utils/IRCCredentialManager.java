package com.heypixel.heypixelmod.obsoverlay.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Properties;

public class IRCCredentialManager {
    private static final String AES_KEY = "a93oqosvxwsrr91p";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    
    private static final Path CREDENTIALS_FILE = Paths.get(System.getProperty("java.io.tmpdir"), "nxdircacc.accs");

    
    public static void saveCredentials(String username, String password) {
        try {
            Properties props = new Properties();
            props.setProperty("username", encrypt(username));
            props.setProperty("password", encrypt(password));
            
            try (OutputStream output = Files.newOutputStream(CREDENTIALS_FILE)) {
                props.store(output, "NXD IRC Credentials");
            }
        } catch (Exception e) {
            System.err.println("Error saving credentials: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
    public static Properties loadCredentials() {
        if (!Files.exists(CREDENTIALS_FILE)) {
            return null;
        }
        
        try {
            Properties props = new Properties();
            try (InputStream input = Files.newInputStream(CREDENTIALS_FILE)) {
                props.load(input);
            }
            
            String encryptedUsername = props.getProperty("username");
            String encryptedPassword = props.getProperty("password");
            
            if (encryptedUsername != null && encryptedPassword != null) {
                props.setProperty("username", decrypt(encryptedUsername));
                props.setProperty("password", decrypt(encryptedPassword));
                return props;
            }
        } catch (Exception e) {
            System.err.println("Error loading credentials: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    
    private static String encrypt(String plainText) throws Exception {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(AES_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            System.err.println("Error encrypting text: " + e.getMessage());
            throw e;
        }
    }
    private static String decrypt(String encryptedText) throws Exception {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(AES_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decryptedBytes);
        } catch (Exception e) {
            System.err.println("Error decrypting text: " + e.getMessage());
            throw e;
        }
    }
}