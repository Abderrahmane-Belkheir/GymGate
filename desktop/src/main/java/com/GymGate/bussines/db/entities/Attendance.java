package com.GymGate.bussines.db.entities;

import java.time.Instant;
import java.time.LocalDateTime;

public class Attendance {

    private int id;
    private final int memberId;
    private final LocalDateTime date;


    public Attendance(int memberId,LocalDateTime date) {
        this.memberId = memberId;
        this.date = date;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getMemberId() {
        return memberId;
    }

    public LocalDateTime getDate() {
        return date;
    }


}

