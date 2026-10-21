package com.riverfount.booksmanager.loan.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import com.riverfount.booksmanager.loan.application.port.out.ReservationFilter;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests the Reservation persistence adapter against a real PostgreSQL (Testcontainers): the explicit
 * mapping between the domain and the JPA entity, the two partial unique indexes turned into business
 * rules (RN12), the queue of a book in order of arrival, and the paginated listing (RNF14).
 */
@PersistenceTest
class ReservationPersistenceAdapterTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);

    @Autowired
    private ReservationPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private ReservationTestData data;
    private long bookId;
    private long readerId;

    @BeforeEach
    void createParents() {
        data = new ReservationTestData(jdbcTemplate);
        bookId = data.newBook();
        readerId = data.newReader();
    }

    private Reservation reserve(long book, long reader) {
        return adapter.save(Reservation.create(book, reader, DAY));
    }

    private Reservation reserveAndHold(long book, long reader, long copy, LocalDate assignedOn, int days) {
        var reservation = Reservation.create(book, reader, assignedOn);
        reservation.assignCopy(copy, assignedOn, days);
        return adapter.save(reservation);
    }

    private void clearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    private static PageQuery byId() {
        return PageQuery.of(0, 20, "id");
    }

    // mapping

    @Test
    void shouldSaveAndFindAWaitingReservationWithTheSameState() {
        var saved = reserve(bookId, readerId);
        clearPersistenceContext();

        var found = adapter.findById(saved.getId()).orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(found.getBookId()).isEqualTo(bookId);
        assertThat(found.getReaderId()).isEqualTo(readerId);
        assertThat(found.getReservationDate()).isEqualTo(DAY);
        assertThat(found.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(found.getCopyId()).isNull();
        assertThat(found.getPickupDeadline()).isNull();
        assertThat(found.isWaiting()).isTrue();
    }

    @Test
    void shouldNotFindAnUnknownReservation() {
        assertThat(adapter.findById(-1L)).isEmpty();
    }

    @Test
    void shouldSaveTheCopyAndTheDeadlineWhenTheReservationStartsHoldingOne() {
        var reservation = reserve(bookId, readerId);
        var copyId = data.newCopy(bookId);
        reservation.assignCopy(copyId, DAY.plusDays(2), 3);
        adapter.save(reservation);
        clearPersistenceContext();

        var found = adapter.findById(reservation.getId()).orElseThrow();

        assertThat(found.getCopyId()).isEqualTo(copyId);
        assertThat(found.getPickupDeadline()).isEqualTo(DAY.plusDays(5));
        assertThat(found.isHoldingCopy()).isTrue();
    }

    @Test
    void shouldSaveEachFinalStatusAndKeepTheCopyForTheHistory() {
        var fulfilled = reserveAndHold(bookId, readerId, data.newCopy(bookId), DAY, 3);
        fulfilled.fulfill(DAY.plusDays(1));
        adapter.save(fulfilled);
        var expired = reserveAndHold(bookId, data.newReader(), data.newCopy(bookId), DAY, 3);
        expired.expire(DAY.plusDays(4));
        adapter.save(expired);
        var cancelled = reserve(bookId, data.newReader());
        cancelled.cancel();
        adapter.save(cancelled);
        clearPersistenceContext();

        assertThat(adapter.findById(fulfilled.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.FULFILLED);
        var foundExpired = adapter.findById(expired.getId()).orElseThrow();
        assertThat(foundExpired.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(foundExpired.getCopyId()).isEqualTo(expired.getCopyId());
        assertThat(adapter.findById(cancelled.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void shouldKeepTheIdOfASavedReservationInsteadOfCreatingAnother() {
        var reservation = reserve(bookId, readerId);
        reservation.cancel();
        adapter.save(reservation);
        clearPersistenceContext();

        assertThat(adapter.findAll(ReservationFilter.none(), byId()).totalItems()).isEqualTo(1);
    }

    @Test
    void shouldCountTheVersionOfEachChange() {
        var reservation = reserve(bookId, readerId);
        reservation.assignCopy(data.newCopy(bookId), DAY, 3);
        adapter.save(reservation);

        var version = jdbcTemplate.queryForObject("select version from reservation where id = ?", Long.class,
                reservation.getId());

        assertThat(version).isEqualTo(1L);
    }

    @Test
    void shouldRefuseToSaveAReservationThatDoesNotExistInTheDatabase() {
        var ghost = Reservation.reconstitute(-5L, bookId, readerId, DAY, ReservationStatus.ACTIVE, null, null);

        assertThatThrownBy(() -> adapter.save(ghost)).isInstanceOf(IllegalStateException.class);
    }

    // the indexes turned into business rules (RN12)

    @Test
    void shouldTranslateASecondActiveReservationOfTheSameBookByTheSameReaderIntoABusinessRule() {
        reserve(bookId, readerId);

        assertThatThrownBy(() -> reserve(bookId, readerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reserva ativa deste livro");
    }

    @Test
    void shouldAllowANewReservationOfTheBookOnceThePreviousOneIsClosed() {
        var first = reserve(bookId, readerId);
        first.cancel();
        adapter.save(first);

        var second = reserve(bookId, readerId);

        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void shouldTranslateTwoActiveReservationsHoldingTheSameCopyIntoABusinessRule() {
        var copyId = data.newCopy(bookId);
        reserveAndHold(bookId, readerId, copyId, DAY, 3);

        assertThatThrownBy(() -> reserveAndHold(bookId, data.newReader(), copyId, DAY, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exemplar");
    }

    @Test
    void shouldNotTranslateAnUnknownBookIntoABusinessRule() {
        assertThatThrownBy(() -> reserve(-1L, readerId))
                .isNotInstanceOf(BusinessRuleException.class)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // the queue of a book: in order of arrival

    @Test
    void theFirstOfTheQueueIsTheOldestReservationStillWaiting() {
        var first = reserve(bookId, readerId);
        reserve(bookId, data.newReader());
        reserve(bookId, data.newReader());
        clearPersistenceContext();

        assertThat(adapter.findFirstWaiting(bookId)).map(Reservation::getId).contains(first.getId());
    }

    @Test
    void theQueueMovesWhenTheFirstOneIsServedCancelledOrExpired() {
        var first = reserve(bookId, readerId);
        var second = reserve(bookId, data.newReader());
        var third = reserve(bookId, data.newReader());

        first.assignCopy(data.newCopy(bookId), DAY, 3);
        adapter.save(first);
        assertThat(adapter.findFirstWaiting(bookId)).map(Reservation::getId).contains(second.getId());

        second.cancel();
        adapter.save(second);
        assertThat(adapter.findFirstWaiting(bookId)).map(Reservation::getId).contains(third.getId());

        third.cancel();
        adapter.save(third);
        assertThat(adapter.findFirstWaiting(bookId)).isEmpty();
    }

    @Test
    void theQueueOfOneBookIgnoresTheReservationsOfAnother() {
        var otherBook = data.newBook();
        reserve(otherBook, readerId);
        var mine = reserve(bookId, readerId);

        assertThat(adapter.findFirstWaiting(bookId)).map(Reservation::getId).contains(mine.getId());
        assertThat(adapter.findFirstWaiting(-1L)).isEmpty();
    }

    @Test
    void shouldGiveThePositionInTheQueueCountingFromOne() {
        var first = reserve(bookId, readerId);
        var second = reserve(bookId, data.newReader());
        var third = reserve(bookId, data.newReader());
        reserve(data.newBook(), data.newReader());

        assertThat(adapter.queuePosition(first.getId())).contains(1L);
        assertThat(adapter.queuePosition(second.getId())).contains(2L);
        assertThat(adapter.queuePosition(third.getId())).contains(3L);

        first.cancel();
        adapter.save(first);

        assertThat(adapter.queuePosition(second.getId())).contains(1L);
        assertThat(adapter.queuePosition(third.getId())).contains(2L);
    }

    @Test
    void thereIsNoPositionForAReservationThatIsNotWaiting() {
        var holding = reserveAndHold(bookId, readerId, data.newCopy(bookId), DAY, 3);
        var cancelled = reserve(bookId, data.newReader());
        cancelled.cancel();
        adapter.save(cancelled);

        assertThat(adapter.queuePosition(holding.getId())).isEmpty();
        assertThat(adapter.queuePosition(cancelled.getId())).isEmpty();
        assertThat(adapter.queuePosition(-1L)).isEmpty();
    }

    @Test
    void shouldGiveTheQueuePositionsOfSeveralBooksInOneQuery() {
        var otherBook = data.newBook();
        var first = reserve(bookId, readerId);
        var second = reserve(bookId, data.newReader());
        var otherFirst = reserve(otherBook, data.newReader());
        var holding = reserveAndHold(bookId, data.newReader(), data.newCopy(bookId), DAY, 3);
        var cancelled = reserve(otherBook, data.newReader());
        cancelled.cancel();
        adapter.save(cancelled);
        var otherSecond = reserve(otherBook, data.newReader());

        var ids = java.util.Set.of(first.getId(), second.getId(), otherFirst.getId(), otherSecond.getId(),
                holding.getId(), cancelled.getId(), -1L);

        assertThat(adapter.queuePositions(ids)).containsOnly(
                java.util.Map.entry(first.getId(), 1L), java.util.Map.entry(second.getId(), 2L),
                java.util.Map.entry(otherFirst.getId(), 1L), java.util.Map.entry(otherSecond.getId(), 2L));
        assertThat(adapter.queuePositions(java.util.Set.of())).isEmpty();
    }

    @Test
    void shouldFindTheActiveReservationThatHoldsACopy() {
        var copyId = data.newCopy(bookId);
        var holding = reserveAndHold(bookId, readerId, copyId, DAY, 3);
        reserve(bookId, data.newReader());

        assertThat(adapter.findActiveByCopyId(copyId)).map(Reservation::getId).contains(holding.getId());
        assertThat(adapter.findActiveByCopyId(data.newCopy(bookId))).isEmpty();
        assertThat(adapter.findActiveByCopyId(-1L)).isEmpty();

        holding.cancel();
        adapter.save(holding);
        assertThat(adapter.findActiveByCopyId(copyId)).isEmpty();
    }

    // count of active reservations of a reader (RN12)

    @Test
    void shouldCountTheActiveReservationsOfAReaderWaitingOrHoldingACopy() {
        reserve(bookId, readerId);
        reserveAndHold(data.newBook(), readerId, data.newCopy(bookId), DAY, 3);
        var closed = reserve(data.newBook(), readerId);
        closed.cancel();
        adapter.save(closed);
        reserve(bookId, data.newReader());

        assertThat(adapter.countActiveByReader(readerId)).isEqualTo(2L);
        assertThat(adapter.countActiveByReader(-1L)).isZero();
    }

    // past the deadline (RF19)

    @Test
    void shouldFindTheReservationsPastTheirDeadlineOldestFirst() {
        var lastDay = reserveAndHold(bookId, readerId, data.newCopy(bookId), DAY, 3);              // deadline 11-04
        var late = reserveAndHold(bookId, data.newReader(), data.newCopy(bookId), DAY.minusDays(5), 3);  // 10-30
        var later = reserveAndHold(bookId, data.newReader(), data.newCopy(bookId), DAY.minusDays(2), 3); // 11-02
        reserve(bookId, data.newReader());                                                            // waiting
        var cancelled = reserveAndHold(bookId, data.newReader(), data.newCopy(bookId), DAY.minusDays(9), 3);
        cancelled.cancel();
        adapter.save(cancelled);
        clearPersistenceContext();

        var past = adapter.findPastDeadline(LocalDate.of(2026, 11, 4));
        var dayAfter = adapter.findPastDeadline(LocalDate.of(2026, 11, 5));

        assertThat(past).extracting(Reservation::getId).containsExactly(late.getId(), later.getId());
        assertThat(dayAfter).extracting(Reservation::getId)
                .containsExactly(late.getId(), later.getId(), lastDay.getId());
    }

    // listing (RNF14)

    @Test
    void shouldListTheReservationsOfAReader() {
        var mine = reserve(bookId, readerId);
        reserve(bookId, data.newReader());

        var page = adapter.findAll(new ReservationFilter(readerId, null, null), byId());

        assertThat(page.items()).extracting(Reservation::getId).containsExactly(mine.getId());
    }

    @Test
    void shouldListTheReservationsOfABookAndFilterByStatus() {
        var waiting = reserve(bookId, readerId);
        var cancelled = reserve(bookId, data.newReader());
        cancelled.cancel();
        adapter.save(cancelled);
        reserve(data.newBook(), readerId);

        assertThat(adapter.findAll(new ReservationFilter(null, bookId, null), byId()).items())
                .extracting(Reservation::getId).containsExactly(waiting.getId(), cancelled.getId());
        assertThat(adapter.findAll(new ReservationFilter(null, bookId, ReservationStatus.ACTIVE), byId()).items())
                .extracting(Reservation::getId).containsExactly(waiting.getId());
        assertThat(adapter.findAll(new ReservationFilter(readerId, bookId, ReservationStatus.CANCELLED), byId())
                .items()).isEmpty();
    }

    @Test
    void shouldPaginateAndSort() {
        for (var day = 0; day < 3; day++) {
            adapter.save(Reservation.create(data.newBook(), readerId, DAY.plusDays(day)));
        }

        var page = adapter.findAll(new ReservationFilter(readerId, null, null),
                PageQuery.of(0, 2, "reservationDate,desc"));

        assertThat(page.items()).extracting(Reservation::getReservationDate)
                .containsExactly(DAY.plusDays(2), DAY.plusDays(1));
        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(2);
    }

    @Test
    void shouldSortByPickupDeadline() {
        var later = reserveAndHold(bookId, readerId, data.newCopy(bookId), DAY.plusDays(4), 3);
        var sooner = reserveAndHold(bookId, data.newReader(), data.newCopy(bookId), DAY, 3);

        var page = adapter.findAll(new ReservationFilter(null, bookId, null), PageQuery.of(0, 10, "pickupDeadline"));

        assertThat(page.items()).extracting(Reservation::getId).containsExactly(sooner.getId(), later.getId());
    }
}
