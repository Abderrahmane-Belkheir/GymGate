package com.GymGate.bussines.services;

import com.GymGate.Ai.recognition.RecognitionResult;
import com.GymGate.Ai.recognition.RecognitionStatus;
import com.GymGate.bussines.db.dao.MemberDao;
import com.GymGate.bussines.db.dao.ReminderDao;
import com.GymGate.bussines.db.entities.Member;
import com.GymGate.bussines.models.ValidationResult;
import com.GymGate.bussines.util.SoundUtil;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class MemberValidationService {

    private final MemberDao memberDao;
    private final AttendanceService attendanceService;
    private final RecognitionResultPresenter recognitionResultPresenter;



    private Instant lastRecognition;


    private static MemberValidationService instance;


    private MemberValidationService(){
        this.memberDao=MemberDao.getInstance();
        this.attendanceService =AttendanceService.getInstance();
        this.recognitionResultPresenter=RecognitionResultPresenter.getInstance();
    }

    public void process(RecognitionResult recognitionResult) {
       ValidationResult result= validate(recognitionResult);
       switch (result.getStatus()){
           case SUCCESS ,ALREADY_CHECKED_IN-> SoundUtil.playWelcome();
           case PLAN_EXPIRED,NO_REMAINING_DAYS -> SoundUtil.playMembershipExpired();
           case NO_ACTIVE_PLAN -> SoundUtil.playNoActivePlan();
       }
       if(result.getStatus()== ValidationResult.ValidationStatus.SUCCESS){
           Member member=result.getMember();
           LocalDate today = LocalDate.now();
           attendanceService.save(member.getId());
           memberDao.onAttendance(member.getId());
           memberDao.markVisited(member.getId(), today);
           member.decreaseRemaining();
           member.setLastVisit(today);
           // checked in — clear today's inactivity reminder if it was logged
           ReminderDao.getInstance().deleteForMember(member.getId());
       }
         recognitionResultPresenter.show(result);
         lastRecognition=Instant.now();
    }

    /**
     * Staff confirmed a POSSIBLE_MATCH suggestion was actually correct. Re-runs
     * the normal MATCHED pipeline for that member — the same plan/expiry/
     * already-checked-in checks apply, so a staff-confirmed identity still
     * can't bypass business rules; it only replaces the low-confidence score
     * with a human's word that the identity itself is right.
     */
    public void confirmPossibleMatch(int memberId) {
        process(new RecognitionResult(memberId, 1f, RecognitionStatus.MATCHED));
    }


    public  ValidationResult validate(RecognitionResult recognitionResult) {

        if (recognitionResult.getStatus() == RecognitionStatus.UNKNOWN_FACE) {
            // Not confident enough to check in outright, but the closest
            // candidate scored close enough to the bar to be worth a staff
            // confirmation instead of a bare rejection — see
            // FaceProcessor.applyLowConfidenceHint().
            if (recognitionResult.isNearMiss() && recognitionResult.getNearestMemberId() != 0) {
                Optional<Member> candidate = memberDao.findById(recognitionResult.getNearestMemberId());
                if (candidate.isPresent()) {
                    return new ValidationResult(
                            ValidationResult.ValidationStatus.POSSIBLE_MATCH, candidate.get());
                }
            }
            return new ValidationResult(
                    ValidationResult.ValidationStatus.MEMBER_NOT_FOUND);
        }

        // AMBIGUOUS = cleared the absolute bar but NOT confirmed: too close to a
        // second member, matched on a single stored angle, or a contested
        // look-alike vote (see FaceProcessor / FaceRecognitionService). Never
        // check anyone in on a maybe — treat it exactly like "not found" so the
        // person re-presents for a cleaner frame. Without this, an AMBIGUOUS
        // result still carries a memberId and would silently mark that member
        // present, defeating the margin / look-alike checks upstream.
        if (recognitionResult.getStatus() == RecognitionStatus.AMBIGUOUS) {
            return new ValidationResult(
                    ValidationResult.ValidationStatus.MEMBER_NOT_FOUND);
        }

        Optional<Member> optionalMember =
                    memberDao.findById(recognitionResult.getMemberId());

        if (optionalMember.isEmpty()) {
            return new ValidationResult(
                    ValidationResult.ValidationStatus.MEMBER_NOT_FOUND);
        }

        Member member = optionalMember.get();

        if (member.getPlanId() == null ||
                member.getStartDate() == null ||
                member.getEndDate() == null) {

            return new ValidationResult(ValidationResult.ValidationStatus.NO_ACTIVE_PLAN,
                    member);
        }

        if (member.getEndDate().isBefore(LocalDate.now())) {
            return new ValidationResult(ValidationResult.ValidationStatus.PLAN_EXPIRED,
                    member);
        }

        if (attendanceService.existsToday(member.getId())) {
            return new ValidationResult(ValidationResult.ValidationStatus.ALREADY_CHECKED_IN,
                    member);
        }

        if (member.getRemainingDays()!=null&&member.getRemainingDays() <= 0) {
            return new ValidationResult(ValidationResult.ValidationStatus.NO_REMAINING_DAYS,
                    member);
        }


        return new ValidationResult(
                ValidationResult.ValidationStatus.SUCCESS,
                member);
    }

    public synchronized static MemberValidationService getInstance(){
    if(instance==null){
        instance=new MemberValidationService();
    }
    return instance;
    }

}
