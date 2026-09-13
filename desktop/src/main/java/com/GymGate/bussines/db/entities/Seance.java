package com.GymGate.bussines.db.entities;

import com.GymGate.bussines.models.Sexe;

/** One single-session ("séance") drop-in price, for a {@link Sexe} × cardio combination. */
public class Seance {

    private int id;
    private double price;
    private Sexe sexe;
    private boolean cardio;

    public Seance() {
    }

    public Seance(int id, double price, Sexe sexe, boolean cardio) {
        this.id = id;
        this.price = price;
        this.sexe = sexe;
        this.cardio = cardio;
    }

    public int getId() { return id; }
    public void setId(int v) { id = v; }

    public double getPrice() { return price; }
    public void setPrice(double v) { price = v; }

    public Sexe getSexe() { return sexe; }
    public void setSexe(Sexe v) { sexe = v; }

    public boolean isCardio() { return cardio; }
    public void setCardio(boolean v) { cardio = v; }
}
