package com.GymGate.UI.navigation;

/**
 * Every page that can be shown in the main content area.
 *
 * HOME, PLANS and ATTENDANCE have a real FXML view + controller today.
 * When Members, Payments, Reports and Settings are built, add one entry
 * each here (pointing at their FXML path) and wire the matching sidebar
 * item in {@link com.GymGate.UI.controller.SidebarController} — nothing
 * else in the navigation system needs to change.
 */
public enum Page {

    HOME("/fxml/HomeView.fxml"),
    MEMBERS("/fxml/MembersView.fxml"),
    PLANS("/fxml/PlansView.fxml"),
    ATTENDANCE("/fxml/AttendanceView.fxml"),
    PAYMENTS("/fxml/PaymentsView.fxml"),
    REPORTS("/fxml/ReportsView.fxml"),
    SETTINGS("/fxml/SettingsView.fxml");

    private final String fxmlPath;

    Page(String fxmlPath) {
        this.fxmlPath = fxmlPath;
    }

    public String getFxmlPath() {
        return fxmlPath;
    }
}