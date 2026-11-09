package com.lnmiit.docreq.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a document request. This enum encodes the DocumentRequest state
 * diagram (Assignment 5, Section 38.1) as an explicit allowed-transition map
 * (Assignment 6, Section 43.8).
 *
 * <pre>
 *  SUBMITTED  -> INCOMPLETE | VERIFIED
 *  INCOMPLETE -> SUBMITTED              (student resubmits)
 *  VERIFIED   -> FORWARDED  | PROCESSING (approval required or not)
 *  FORWARDED  -> APPROVED   | REJECTED
 *  APPROVED   -> PROCESSING
 *  PROCESSING -> COMPLETED
 *  REJECTED, COMPLETED: final (BR-006)
 * </pre>
 */
public enum RequestStatus {
    SUBMITTED,
    INCOMPLETE,
    VERIFIED,
    FORWARDED,
    APPROVED,
    REJECTED,
    PROCESSING,
    COMPLETED;

    private static final Map<RequestStatus, Set<RequestStatus>> ALLOWED = new EnumMap<>(RequestStatus.class);

    static {
        ALLOWED.put(SUBMITTED, EnumSet.of(INCOMPLETE, VERIFIED));
        ALLOWED.put(INCOMPLETE, EnumSet.of(SUBMITTED));
        ALLOWED.put(VERIFIED, EnumSet.of(FORWARDED, PROCESSING));
        ALLOWED.put(FORWARDED, EnumSet.of(APPROVED, REJECTED));
        ALLOWED.put(APPROVED, EnumSet.of(PROCESSING));
        ALLOWED.put(PROCESSING, EnumSet.of(COMPLETED));
        ALLOWED.put(REJECTED, EnumSet.noneOf(RequestStatus.class));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(RequestStatus.class));
    }

    public boolean canTransitionTo(RequestStatus next) {
        return next != null && ALLOWED.get(this).contains(next);
    }

    public Set<RequestStatus> allowedNext() {
        return Collections.unmodifiableSet(ALLOWED.get(this));
    }

    public boolean isFinal() {
        return ALLOWED.get(this).isEmpty();
    }

    /** Human-readable label used in messages and the UI ("Submitted", "Processing", ...). */
    public String label() {
        String n = name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }
}
