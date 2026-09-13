package com.GymGate.bussines.services;


import com.GymGate.bussines.models.Settings;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

public class I18nService {

    private static Locale locale;
    private static ResourceBundle bundle ;


    private I18nService() {
    }

    public static void init(){
        String lan=Settings.getLanguage();
            switch (lan){
                case "fr"-> locale=Locale.FRENCH;
                case "ar"-> locale=Locale.forLanguageTag("ar");
                default -> locale=Locale.ENGLISH;
            }
            bundle=ResourceBundle.getBundle("i18n.messages",locale);
     }


    public static String get(String key) {
        return bundle.getString(key);
    }

    public static ResourceBundle getBundle(){return bundle;}

    /** The active locale, for locale-aware formatting (day/month names, numbers). */
    public static Locale getLocale() {
        return locale != null ? locale : Locale.ENGLISH;
    }

}