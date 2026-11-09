package com.lnmiit.docreq.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumSet;

import static com.lnmiit.docreq.domain.RequestStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests of the DocumentRequest state diagram (Assignment 5, Section 38.1). */
class RequestStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed")
    @CsvSource({
            "SUBMITTED, INCOMPLETE", "SUBMITTED, VERIFIED", "INCOMPLETE, SUBMITTED",
            "VERIFIED, FORWARDED", "VERIFIED, PROCESSING", "FORWARDED, APPROVED", "FORWARDED, REJECTED",
            "APPROVED, PROCESSING", "PROCESSING, COMPLETED"})
    void validTransitions(RequestStatus from, RequestStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} refused")
    @CsvSource({
            "SUBMITTED, APPROVED", "SUBMITTED, COMPLETED", "VERIFIED, APPROVED", "FORWARDED, PROCESSING",
            "REJECTED, PROCESSING", "REJECTED, COMPLETED", "COMPLETED, PROCESSING", "APPROVED, COMPLETED",
            "INCOMPLETE, VERIFIED"})
    void invalidTransitions(RequestStatus from, RequestStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void rejectedAndCompletedAreFinal_BR006() {
        assertThat(REJECTED.isFinal()).isTrue();
        assertThat(COMPLETED.isFinal()).isTrue();
        assertThat(EnumSet.complementOf(EnumSet.of(REJECTED, COMPLETED)))
                .allSatisfy(s -> assertThat(s.isFinal()).isFalse());
    }

    @Test
    void noStateCanTransitionToItself() {
        for (RequestStatus s : values()) {
            assertThat(s.canTransitionTo(s)).as(s.name()).isFalse();
        }
    }

    @Test
    void nullTargetIsRefused() {
        assertThat(SUBMITTED.canTransitionTo(null)).isFalse();
    }
}
