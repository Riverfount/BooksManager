package com.riverfount.booksmanager.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;

class CopyTest {

    @Test
    void shouldCreateNewAvailableAndActiveCopyWithVersionZero() {
        var copy = Copy.create(1L, "PAT-1");

        assertThat(copy.getId()).isNull();
        assertThat(copy.getBookId()).isEqualTo(1L);
        assertThat(copy.getAssetCode()).isEqualTo("PAT-1");
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
        assertThat(copy.isActive()).isTrue();
        assertThat(copy.getVersion()).isEqualTo(0L);
    }

    @Test
    void shouldReconstituteCopyWithPersistedState() {
        var copy = Copy.reconstitute(1L, 2L, "PAT-1", CopyStatus.BORROWED, true, 5L);

        assertThat(copy.getId()).isEqualTo(1L);
        assertThat(copy.getBookId()).isEqualTo(2L);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.BORROWED);
        assertThat(copy.getVersion()).isEqualTo(5L);
    }

    @Test
    void shouldNotCreateCopyWithNullBookId() {
        assertThatThrownBy(() -> Copy.create(null, "PAT-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateCopyWithBlankAssetCode() {
        assertThatThrownBy(() -> Copy.create(1L, "  ")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateCopyWithLeadingOrTrailingSpaceInAssetCode() {
        assertThatThrownBy(() -> Copy.create(1L, " PAT-1")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateCopyWithAssetCodeLongerThanFiftyCharacters() {
        var codeWithFiftyOneCharacters = "A".repeat(51);

        assertThatThrownBy(() -> Copy.create(1L, codeWithFiftyOneCharacters))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldAcceptAssetCodeWithExactlyFiftyCharacters() {
        var codeWithFiftyCharacters = "A".repeat(50);

        var copy = Copy.create(1L, codeWithFiftyCharacters);

        assertThat(copy.getAssetCode()).isEqualTo(codeWithFiftyCharacters);
    }

    @Test
    void shouldNotValidateUniqueAssetCodeWhenAnotherCopyAlreadyHasTheSameCode() {
        assertThatThrownBy(() -> Copy.validateUniqueAssetCode("PAT-1", true))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldValidateUniqueAssetCodeWhenNoOtherCopyHasTheSameCode() {
        org.assertj.core.api.Assertions.assertThatCode(() -> Copy.validateUniqueAssetCode("PAT-1", false))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldCheckOutAvailableAndActiveCopy() {
        var copy = Copy.create(1L, "PAT-1");

        copy.checkOut();

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.BORROWED);
    }

    @Test
    void shouldNotCheckOutAlreadyBorrowedCopy() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.BORROWED, true, 0L);

        assertThatThrownBy(copy::checkOut).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCheckOutReservedCopy() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.RESERVED, true, 0L);

        assertThatThrownBy(copy::checkOut).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCheckOutInactiveCopy() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.AVAILABLE, false, 0L);

        assertThatThrownBy(copy::checkOut).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldCheckInBorrowedCopy() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.BORROWED, true, 0L);

        copy.checkIn();

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void shouldNotCheckInCopyThatIsNotBorrowed() {
        var copy = Copy.create(1L, "PAT-1");

        assertThatThrownBy(copy::checkIn).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldMarkCopyAsDamaged() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.BORROWED, true, 0L);

        copy.markAsDamaged();

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.DAMAGED);
    }

    @Test
    void shouldMarkCopyAsLost() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.BORROWED, true, 0L);

        copy.markAsLost();

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.LOST);
    }

    @Test
    void shouldDeactivateAvailableCopy() {
        var copy = Copy.create(1L, "PAT-1");

        copy.deactivate();

        assertThat(copy.isActive()).isFalse();
    }

    @Test
    void shouldDeactivateDamagedCopy() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.DAMAGED, true, 0L);

        copy.deactivate();

        assertThat(copy.isActive()).isFalse();
    }

    @Test
    void shouldNotDeactivateBorrowedCopy() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.BORROWED, true, 0L);

        assertThatThrownBy(copy::deactivate).isInstanceOf(BusinessRuleException.class);
    }
}
