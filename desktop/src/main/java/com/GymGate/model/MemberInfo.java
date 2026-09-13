package com.GymGate.model;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.BooleanProperty;

/**
 * Represents the member currently shown on the Live Check-In card.
 * All fields are exposed as JavaFX properties so the UI can be bound
 * and later updated dynamically by the recognition pipeline.
 */
public class MemberInfo {

    private int id;
    private String fullName;
    private String phoneNumber;
    private String membershipPlan;
    private String membershipStatus;
    private String expirationDate;
    private String lastCheckIn;
    private String photoInitials;
    private boolean verified;

    public MemberInfo() {
    }

    public MemberInfo(int id, String fullName, String phoneNumber, String membershipPlan,
                      String membershipStatus, String expirationDate, String lastCheckIn,
                      String photoInitials, boolean verified) {
        this.id = id;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.membershipPlan = membershipPlan;
        this.membershipStatus = membershipStatus;
        this.expirationDate = expirationDate;
        this.lastCheckIn = lastCheckIn;
        this.photoInitials = photoInitials;
        this.verified = verified;
    }

    public int getId() { return id; }
    public void setId(int v) { id = v; }

    public String getFullName() { return fullName; }
    public void setFullName(String v) { fullName = v; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String v) { phoneNumber = v; }

    public String getMembershipPlan() { return membershipPlan; }
    public void setMembershipPlan(String v) { membershipPlan = v; }

    public String getMembershipStatus() { return membershipStatus; }
    public void setMembershipStatus(String v) { membershipStatus = v; }

    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String v) { expirationDate = v; }

    public String getLastCheckIn() { return lastCheckIn; }
    public void setLastCheckIn(String v) { lastCheckIn = v; }

    public String getPhotoInitials() { return photoInitials; }
    public void setPhotoInitials(String v) { photoInitials = v; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean v) { verified = v; }
}