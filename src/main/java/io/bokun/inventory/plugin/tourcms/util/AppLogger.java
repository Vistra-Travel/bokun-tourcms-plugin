package io.bokun.inventory.plugin.tourcms.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppLogger {
    private static final Logger log = LoggerFactory.getLogger("AppLogger");
    private static final Logger errorLog = LoggerFactory.getLogger("ErrorLogger");

    public static void info(String tag, String message) {
        log.info("[{}] {}", tag, message);
    }

    public static void warn(String tag, String message) {
        log.warn("[{}] {}", tag, message);
    }

    public static void error(String tag, String message) {
        errorLog.error("[{}] {}", tag, message);
    }

    public static void error(String tag, String message, Throwable throwable) {
        errorLog.error("[{}] {}", tag, message, throwable);
    }
}
