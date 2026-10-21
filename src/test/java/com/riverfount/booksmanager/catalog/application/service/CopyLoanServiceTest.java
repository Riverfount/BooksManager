package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.catalog.domain.CopyReturnCondition;
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
    private InMemoryBookRepository bookRepository;
    private CopyLoanService service;

    @BeforeEach
    void setUp() {
        copyRepository = new InMemoryCopyRepository();
        bookRepository = new InMemoryBookRepository();
        service = new CopyLoanService(copyRepository, bookRepository);
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

    @ParameterizedTest
    @EnumSource(value = CopyReturnCondition.class, names = {"DAMAGED", "LOST"})
    void aBorrowedCopyCheckedInDamagedOrLostShouldNeverBecomeAvailable(CopyReturnCondition condition) {
        var id = saveCopy(CopyStatus.BORROWED, true);

        service.checkIn(id, condition);

        assertThat(statusOf(id)).isEqualTo(CopyStatus.valueOf(condition.name()));
        assertThat(service.findAvailability(id).orElseThrow().lendable()).isFalse();
    }

    @Test
    void checkingInWithGoodConditionShouldMakeTheCopyAvailable() {
        var id = saveCopy(CopyStatus.BORROWED, true);

        service.checkIn(id, CopyReturnCondition.GOOD);

        assertThat(statusOf(id)).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void shouldNotCheckInWithConditionACopyThatWasNotBorrowed() {
        var id = saveCopy(CopyStatus.AVAILABLE, true);

        assertThatThrownBy(() -> service.checkIn(id, CopyReturnCondition.LOST))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(statusOf(id)).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void shouldNotCheckInWithConditionACopyThatDoesNotExist() {
        assertThatThrownBy(() -> service.checkIn(999L, CopyReturnCondition.DAMAGED))
                .isInstanceOf(ResourceNotFoundException.class);
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

    // summaries for the loan module's reports

    private Long saveBook(String isbn, String title) {
        var clock = java.time.Clock.fixed(java.time.Instant.parse("2026-10-10T00:00:00Z"), java.time.ZoneOffset.UTC);
        return bookRepository.save(Book.create(new Isbn(isbn), title, "Editora X", 1899, 1L, java.util.Set.of(1L),
                clock)).getId();
    }

    private Long saveCopyOf(Long bookId, String assetCode) {
        return copyRepository.save(Copy.create(bookId, assetCode)).getId();
    }

    @Test
    void shouldGiveTheAssetCodeAndTheTitleOfSeveralCopiesAtOnce() {
        var domCasmurro = saveBook("9788533302273", "Dom Casmurro");
        var quincas = saveBook("080442957X", "Quincas Borba");
        var first = saveCopyOf(domCasmurro, "PAT-1");
        var second = saveCopyOf(quincas, "PAT-2");
        var third = saveCopyOf(domCasmurro, "PAT-3");

        var summaries = service.findSummaries(java.util.Set.of(first, second, 999L));

        assertThat(summaries).containsOnlyKeys(first, second);
        assertThat(summaries.get(first).assetCode()).isEqualTo("PAT-1");
        assertThat(summaries.get(first).bookId()).isEqualTo(domCasmurro);
        assertThat(summaries.get(first).bookTitle()).isEqualTo("Dom Casmurro");
        assertThat(summaries.get(second).bookTitle()).isEqualTo("Quincas Borba");
        assertThat(summaries.get(first).copyId()).isEqualTo(first);
        assertThat(summaries).doesNotContainKey(third);
    }

    @Test
    void shouldGiveNoSummariesForNoCopies() {
        assertThat(service.findSummaries(java.util.Set.of())).isEmpty();
    }

    @Test
    void aSummaryShouldAlsoCoverAnInactiveCopyBecauseTheHistoryKeepsIt() {
        var book = saveBook("9788533302273", "Dom Casmurro");
        var copy = copyRepository.save(
                Copy.reconstitute(null, book, "PAT-OLD", CopyStatus.AVAILABLE, false, 0L)).getId();

        assertThat(service.findSummaries(java.util.Set.of(copy))).containsOnlyKeys(copy);
    }

    // the copy is also told to be reserved (RF18)

    @Test
    void aReservedCopyShouldBeReportedAsReservedAndNotLendable() {
        var id = saveCopy(CopyStatus.RESERVED, true);

        var availability = service.findAvailability(id).orElseThrow();

        assertThat(availability.reserved()).isTrue();
        assertThat(availability.lendable()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"AVAILABLE", "BORROWED", "DAMAGED", "LOST"})
    void aCopyThatIsNotReservedShouldNotBeReportedAsReserved(CopyStatus status) {
        var id = saveCopy(status, true);

        assertThat(service.findAvailability(id).orElseThrow().reserved()).isFalse();
    }

    // reserve, release and pick up (RF16, RF17, RF18, RN03)

    @Test
    void shouldReserveAnAvailableActiveCopy() {
        var id = saveCopy(CopyStatus.AVAILABLE, true);

        service.reserve(id);

        assertThat(statusOf(id)).isEqualTo(CopyStatus.RESERVED);
        assertThat(service.findAvailability(id).orElseThrow().lendable()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"BORROWED", "RESERVED", "DAMAGED", "LOST"})
    void shouldNotReserveACopyThatIsNotAvailable(CopyStatus status) {
        var id = saveCopy(status, true);

        assertThatThrownBy(() -> service.reserve(id)).isInstanceOf(BusinessRuleException.class);
        assertThat(statusOf(id)).isEqualTo(status);
    }

    @Test
    void shouldNotReserveAnInactiveCopy() {
        var id = saveCopy(CopyStatus.AVAILABLE, false);

        assertThatThrownBy(() -> service.reserve(id)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotReserveACopyThatDoesNotExist() {
        assertThatThrownBy(() -> service.reserve(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldReleaseAReservedCopy() {
        var id = saveCopy(CopyStatus.RESERVED, true);

        service.releaseReservation(id);

        assertThat(statusOf(id)).isEqualTo(CopyStatus.AVAILABLE);
        assertThat(service.findAvailability(id).orElseThrow().lendable()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"AVAILABLE", "BORROWED", "DAMAGED", "LOST"})
    void shouldNotReleaseACopyThatIsNotReserved(CopyStatus status) {
        var id = saveCopy(status, true);

        assertThatThrownBy(() -> service.releaseReservation(id)).isInstanceOf(BusinessRuleException.class);
        assertThat(statusOf(id)).isEqualTo(status);
    }

    @Test
    void shouldNotReleaseACopyThatDoesNotExist() {
        assertThatThrownBy(() -> service.releaseReservation(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldCheckOutAReservedCopy() {
        var id = saveCopy(CopyStatus.RESERVED, true);

        service.checkOutReserved(id);

        assertThat(statusOf(id)).isEqualTo(CopyStatus.BORROWED);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = {"AVAILABLE", "BORROWED", "DAMAGED", "LOST"})
    void shouldNotCheckOutAsReservedACopyThatIsNotReserved(CopyStatus status) {
        var id = saveCopy(status, true);

        assertThatThrownBy(() -> service.checkOutReserved(id)).isInstanceOf(BusinessRuleException.class);
        assertThat(statusOf(id)).isEqualTo(status);
    }

    @Test
    void shouldNotCheckOutAsReservedACopyThatDoesNotExist() {
        assertThatThrownBy(() -> service.checkOutReserved(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theOrdinaryCheckOutStillRefusesAReservedCopy() {
        var id = saveCopy(CopyStatus.RESERVED, true);

        assertThatThrownBy(() -> service.checkOut(id)).isInstanceOf(BusinessRuleException.class);
        assertThat(statusOf(id)).isEqualTo(CopyStatus.RESERVED);
    }

    @Test
    void aCopyGoesThroughTheWholeCycleOfAReservation() {
        var id = saveCopy(CopyStatus.AVAILABLE, true);

        service.reserve(id);
        service.releaseReservation(id);
        service.reserve(id);
        service.checkOutReserved(id);
        service.checkIn(id);

        assertThat(statusOf(id)).isEqualTo(CopyStatus.AVAILABLE);
    }

    // does the book have a copy to lend? (RN12)

    @Test
    void aBookWithAnAvailableActiveCopyShouldHaveAvailability() {
        var book = saveBook("9788533302273", "Dom Casmurro");
        saveCopyOf(book, "PAT-1");

        assertThat(service.findBookAvailability(book)).hasValueSatisfying(availability -> {
            assertThat(availability.bookId()).isEqualTo(book);
            assertThat(availability.hasAvailableCopy()).isTrue();
        });
    }

    @Test
    void aBookWhoseCopiesAreNotAvailableShouldHaveNoAvailability() {
        var book = saveBook("9788533302273", "Dom Casmurro");
        for (var status : new CopyStatus[] {CopyStatus.BORROWED, CopyStatus.RESERVED, CopyStatus.DAMAGED,
                CopyStatus.LOST}) {
            copyRepository.save(Copy.reconstitute(null, book, "PAT-" + status, status, true, 0L));
        }
        copyRepository.save(Copy.reconstitute(null, book, "PAT-OFF", CopyStatus.AVAILABLE, false, 0L));

        assertThat(service.findBookAvailability(book).orElseThrow().hasAvailableCopy()).isFalse();
    }

    @Test
    void aBookWithNoCopiesShouldHaveNoAvailability() {
        var book = saveBook("9788533302273", "Dom Casmurro");

        assertThat(service.findBookAvailability(book).orElseThrow().hasAvailableCopy()).isFalse();
    }

    @Test
    void shouldAnswerNothingForABookThatDoesNotExist() {
        assertThat(service.findBookAvailability(999L)).isEmpty();
    }

    @Test
    void reservingTheLastAvailableCopyMakesTheBookUnavailable() {
        var book = saveBook("9788533302273", "Dom Casmurro");
        var copy = saveCopyOf(book, "PAT-1");

        service.reserve(copy);

        assertThat(service.findBookAvailability(book).orElseThrow().hasAvailableCopy()).isFalse();
    }
}
