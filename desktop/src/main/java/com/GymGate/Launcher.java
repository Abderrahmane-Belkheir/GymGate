package com.GymGate;


import javafx.application.Application;

import java.io.IOException;


public class Launcher {
    public static  String pendingRestartCommand;
    public static void main(String[] args) {
        Application.launch(GymGateEntry.class,args);
        if (pendingRestartCommand != null) {
            try {
                new ProcessBuilder(pendingRestartCommand).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.exit(0);
}

}

