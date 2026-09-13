package com.GymGate.bussines.services;

import com.GymGate.bussines.db.dao.AttendanceDao;
import com.GymGate.bussines.db.dao.MemberDao;
import com.GymGate.bussines.db.dao.PaymentDao;
import com.GymGate.bussines.db.entities.Member;
import com.GymGate.bussines.models.MemberStats;
import com.GymGate.bussines.models.PaymentRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Assembles the {@link MemberStats} shown on the Member Detail view from the
 * member row plus their full payment and attendance history.
 */
public class MemberStatsService {

    private final MemberDao memberDao;
    private final PaymentDao paymentDao;
    private final AttendanceDao attendanceDao;

    private static MemberStatsService instance;

    private MemberStatsService() {
        this.memberDao = MemberDao.getInstance();
        this.paymentDao = PaymentDao.getInstance();
        this.attendanceDao = AttendanceDao.getInstance();
    }

    public MemberStats getStats(int memberId) {
        Member member = memberDao.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("Member not found: " + memberId));
        List<PaymentRecord> payments = paymentDao.findByMember(memberId);
        List<LocalDateTime> visits = attendanceDao.findDatesForMember(memberId);
        return new MemberStats(member, payments, visits);
    }

    public static synchronized MemberStatsService getInstance() {
        if (instance == null) {
            instance = new MemberStatsService();
        }
        return instance;
    }
}
