package com.visual.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class XealeniumRuntimeProperties {
    private static final String RESOURCE_NAME = "xealenium.properties";
    private static final Properties FILE_PROPERTIES = loadFileProperties();

    private XealeniumRuntimeProperties() {
    }

    public static String get(String key) {
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue.trim();
        }
        String fileValue = FILE_PROPERTIES.getProperty(key);
        return fileValue == null ? "" : fileValue.trim();
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value.isBlank() ? defaultValue : value;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        return value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }

    public static double getDouble(String key, double defaultValue) {
        String value = get(key);
        if (value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public static void applyToSystemProperties(String... keys) {
        for (String key : keys) {
            if (System.getProperty(key) == null) {
                String value = FILE_PROPERTIES.getProperty(key);
                if (value != null && !value.isBlank()) {
                    System.setProperty(key, value.trim());
                }
            }
        }
    }

    private static Properties loadFileProperties() {
        Properties properties = new Properties();
        try (InputStream input = XealeniumRuntimeProperties.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            System.err.println("[XEALENIUM-CONFIG] Unable to load " + RESOURCE_NAME + ": " + e.getMessage());
        }
        return properties;
    }
}
