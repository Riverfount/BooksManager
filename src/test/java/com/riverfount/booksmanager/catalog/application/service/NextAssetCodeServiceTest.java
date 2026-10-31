package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.catalog.application.port.out.AssetCodePrefix;
import com.riverfount.booksmanager.catalog.domain.Copy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The suggestion of the next asset code (RN02): the highest number used with the library's prefix plus one, padded
 * to five digits. Codes that do not follow the format, or have another prefix, do not count; the inactive ones do.
 */
class NextAssetCodeServiceTest {

    private InMemoryCopyRepository copies;
    private String prefix;
    private NextAssetCodeService service;

    @BeforeEach
    void setUp() {
        copies = new InMemoryCopyRepository();
        prefix = "PAT";
        AssetCodePrefix configured = () -> prefix;
        service = new NextAssetCodeService(copies, configured);
    }

    private void register(String assetCode) {
        copies.save(Copy.create(1L, assetCode));
    }

    @Test
    void shouldSuggestTheFirstCodeWhenThereAreNoCopies() {
        assertThat(service.suggest()).isEqualTo("PAT-00001");
    }

    @Test
    void shouldSuggestTheHighestNumberPlusOneWithoutFillingGaps() {
        register("PAT-00001");
        register("PAT-00007");

        assertThat(service.suggest()).isEqualTo("PAT-00008");
    }

    @Test
    void shouldIgnoreCodesOutsideTheFormat() {
        register("LIVRO-9");
        register("PAT-12A");
        register("PAT-");
        register("PAT00050");
        register("PAT-00003");

        assertThat(service.suggest()).isEqualTo("PAT-00004");
    }

    @Test
    void shouldIgnoreCodesOfAnotherPrefix() {
        register("BIB-00099");
        register("PATX-00099");
        register("PAT-00002");

        assertThat(service.suggest()).isEqualTo("PAT-00003");
    }

    @Test
    void shouldCountTheCodesOfInactiveCopiesBecauseTheyStayTaken() {
        var copy = copies.save(Copy.create(1L, "PAT-00010"));
        copy.deactivate(false);
        copies.save(copy);

        assertThat(service.suggest()).isEqualTo("PAT-00011");
    }

    @Test
    void shouldGrowPastFiveDigits() {
        register("PAT-99999");

        assertThat(service.suggest()).isEqualTo("PAT-100000");
    }

    @Test
    void shouldIgnoreANumberTooLongToBeASequenceSoTheCounterNeverOverflows() {
        register("PAT-" + "9".repeat(20));
        register("PAT-1234567890");
        register("PAT-00005");

        assertThat(service.suggest()).isEqualTo("PAT-00006");
    }

    @Test
    void shouldStartANewSequenceWhenThePrefixChangesKeepingTheOldCodes() {
        register("PAT-00007");
        prefix = "BIB";

        assertThat(service.suggest()).isEqualTo("BIB-00001");
    }

    @Test
    void shouldReadThePrefixAtEachSuggestionSoAChangeInTheConfigurationApplies() {
        assertThat(service.suggest()).isEqualTo("PAT-00001");

        prefix = "ABCD";

        assertThat(service.suggest()).isEqualTo("ABCD-00001");
    }
}
