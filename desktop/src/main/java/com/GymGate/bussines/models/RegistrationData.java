package com.GymGate.bussines.models;


import java.util.Objects;

/**
 * Immutable DTO carrying the values collected by RegisterMemberDialog.
 * Intentionally not a Member entity — the caller decides how/whether
 * to turn this into one.
 */
public final class RegistrationData {

    private final String firstName;
    private final String lastName;
    private final String phoneNumber;
    private final Sexe sexe;

    public RegistrationData(String firstName, String lastName, String phoneNumber,Sexe sexe) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.sexe=sexe;
    }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhoneNumber() { return phoneNumber; }
    public Sexe getSexe(){return sexe;}
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegistrationData other)) return false;
        return Objects.equals(firstName, other.firstName)
                && Objects.equals(lastName, other.lastName)
                && Objects.equals(phoneNumber, other.phoneNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName, phoneNumber);
    }

    @Override
    public String toString() {
        return "RegistrationData{firstName='" + firstName + "', lastName='" + lastName
                + "', phoneNumber='" + phoneNumber + "'}";
    }
}
