package com.GymGate.bussines.services;

import com.GymGate.bussines.db.dao.AttendanceDao;
import com.GymGate.bussines.db.dao.MemberCountType;
import com.GymGate.bussines.db.dao.MemberDao;
import com.GymGate.bussines.db.dao.PaymentDao;
import com.GymGate.bussines.db.entities.Member;
import com.GymGate.bussines.models.AttendanceRecord;
import com.GymGate.bussines.models.PaymentRecord;

import java.time.LocalDate;
import java.util.List;

public class StatsService {
    private final AttendanceDao attendanceDao;
    private final PaymentDao paymentDao;
    private final MemberDao memberDao;
    private static StatsService instance;

    public StatsService(){
        this.attendanceDao=AttendanceDao.getInstance();
        this.paymentDao=PaymentDao.getInstance();
        this.memberDao=MemberDao.getInstance();
    }

    public int getTodayChecksIn(){
        LocalDate today=LocalDate.now();
       return attendanceDao.count(today.getYear(),today.getMonthValue(),today.getDayOfMonth());
    }

    public int getTodayRevenue(){
        LocalDate today=LocalDate.now();
        return paymentDao.sumAmount(today.getYear(),today.getMonthValue(),today.getDayOfMonth());
    }

    public int getTodayRenewedSubs(){
     return memberDao.count( MemberCountType.RENEW);
    }

    public int getTodayNewMem(){
        return memberDao.count(MemberCountType.NEW);
    }
    public int getTodayExpiredMem(){
        return memberDao.count(MemberCountType.EXPIRED);
    }






    // ---- Home stat-card drill-downs: the rows behind each count -----------

    public List<AttendanceRecord> getTodayCheckInList(){
        LocalDate t = LocalDate.now();
        return attendanceDao.find(t.getYear(), t.getMonthValue(), t.getDayOfMonth());
    }

    public List<PaymentRecord> getTodayPayments(){
        LocalDate t = LocalDate.now();
        return paymentDao.find(t.getYear(), t.getMonthValue(), t.getDayOfMonth());
    }

    public List<Member> getTodayRenewedList(){
        return memberDao.renewedToday();
    }

    public List<Member> getTodayNewList(){
        return memberDao.newToday();
    }

    public List<Member> getTodayExpiringList(){
        return memberDao.expiringToday();
    }

    public synchronized static StatsService getInstance(){
        if(instance==null){
            instance=new StatsService();
        }
        return instance;
    }
}
