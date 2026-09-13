package com.GymGate.bussines.models;

import com.GymGate.bussines.db.entities.Member;

public class ValidationResult {

    private ValidationStatus status;

    private Member member;


    public ValidationResult(ValidationStatus status,Member member){
        this.status=status;
        this.member=member;
    }

    public ValidationResult(ValidationStatus status){
        this(status,null);
    }

    public Member getMember(){
        return member;
    }

    public ValidationStatus getStatus(){
        return status;
    }


    public enum ValidationStatus {
        SUCCESS,
        MEMBER_NOT_FOUND,
        PLAN_EXPIRED,
        ALREADY_CHECKED_IN,
        NO_ACTIVE_PLAN,
        NO_REMAINING_DAYS,
        /** Not confirmed — the closest candidate just wasn't a confident enough
         *  match to check in outright. {@code member} carries that candidate,
         *  offered to staff as an accept/reject suggestion rather than a match. */
        POSSIBLE_MATCH
    }

}