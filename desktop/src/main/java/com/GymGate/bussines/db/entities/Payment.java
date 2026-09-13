package com.GymGate.bussines.db.entities;

import java.time.LocalDateTime;

public class Payment {

    private int id; // database generated

    private final int memberId;
    private final int planId;
    private final int amount;
    private String date;


    public Payment(int memberId,
                   int planId,
                   int amount){
        this(memberId,planId,amount,LocalDateTime.now().toString());
    }

    private Payment(
            int memberId,
            int planId,
            int amount,
            String date) {

        this.memberId = memberId;
        this.planId = planId;
        this.amount = amount;
        this.date=date;

    }


    public int getMemberId() {
        return memberId;
    }

    public int getPlanId() {
        return planId;
    }

    public int getAmount() {
        return amount;
    }

    public String getDate(){return date;}





    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}