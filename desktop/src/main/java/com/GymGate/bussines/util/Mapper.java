package com.GymGate.bussines.util;



import com.GymGate.bussines.db.entities.Member;
import com.GymGate.bussines.models.RegistrationData;

public class Mapper {

    public static Member toMember(RegistrationData data){
        return new Member(data.getFirstName(),data.getLastName(),data.getPhoneNumber(),data.getSexe());
    }

}
