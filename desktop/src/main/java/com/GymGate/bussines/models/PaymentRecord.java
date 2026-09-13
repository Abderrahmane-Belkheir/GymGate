package com.GymGate.bussines.models;

import com.GymGate.bussines.util.Converter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class PaymentRecord {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private int memberId;
    private int amount;
    private String firstName;
    private String lastName;
    private String planName;
    private LocalDateTime paymentDate;

    // Single-session ("séance") sale — no member, no plan.
    private boolean seance;
    private Sexe seanceSexe;
    private boolean seanceCardio;

    public PaymentRecord(int memberId,String firstName,String lastName,int amount,String planName,LocalDateTime paymentDate){
        this.memberId=memberId;
        this.firstName=firstName;
        this.lastName=lastName;
        this.amount=amount;
        this.planName=planName;
        this.paymentDate=paymentDate;
    }

    /** A séance sale row: gender + cardio + price, no member/plan. */
    public PaymentRecord(int amount, Sexe seanceSexe, boolean seanceCardio, LocalDateTime paymentDate) {
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.seance = true;
        this.seanceSexe = seanceSexe;
        this.seanceCardio = seanceCardio;
    }

    public String getInitials() { return ""; }
    public int getId(){
        return memberId;
    }

    public int getAmount(){
        return amount;
    }

    public String getPlanName(){
        return planName;
    }

    public boolean isSeance() { return seance; }
    public Sexe getSeanceSexe() { return seanceSexe; }
    public boolean isSeanceCardio() { return seanceCardio; }

    public String getFullName() {
        return Converter.capitalize(firstName) + " " + Converter.capitalize(lastName);
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public String getFormattedTime() {
        return paymentDate.toLocalTime().format(TIME_FORMAT);
    }

    public String getFormattedDate() {
        return paymentDate.toLocalDate().format(DATE_FORMAT);
    }

}
