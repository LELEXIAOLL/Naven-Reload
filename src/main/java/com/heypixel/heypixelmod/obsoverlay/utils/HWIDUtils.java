package com.heypixel.heypixelmod.obsoverlay.utils;

import com.heypixel.heypixelmod.obfuscation.JNICObf;
import oshi.SystemInfo;
import oshi.hardware.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@JNICObf
public class HWIDUtils {
    private static String cachedHWID = null;
    private HWIDUtils() {
    }

    public static String getHWID() {
        if (cachedHWID != null) {
            return cachedHWID;
        }

        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();
        StringBuilder sb = new StringBuilder();

        try {
            CentralProcessor processor = hal.getProcessor();
            if (processor != null && processor.getProcessorIdentifier() != null) {
                String processorID = processor.getProcessorIdentifier().getProcessorID();
                if (processorID != null && !processorID.isEmpty()) {
                    sb.append("CPU:").append(processorID).append(":");
                }
            }
            List<HWDiskStore> diskStores = hal.getDiskStores();
            if (diskStores != null && !diskStores.isEmpty()) {
                for (HWDiskStore disk : diskStores) {
                    String diskSerial = disk.getSerial();
                    if (diskSerial != null && !diskSerial.isEmpty() && !"Unknown".equalsIgnoreCase(diskSerial)) {
                        sb.append("DISK:").append(diskSerial).append(":");
                        break; // 只获取第一个有效硬盘的序列号
                    }
                }
            }

            ComputerSystem computerSystem = hal.getComputerSystem();
            Baseboard baseboard = computerSystem.getBaseboard();

            // (已修正笔误：base -> baseboard)
            String boardSerial = baseboard.getSerialNumber();
            if (boardSerial != null && !boardSerial.isEmpty() && !"Unknown".equalsIgnoreCase(boardSerial)) {
                sb.append("MB_SERIAL:").append(boardSerial).append(":");
            }

            String boardModel = baseboard.getModel();
            if (boardModel != null && !boardModel.isEmpty() && !"Unknown".equalsIgnoreCase(boardModel)) {
                sb.append("MB_MODEL:").append(boardModel).append(":");
            }

        } catch (Exception e) {
            System.err.println("获取硬件信息时出错: " + e.getMessage());
        }

        // 如果上述信息都获取失败，使用主机名作为备用方案
        if (sb.length() == 0) {
            sb.append("Fallback:").append(si.getOperatingSystem().getNetworkParams().getHostName());
        }

        String fullHash = generateHash(sb.toString());

        // --- 关键修改点 ---
        // 将截取的字符串长度从 25 改为 32
        cachedHWID = fullHash.length() > 32 ? fullHash.substring(0, 32) : fullHash;

        return cachedHWID;
    }

    /**
     * 对输入字符串进行 SHA-256 哈希计算。
     * @param input 原始信息字符串。
     * @return 64位的十六进制哈希字符串。
     */
    private static String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("SHA-256 算法未找到。");
            return ""; // 在发生错误时返回空字符串
        }
    }
}