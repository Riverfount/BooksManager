package com.riverfount.booksmanager.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AssetCodesTest {

    @ParameterizedTest
    @CsvSource({
            "PAT, 1, PAT-00001",
            "PAT, 8, PAT-00008",
            "PAT, 99999, PAT-99999",
            "PAT, 100000, PAT-100000",
            "BIB, 42, BIB-00042",
            "ABCD, 7, ABCD-00007"
    })
    void shouldFormatThePrefixAndTheNumberPaddedToFiveDigits(String prefix, long number, String expected) {
        assertThat(AssetCodes.format(prefix, number)).isEqualTo(expected);
    }

    @Test
    void shouldSuggestTheFirstCodeWhenThereIsNoNumberYet() {
        assertThat(AssetCodes.next("PAT", 0)).isEqualTo("PAT-00001");
    }

    @Test
    void shouldSuggestTheHighestNumberPlusOneWithoutFillingGaps() {
        assertThat(AssetCodes.next("PAT", 7)).isEqualTo("PAT-00008");
    }

    @Test
    void shouldGrowPastFiveDigits() {
        assertThat(AssetCodes.next("PAT", 99_999)).isEqualTo("PAT-100000");
    }

    @Test
    void shouldDescribeTheNumbersTheSuggestionLooksAt() {
        assertThat(AssetCodes.MAX_DIGITS).isEqualTo(9);
        assertThat(AssetCodes.MIN_DIGITS).isEqualTo(5);
    }
}
