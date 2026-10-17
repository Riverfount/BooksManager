package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** What the catalog offers the loan module (RN03, RNF17): availability, check out and check in. */
class CopyLoanServiceTest {

    private InMemoryCopyRepository copyRepository;
    private CopyLoanService service;

    @BeforeEach
    void setUp() {
        copyRepository = new InMemoryCopyRepository();
        service = new CopyLoanService(copyRepository);
    }

    private Long saveCopy(CopyStatus status, boolean active) {
        return copyRepository.save(Copy.reconstitute(null, 1L, "PAT-" + status + active, status, active, 0L)).getId();
    }

    private CopyStatus statusOf(Long copyId) {
        return copyRepository.findById(copyId).orElseThrow().getStatus();
    }

    // availability

    @Test
    void anActiveAvailableCopyShouldBeLendable() {
        var id = saveCopy(CopyStatus.AVAILABLE, true);

        assertThat(service.findAvailability(id)).hasValueSatisfying(
                availability -> assertThat(availability.lendable()).isTrue());
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"BORROWED", "RESERVED", "DAMAGED", "LOST"})
    void aCopyThatIsNotAvailableShouldNotBeLendable(CopyStatus status) {
        var id = saveCopy(status, true);

        assertThat(service.findAvailability(id)).hasValueSatisfying(
                availability -> assertThat(availability.lendable()).isFalse());
    }

    @Test
    void anInactiveCopyShouldNotBeLendable() {
        var id = saveCopy(CopyStatus.AVAILABLE, false);

        assertThat(service.findAvailability(id)).hasValueSatisfying(
                availability -> assertThat(availability.lendable()).isFalse());
    }

    @Test
    void shouldAnswerNothingForACopyThatDoesNotExist() {
        assertThat(service.findAvailability(999L)).isEmpty();
    }

    @Test
    void theAnswerShouldCarryTheIdOfTheCopy() {
        var id = saveCopy(CopyStatus.AVAILABLE, true);

        assertThat(service.findAvailability(id).orElseThrow().copyId()).isEqualTo(id);
    }

    // check out (RN03)

    @Test
    void shouldCheckOutAnAvailableActiveCopy() {
        var id = saveCopy(CopyStatus.AVAILABLE, true);

        service.checkOut(id);

        assertThat(statusOf(id)).isEqualTo(CopyStatus.BORROWED);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"BORROWED", "RESERVED", "DAMAGED", "LOST"})
    void shouldNotCheckOutACopyThatIsNotAvailable(CopyStatus status) {
        var id = saveCopy(status, true);

        assertThatThrownBy(() -> service.checkOut(id)).isInstanceOf(BusinessRuleException.class);
        assertThat(statusOf(id)).isEqualTo(status);
    }

    @Test
    void shouldNotCheckOutAnInactiveCopy() {
        var id = saveCopy(CopyStatus.AVAILABLE, false);

        assertThatThrownBy(() -> service.checkOut(id)).isInstanceOf(BusinessRuleException.class);
        assertThat(statusOf(id)).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void shouldNotCheckOutACopyThatDoesNotExist() {
        assertThatThrownBy(() -> service.checkOut(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // check in

    @Test
    void shouldCheckInABorrowedCopy() {
        var id = saveCopy(CopyStatus.BORROWED, true);

        service.checkIn(id);

        assertThat(statusOf(id)).isEqualTo(CopyStatus.AVAILABLE);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"DAMAGED", "LOST"})
    void checkingInADamagedOrLostCopyShouldNotMakeItAvailable(CopyStatus status) {
        var id = saveCopy(status, true);

        service.checkIn(id);

        assertThat(statusOf(id)).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"AVAILABLE", "RESERVED"})
    void shouldNotCheckInACopyThatWasNotBorrowed(CopyStatus status) {
        var id = saveCopy(status, true);

        assertThatThrownBy(() -> service.checkIn(id)).isInstanceOf(BusinessRuleException.class);
        assertThat(statusOf(id)).isEqualTo(status);
    }

    @Test
    void shouldNotCheckInACopyThatDoesNotExist() {
        assertThatThrownBy(() -> service.checkIn(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aCopyShouldBeLendableAgainAfterItIsCheckedIn() {
        var id = saveCopy(CopyStatus.AVAILABLE, true);
        service.checkOut(id);
        service.checkIn(id);

        assertThat(service.findAvailability(id).orElseThrow().lendable()).isTrue();
    }
}
