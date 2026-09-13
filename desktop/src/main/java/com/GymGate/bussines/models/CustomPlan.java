package com.GymGate.bussines.models;

import java.time.LocalDate;

public class CustomPlan {
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Integer remainingDays;

    public CustomPlan(LocalDate startDate,LocalDate endDate,Integer remainingDays){
        this.startDate=startDate;
        this.endDate=endDate;
        this.remainingDays=remainingDays;
    }

    public LocalDate getStartDate(){return startDate;}
    public LocalDate getEndDate(){return endDate;}
    public Integer getRemainingDays(){return remainingDays;}
}
