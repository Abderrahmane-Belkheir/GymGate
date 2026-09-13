package com.GymGate.bussines.models;

import com.GymGate.bussines.util.Converter;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Flattened view-model for a single attendance row (already joined from Member + Plan + Attendance). */
public class AttendanceRecord {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final int id;
    private final String firstName;
    private final String lastName;
    private final LocalDateTime checkInTime;





    public AttendanceRecord( int id,String firstName,String lastName,LocalDateTime checkInTime) {
        this.firstName=firstName;
        this.id=id;
        this.lastName=lastName;
        this.checkInTime = checkInTime;
    }

    public String getInitials() { return ""; }


    public int getId(){return id;}

    public String getFullName() {
        return Converter.capitalize(firstName) + " " + Converter.capitalize(lastName);
    }

    public String getFormattedTime() {
        return checkInTime.toLocalTime().format(TIME_FORMAT);
    }

    @Override
    public String toString() {
        return "AttendanceRecord{" +
                "checkInTime=" + checkInTime +
                ", id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                '}';
    }
}