package com.GymGate.bussines.db.dao;

/**
 * Why a member is being reminded — not persisted (a member's {@code end_date}
 * can only match one of these at a time), only carried on {@link ReminderDao.Due}
 * to pick the review card and WhatsApp message.
 */
public enum ReminderType {

    /** The member's plan expires <b>today</b> — time to renew. */
    EXPIRY,

    /** The member's plan ended exactly 5 days ago and they still haven't
     *  renewed — a first, softer win-back follow-up. */
    LAPSED_5D,

    /** The member's plan ended exactly 10 days ago and they still haven't
     *  renewed — a more urgent follow-up; the gym risks losing them. */
    LAPSED_10D,

    /** The member's plan is still active but they haven't checked in for a long
     *  time — a "we miss you" nudge. */
    INACTIVITY
}
