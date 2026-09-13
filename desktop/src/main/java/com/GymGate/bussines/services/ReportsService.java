package com.GymGate.bussines.services;

import com.GymGate.bussines.db.dao.AttendanceDao;
import com.GymGate.bussines.db.dao.MemberDao;
import com.GymGate.bussines.db.dao.PaymentDao;
import com.GymGate.bussines.db.entities.Member;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Assembles the Reports screen's data for a chosen period, plus the matching
 * "previous period" for comparison. Every historical figure is drawn from the
 * {@code payments} / {@code attendance} ledgers and so <b>excludes members
 * deleted since</b> (hard delete cascades). "New member" vs "renewal" is decided
 * per payment against the member's earliest payment.
 */
public final class ReportsService {

    /** 7-day amber "expiring soon" window, matching the Members-screen pill. */
    private static final int EXPIRING_SOON_DAYS = 7;
    /** Rolling window for the trend charts. */
    private static final int TREND_MONTHS = 12;
    private static final int RENEWALS_DUE_DAYS = 30;
    private static final int RENEWALS_DUE_LOW_DAYS = 5;

    private static ReportsService instance;

    private final PaymentDao payments = PaymentDao.getInstance();
    private final AttendanceDao attendance = AttendanceDao.getInstance();
    private final MemberDao members = MemberDao.getInstance();

    private ReportsService() { }

    public static synchronized ReportsService getInstance() {
        if (instance == null) {
            instance = new ReportsService();
        }
        return instance;
    }

    public enum Period {
        THIS_MONTH, LAST_MONTH, LAST_3_MONTHS, THIS_YEAR
    }

    /** A closed date range, both ends inclusive. */
    public record Range(LocalDate from, LocalDate to) { }

    /**
     * One period's numbers. {@code *Prev} fields are the same measure over the
     * comparison range (the equivalent span immediately before). {@code active}
     * and {@code composition} are "as of now" — there is no historical member
     * state to compare against.
     */
    public record Snapshot(
            Range range,
            Range compare,
            long revenue, long revenuePrev,
            long seanceRevenue, long seanceRevenuePrev,
            int newMembers, int newMembersPrev,
            int renewals, int renewalsPrev,
            int visits, int visitsPrev,
            int activeMembers,
            int[] composition,                 // { active, expiringSoon, expired, noPlan }
            Map<String, Integer> revenueByPlan,
            Map<YearMonth, int[]> revenueTrend, // last 12 months, { registration, renewal }
            List<Member> renewalsDue,
            int dueIn7, int dueIn14, int dueIn30
    ) { }

    public Range resolve(Period period) {
        LocalDate today = LocalDate.now();
        return switch (period) {
            case THIS_MONTH -> new Range(today.withDayOfMonth(1), today);
            case LAST_MONTH -> {
                YearMonth last = YearMonth.now().minusMonths(1);
                yield new Range(last.atDay(1), last.atEndOfMonth());
            }
            case LAST_3_MONTHS -> new Range(today.minusMonths(2).withDayOfMonth(1), today);
            case THIS_YEAR -> new Range(today.withDayOfMonth(1).withMonth(1), today);
        };
    }

    private Range compareRange(Period period, Range r) {
        return switch (period) {
            case THIS_MONTH, LAST_MONTH -> new Range(r.from().minusMonths(1), r.to().minusMonths(1));
            case LAST_3_MONTHS -> new Range(r.from().minusMonths(3), r.to().minusMonths(3));
            case THIS_YEAR -> new Range(r.from().minusYears(1), r.to().minusYears(1));
        };
    }

    public Snapshot build(Period period) {
        Range r = resolve(period);
        Range c = compareRange(period, r);

        int[] comp = members.composition(EXPIRING_SOON_DAYS);
        List<Member> due = members.expiringSoon(RENEWALS_DUE_DAYS, RENEWALS_DUE_LOW_DAYS);

        LocalDate today = LocalDate.now();
        int dueIn7 = 0;
        int dueIn14 = 0;
        int dueIn30 = due.size();
        for (Member m : due) {
            if (m.getEndDate() == null) {
                continue;
            }
            long d = java.time.temporal.ChronoUnit.DAYS.between(today, m.getEndDate());
            if (d <= 7) dueIn7++;
            if (d <= 14) dueIn14++;
        }

        return new Snapshot(
                r, c,
                payments.sumBetween(r.from(), r.to()), payments.sumBetween(c.from(), c.to()),
                payments.seanceRevenueBetween(r.from(), r.to()), payments.seanceRevenueBetween(c.from(), c.to()),
                payments.newMemberCountBetween(r.from(), r.to()), payments.newMemberCountBetween(c.from(), c.to()),
                payments.renewalCountBetween(r.from(), r.to()), payments.renewalCountBetween(c.from(), c.to()),
                attendance.countBetween(r.from(), r.to()), attendance.countBetween(c.from(), c.to()),
                comp[0] + comp[1],
                comp,
                payments.revenueByPlanBetween(r.from(), r.to()),
                payments.revenueSplitByMonth(TREND_MONTHS),
                due,
                dueIn7, dueIn14, dueIn30
        );
    }
}
