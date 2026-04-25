package com.irc.utils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class LogManager {

    private static final String LOG_DIR = "logs";
    private static final SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_YELLOW = "\u001B[33m";

    public static void info(String message) {
        String timeStr = sdfTime.format(new Date());
        String logText = String.format("[%s] [信息] %s", timeStr, message);
        System.out.println(ANSI_GREEN + logText + ANSI_RESET);
        writeToFile(logText);
    }

    public static void warn(String message) {
        String timeStr = sdfTime.format(new Date());
        String logText = String.format("[%s] [警告] %s", timeStr, message);
        System.out.println(ANSI_YELLOW + logText + ANSI_RESET);
        writeToFile(logText);
    }

    public static void error(String message) {
        String timeStr = sdfTime.format(new Date());
        String logText = String.format("[%s] [错误] %s", timeStr, message);
        System.err.println(ANSI_RED + logText + ANSI_RESET);
        writeToFile(logText);
    }

    private static synchronized void writeToFile(String text) {
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String fileName = sdfDate.format(new Date()) + ".log";
            File file = new File(dir, fileName);

            try (FileWriter fw = new FileWriter(file, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(text);
            }

        } catch (IOException e) {
            System.err.println("日志写入失败: " + e.getMessage());
        }
    }
}