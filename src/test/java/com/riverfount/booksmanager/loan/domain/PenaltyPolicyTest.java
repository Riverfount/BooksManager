package com.riverfount.booksmanager.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PenaltyPolicyTest {

    @Test
    void shouldHaveExactlyTheFourVariantsOfTheLatenessPenalty() {
        assertThat(PenaltyPolicy.values()).containsExactly(
                PenaltyPolicy.FINE, PenaltyPolicy.SUSPENSION, PenaltyPolicy.BOTH, PenaltyPolicy.NONE);
    }

    @Test
    void theNamesShouldMatchWhatTheDatabaseAccepts() {
        assertThat(PenaltyPolicy.values()).extracting(Enum::name)
                .containsExactly("FINE", "SUSPENSION", "BOTH", "NONE");
    }
}
