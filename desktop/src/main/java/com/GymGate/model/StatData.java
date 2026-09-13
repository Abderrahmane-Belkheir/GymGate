package com.GymGate.model;

/**
 * Immutable data holder describing a single statistic card
 * on the "Today's Statistics" panel.
 */
public class StatData {

    private final String iconLiteral;
    private final String iconBackgroundStyleClass;
    private final String value;
    private final String label;


    public StatData(String iconLiteral, String iconBackgroundStyleClass, String value,
                     String label) {
        this.iconLiteral = iconLiteral;
        this.iconBackgroundStyleClass = iconBackgroundStyleClass;
        this.value = value;
        this.label = label;
    }

    public String getIconLiteral() { return iconLiteral; }
    public String getIconBackgroundStyleClass() { return iconBackgroundStyleClass; }
    public String getValue() { return value; }
    public String getLabel() { return label; }

    public void increment(){

    }

}
