package com.GymGate.bussines.models;


import java.time.LocalDateTime;

public record LatestAttendance (int memberId, LocalDateTime checkIn){}
