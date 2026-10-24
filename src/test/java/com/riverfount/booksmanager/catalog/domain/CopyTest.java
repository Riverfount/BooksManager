package com.riverfount.booksmanager.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"DAMAGED", "LOST"})
    void shouldNotMakeADamagedOrLostCopyAvailableWhenItIsChecedIn(CopyStatus status) {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", status, true, 0L);

        copy.checkIn();

        assertThat(copy.getStatus()).isEqualTo(status);
    }

    @Test
    void shouldCheckInABorrowedCopyAsAvailableWhenItCameBackInGoodCondition() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.BORROWED, true, 0L);

        copy.checkIn(CopyReturnCondition.GOOD);

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void aBorrowedCopyThatCameBackDamagedShouldBecomeDamagedAndNeverAvailable() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.BORROWED, true, 0L);

        copy.checkIn(CopyReturnCondition.DAMAGED);

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.DAMAGED);
    }

    @Test
    void aBorrowedCopyThatWasLostShouldBecomeLostAndNeverAvailable() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.BORROWED, true, 0L);

        copy.checkIn(CopyReturnCondition.LOST);

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.LOST);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"AVAILABLE", "RESERVED"})
    void shouldNotCheckInWithAConditionACopyThatIsNotBorrowed(CopyStatus status) {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", status, true, 0L);

        assertThatThrownBy(() -> copy.checkIn(CopyReturnCondition.DAMAGED))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(copy.getStatus()).isEqualTo(status);
    }

    @Test
    void shouldRefuseACheckInWithoutCondition() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.BORROWED, true, 0L);

        assertThatThrownBy(() -> copy.checkIn(null)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("condição");
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.BORROWED);
    }

    @ParameterizedTest
    @EnumSource(CopyReturnCondition.class)
    void aCopyAlreadyDamagedOrLostKeepsItsStatusWhateverTheCondition(CopyReturnCondition condition) {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.LOST, true, 0L);

        copy.checkIn(condition);

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.LOST);
    }

    @Test
    void theManualChangeShouldStillRefuseABorrowedCopy() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.BORROWED, true, 0L);

        assertThatThrownBy(() -> copy.changeStatus(CopyStatus.DAMAGED)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> copy.changeStatus(CopyStatus.LOST)).isInstanceOf(BusinessRuleException.class);
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

        copy.deactivate(false);

        assertThat(copy.isActive()).isFalse();
    }

    @Test
    void shouldNotDeactivateACopyThatHasAnActiveLoanEvenWhenItsStatusDoesNotShowIt() {
        var copy = Copy.create(1L, "PAT-1");

        assertThatThrownBy(() -> copy.deactivate(true)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empréstimo ativo");

        assertThat(copy.isActive()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(CopyStatus.class)
    void aCopyWithAnActiveLoanShouldNeverBeDeactivatedWhateverItsStatus(CopyStatus status) {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", status, true, 0L);

        assertThatThrownBy(() -> copy.deactivate(true)).isInstanceOf(BusinessRuleException.class);

        assertThat(copy.isActive()).isTrue();
    }

    @Test
    void shouldDeactivateDamagedCopy() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.DAMAGED, true, 0L);

        copy.deactivate(false);

        assertThat(copy.isActive()).isFalse();
    }

    @Test
    void shouldNotDeactivateBorrowedCopy() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.BORROWED, true, 0L);

        assertThatThrownBy(() -> copy.deactivate(false)).isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"AVAILABLE", "RESERVED", "DAMAGED"})
    void shouldManuallyMarkNonBorrowedCopyAsLost(CopyStatus current) {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", current, true, 0L);

        copy.changeStatus(CopyStatus.LOST);

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.LOST);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"AVAILABLE", "RESERVED", "LOST"})
    void shouldManuallyMarkNonBorrowedCopyAsDamaged(CopyStatus current) {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", current, true, 0L);

        copy.changeStatus(CopyStatus.DAMAGED);

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.DAMAGED);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"DAMAGED", "LOST"})
    void shouldNotManuallyMarkBorrowedCopyAsDamagedOrLost(CopyStatus target) {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.BORROWED, true, 0L);

        assertThatThrownBy(() -> copy.changeStatus(target))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("emprestado");
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.BORROWED);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"DAMAGED", "LOST"})
    void shouldManuallyMakeDamagedOrLostCopyAvailableAgain(CopyStatus current) {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", current, true, 0L);

        copy.changeStatus(CopyStatus.AVAILABLE);

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"BORROWED", "RESERVED"})
    void shouldNotManuallyMakeBorrowedOrReservedCopyAvailable(CopyStatus current) {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", current, true, 0L);

        assertThatThrownBy(() -> copy.changeStatus(CopyStatus.AVAILABLE))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(copy.getStatus()).isEqualTo(current);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"BORROWED", "RESERVED"})
    void shouldNotManuallySetBorrowedOrReserved(CopyStatus target) {
        var copy = Copy.create(1L, "PAT-1");

        assertThatThrownBy(() -> copy.changeStatus(target))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empréstimo");
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @ParameterizedTest
    @EnumSource(CopyStatus.class)
    void settingTheCurrentStatusShouldBeNoOp(CopyStatus current) {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", current, true, 0L);

        copy.changeStatus(current);

        assertThat(copy.getStatus()).isEqualTo(current);
    }

    @Test
    void shouldNotChangeStatusToNull() {
        var copy = Copy.create(1L, "PAT-1");

        assertThatThrownBy(() -> copy.changeStatus(null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void deactivatingAnInactiveCopyShouldBeNoOp() {
        var copy = Copy.create(1L, "PAT-1");
        copy.deactivate(false);

        copy.deactivate(false);

        assertThat(copy.isActive()).isFalse();
    }

    @Test
    void deactivatingShouldKeepTheCopyData() {
        var copy = Copy.reconstitute(1L, 7L, "PAT-1", CopyStatus.LOST, true, 3L);

        copy.deactivate(false);

        assertThat(copy.getBookId()).isEqualTo(7L);
        assertThat(copy.getAssetCode()).isEqualTo("PAT-1");
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.LOST);
        assertThat(copy.getVersion()).isEqualTo(3L);
    }

    @Test
    void borrowedCopyShouldStayActiveWhenDeactivationIsRefused() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.BORROWED, true, 0L);

        assertThatThrownBy(() -> copy.deactivate(false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("emprestado");
        assertThat(copy.isActive()).isTrue();
    }

    // reservation cycle (RF16, RF18, RN03)

    @Test
    void anAvailableActiveCopyCanBeReserved() {
        var copy = Copy.create(1L, "PAT-1");

        copy.reserve();

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.RESERVED);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"BORROWED", "RESERVED", "DAMAGED", "LOST"})
    void aCopyThatIsNotAvailableCannotBeReserved(CopyStatus status) {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", status, true, 0L);

        assertThatThrownBy(copy::reserve).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("disponível e ativo");
        assertThat(copy.getStatus()).isEqualTo(status);
    }

    @Test
    void anInactiveCopyCannotBeReserved() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.AVAILABLE, false, 0L);

        assertThatThrownBy(copy::reserve).isInstanceOf(BusinessRuleException.class);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void aReservedCopyCanBeReleasedBackToAvailable() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.RESERVED, true, 0L);

        copy.releaseReservation();

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"AVAILABLE", "BORROWED", "DAMAGED", "LOST"})
    void onlyAReservedCopyCanBeReleased(CopyStatus status) {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", status, true, 0L);

        assertThatThrownBy(copy::releaseReservation).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reservado");
        assertThat(copy.getStatus()).isEqualTo(status);
    }

    @Test
    void aReservedCopyCanBePickedUpAndBecomesBorrowed() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.RESERVED, true, 0L);

        copy.checkOutReserved();

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.BORROWED);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"AVAILABLE", "BORROWED", "DAMAGED", "LOST"})
    void onlyAReservedCopyCanBePickedUp(CopyStatus status) {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", status, true, 0L);

        assertThatThrownBy(copy::checkOutReserved).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reservado");
        assertThat(copy.getStatus()).isEqualTo(status);
    }

    @Test
    void anInactiveReservedCopyCannotBePickedUpButCanBeReleased() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.RESERVED, false, 0L);

        assertThatThrownBy(copy::checkOutReserved).isInstanceOf(BusinessRuleException.class);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.RESERVED);

        copy.releaseReservation();

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void theManualStatusChangeStillRefusesReserved() {
        var copy = Copy.create(1L, "PAT-1");

        assertThatThrownBy(() -> copy.changeStatus(CopyStatus.RESERVED)).isInstanceOf(BusinessRuleException.class);
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
    }

    // reactivating a copy: its book has to be active (a copy of an inactive book is not registered either)

    @Test
    void shouldReactivateAnInactiveCopyOfAnActiveBookKeepingItsStatus() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.DAMAGED, false, 3L);

        copy.reactivate(true);

        assertThat(copy.isActive()).isTrue();
        assertThat(copy.getStatus()).isEqualTo(CopyStatus.DAMAGED);
    }

    @Test
    void shouldNotReactivateACopyWhoseBookIsInactive() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.AVAILABLE, false, 0L);

        assertThatThrownBy(() -> copy.reactivate(false)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("livro está inativo");

        assertThat(copy.isActive()).isFalse();
    }

    @Test
    void reactivatingAnActiveCopyShouldChangeNothingEvenIfItsBookIsInactive() {
        var copy = Copy.reconstitute(1L, 1L, "PAT-1", CopyStatus.AVAILABLE, true, 0L);

        copy.reactivate(false);

        assertThat(copy.isActive()).isTrue();
    }
}
