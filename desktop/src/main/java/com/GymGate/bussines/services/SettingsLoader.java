package com.GymGate.bussines.services;

import com.GymGate.bussines.models.Settings;
import com.GymGate.bussines.util.AppPaths;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public final class SettingsLoader {

    private static final Properties properties = new Properties();
    private static final File SETTINGS_FILE = AppPaths.resolve("Settings.properties");


    public static void loadSettings() {

        try {
           HttpClient client=null;

            if (!SETTINGS_FILE.exists()) {
              createDefaultSettings();
                return;
            }

            try (InputStream in = Files.newInputStream(SETTINGS_FILE.toPath())) {
                properties.clear();
                properties.load(in);
            }

            Settings.setLanguage(properties.getProperty("language"));
            Settings.setGymName(properties.getProperty("gym-name"));
            Settings.setLogoPath(properties.getProperty("gym-logo-path", ""));
            Settings.setWhatsappEnabled(Boolean.parseBoolean(properties.getProperty("whatsapp", "true")));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private static void createDefaultSettings() {
        Settings.setGymName("GymGate Gym");
        Settings.setLanguage("en");
        Settings.setLogoPath("");
        Settings.setWhatsappEnabled(true);
        properties.clear();
        properties.setProperty("gym-name", Settings.getGymName());
        properties.setProperty("language", Settings.getLanguage());
        properties.setProperty("gym-logo-path","");
        properties.setProperty("whatsapp", "true");
        try (OutputStream out = Files.newOutputStream(SETTINGS_FILE.toPath())) {
            properties.store(out, "GymGate Settings");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void editLanguage(String language) {
        editProperty("language",language);
    }
    public static void editGymName(String gymName){
        editProperty("gym-name",gymName);
    }

    public static void editGymLogo(String logoPath){
        editProperty("gym-logo-path",logoPath);
    }

    public static void editWhatsapp(boolean enabled){
        editProperty("whatsapp", String.valueOf(enabled));
    }


    private static void editProperty(String property,String value){
        properties.setProperty(property,value);
        try(OutputStream outputStream=Files.newOutputStream(SETTINGS_FILE.toPath())) {
            properties.store(outputStream,"GymGate Settings");
        }catch (IOException e){
            throw new RuntimeException(e);
        }
    }

}