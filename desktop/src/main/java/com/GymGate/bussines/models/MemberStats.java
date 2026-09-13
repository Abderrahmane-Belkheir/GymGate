package com.GymGate.bussines.models;

import com.GymGate.bussines.db.entities.Member;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the Member Detail view shows about one member, computed once from
 * the member row plus their full payment and attendance history.
 *
 * <p>Payment/attendance figures are exact (direct counts and sums). The
 * subscription-derived numbers (renewals) treat every payment as one purchased
 * term, which is how registration and renewal both record a payment.
 */
public class MemberStats {

    public enum MembershipStatus { ACTIVE, EXPIRING, EXPIRED, NO_PLAN }

    private final Member member;
    private final MembershipStatus status;

    /** Newest payment first — for the history table. */
    private final List<PaymentRecord> payments;
    /** Oldest check-in first. */
    private final List<LocalDateTime> visits;

    private final int lifetimeValue;
    private final int paymentCount;
    private final int renewalCount;
    private final LocalDate firstPaymentDate;
    private final LocalDate lastPaymentDate;

    private final int totalVisits;
    private final int visitsThisMonth;
    private final LocalDate firstVisitDate;
    private final LocalDate lastVisitDate;
    private final Long daysSinceLastVisit;

    private final DayOfWeek favoriteDay;
    private final Integer favoriteHour;
    private final double visitsPerWeek;
    private final int longestGapDays;

    /** Last 6 calendar months (chronological), zero-filled. */
    private final Map<YearMonth, Integer> visitsByMonth;

    public MemberStats(Member member, List<PaymentRecord> paymentsAsc, List<LocalDateTime> visitsAsc) {
        this.member = member;
        this.status = computeStatus(member);

        List<PaymentRecord> asc = new ArrayList<>(paymentsAsc);
        this.payments = new ArrayList<>(asc);
        Collections.reverse(this.payments);
        this.paymentCount = asc.size();
        this.renewalCount = Math.max(0, paymentCount - 1);

        int sum = 0;
        for (PaymentRecord p : asc) {
            sum += p.getAmount();
        }
        this.lifetimeValue = sum;
        this.firstPaymentDate = asc.isEmpty() ? null : asc.get(0).getPaymentDate().toLocalDate();
        this.lastPaymentDate = asc.isEmpty() ? null : asc.get(asc.size() - 1).getPaymentDate().toLocalDate();

        this.visits = new ArrayList<>(visitsAsc);
        this.totalVisits = visits.size();
        this.firstVisitDate = visits.isEmpty() ? null : visits.get(0).toLocalDate();
        this.lastVisitDate = visits.isEmpty() ? null : visits.get(visits.size() - 1).toLocalDate();

        LocalDate today = LocalDate.now();
        this.daysSinceLastVisit = lastVisitDate == null
                ? null
                : ChronoUnit.DAYS.between(lastVisitDate, today);

        YearMonth thisMonth = YearMonth.now();
        int monthCount = 0;
        int[] byDow = new int[8];
        int[] byHour = new int[24];
        for (LocalDateTime v : visits) {
            if (YearMonth.from(v).equals(thisMonth)) {
                monthCount++;
            }
            byDow[v.getDayOfWeek().getValue()]++;
            byHour[v.getHour()]++;
        }
        this.visitsThisMonth = monthCount;
        this.favoriteDay = totalVisits == 0 ? null : DayOfWeek.of(argMax(byDow, 1, 7));
        this.favoriteHour = totalVisits == 0 ? null : argMax(byHour, 0, 23);

        if (totalVisits == 0) {
            this.visitsPerWeek = 0;
        } else {
            long spanDays = ChronoUnit.DAYS.between(firstVisitDate, today) + 1;
            double weeks = Math.max(1.0, spanDays / 7.0);
            this.visitsPerWeek = totalVisits / weeks;
        }

        long maxGap = 0;
        for (int i = 1; i < visits.size(); i++) {
            long gap = ChronoUnit.DAYS.between(visits.get(i - 1).toLocalDate(), visits.get(i).toLocalDate());
            maxGap = Math.max(maxGap, gap);
        }
        this.longestGapDays = (int) maxGap;

        Map<YearMonth, Integer> months = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            months.put(thisMonth.minusMonths(i), 0);
        }
        for (LocalDateTime v : visits) {
            months.computeIfPresent(YearMonth.from(v), (k, c) -> c + 1);
        }
        this.visitsByMonth = months;
    }

    private static int argMax(int[] counts, int from, int to) {
        int best = from;
        for (int i = from; i <= to; i++) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        return best;
    }

    private static MembershipStatus computeStatus(Member m) {
        if (m.getPlanId() == null) {
            return MembershipStatus.NO_PLAN;
        }
        LocalDate today = LocalDate.now();
        boolean dateValid = m.getEndDate() != null && !m.getEndDate().isBefore(today);
        boolean daysValid = m.getRemainingDays() == null || m.getRemainingDays() > 0;
        if (!dateValid || !daysValid) {
            return MembershipStatus.EXPIRED;
        }
        boolean soon = (m.getRemainingDays() != null && m.getRemainingDays() <= 7)
                || (m.getEndDate() != null && !m.getEndDate().isAfter(today.plusDays(7)));
        return soon ? MembershipStatus.EXPIRING : MembershipStatus.ACTIVE;
    }

    public Member getMember() { return member; }
    public MembershipStatus getStatus() { return status; }
    public List<PaymentRecord> getPayments() { return payments; }
    public List<LocalDateTime> getVisits() { return visits; }
    public int getLifetimeValue() { return lifetimeValue; }
    public int getPaymentCount() { return paymentCount; }
    public int getRenewalCount() { return renewalCount; }
    public LocalDate getFirstPaymentDate() { return firstPaymentDate; }
    public LocalDate getLastPaymentDate() { return lastPaymentDate; }
    public int getTotalVisits() { return totalVisits; }
    public int getVisitsThisMonth() { return visitsThisMonth; }
    public LocalDate getFirstVisitDate() { return firstVisitDate; }
    public LocalDate getLastVisitDate() { return lastVisitDate; }
    public Long getDaysSinceLastVisit() { return daysSinceLastVisit; }
    public DayOfWeek getFavoriteDay() { return favoriteDay; }
    public Integer getFavoriteHour() { return favoriteHour; }
    public double getVisitsPerWeek() { return visitsPerWeek; }
    public int getLongestGapDays() { return longestGapDays; }
    public Map<YearMonth, Integer> getVisitsByMonth() { return visitsByMonth; }
}
