package com.GymGate.bussines.util;

public class Converter {
    public static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        value = value.trim().toLowerCase();

        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
