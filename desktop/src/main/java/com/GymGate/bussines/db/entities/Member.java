package com.GymGate.bussines.db.entities;


import com.GymGate.bussines.models.Sexe;

import java.time.LocalDate;

public class Member {

    private int id;
    private String firstName;
    private String lastName;
    private String phoneNumber;

    private Integer planId;
    private String planName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer remainingDays;
    private LocalDate lastVisit;
    private LocalDate createdDate;
    private Sexe sexe;
    public Member() {}

    public Member(String firstName, String lastName, String phoneNumber, Sexe sexe) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.sexe=sexe;
    }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { firstName = v; }

    public String getLastName() { return lastName; }
    public void setLastName(String v) { lastName = v; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String v) { phoneNumber = v; }

    public Sexe getSexe() { return sexe; }
    public void setSexe(Sexe v) { sexe = v; }

    public void setCreatedDate(LocalDate v){this.createdDate=v;}
    public LocalDate getCreatedDate(){return createdDate;}


    public Integer getPlanId() { return planId; }
    public void setPlanId(Integer v) { planId = v; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { startDate = v; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { endDate = v; }

    public Integer getRemainingDays() { return remainingDays; }
    public void setRemainingDays(Integer v) { remainingDays = v; }

    /** Date of the member's most recent check-in; {@code null} if they never came. */
    public LocalDate getLastVisit() { return lastVisit; }
    public void setLastVisit(LocalDate v) { lastVisit = v; }

   public String getPlanName(){
        return planName;
   }

   public void setPlanName(String name){
        this.planName=name;
   }

   public void decreaseRemaining(){
        if(remainingDays!=null) remainingDays=remainingDays-1;
   }

}