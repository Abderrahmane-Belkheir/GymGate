package com.GymGate.bussines.db.entities;


import com.GymGate.bussines.models.Sexe;
import com.GymGate.bussines.services.I18nService;

public class Plan {

    private int id;
    private String name;
    private int durationMonths;
    private Integer daysPerMonth;
    private int price;
    private boolean cardio;
    private Sexe sexe;

    public Plan() {

    }

    public Plan(String name){
    if(name.equalsIgnoreCase(I18nService.get("Custom"))){
        this.name=name;
    }

    }

    public Plan(String name, int durationMonths, Integer daysPerMonth, int price,boolean cardio,Sexe sexe) {
        this.name = name;
        this.durationMonths = durationMonths;
        this.daysPerMonth = daysPerMonth;
        this.price = price;
        this.cardio=cardio;
        this.sexe=sexe;
    }

    public Sexe getSexe(){
        return sexe;
    }
    public void setSexe(Sexe sexe){
        this.sexe = sexe;
    }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getDurationMonths() { return durationMonths; }
    public void setDurationMonths(int v) { durationMonths = v; }

    public Integer getDaysPerMonth() { return daysPerMonth; }
    public void setDaysPerMonth(Integer v) { daysPerMonth = v; }

    /**
     * Visit allowance for the whole plan, i.e. the per-month day count applied
     * across every month of {@link #getDurationMonths()}. This is what a member
     * gets credited on registration/renewal — {@code daysPerMonth} alone would
     * give a multi-month plan only a single month's worth of visits. Null for an
     * unlimited plan (no per-month cap).
     */
    public Integer getTotalDays() {
        return daysPerMonth == null ? null : daysPerMonth * durationMonths;
    }

    public int getPrice() { return price; }
    public void setPrice(int v) { price = v; }

    public boolean isCardioIncluded(){return cardio;}
    public void setCardioIncluded(boolean cardioIncluded){this.cardio=cardioIncluded;}

}