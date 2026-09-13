package com.GymGate.bussines.models;

import java.util.Set;

public class Settings {
    private static String language;
    private static String gymName;
    private static String logoPath;
    private static boolean whatsappEnabled = true;

    private Settings(){}

    public static boolean isWhatsappEnabled(){return whatsappEnabled;}
    public static void setWhatsappEnabled(boolean enabled){whatsappEnabled=enabled;}

    public static String getLogoPath(){return logoPath;}
    public static void setLogoPath(String path){logoPath=path;}
    public static String getLanguage(){
        return Settings.language;
    }
    public static void setLanguage(String language){
    Settings.language=language;
    }

    public static String getGymName(){return Settings.gymName;}
    public static void setGymName(String gymName){Settings.gymName=gymName;}

}
