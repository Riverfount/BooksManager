package com.riverfount.booksmanager.loan.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.riverfount.booksmanager.loan.application.port.out.SuspensionFilter;
import com.riverfount.booksmanager.loan.domain.Suspension;

/**
 * Tests the Suspension persistence adapter against a real PostgreSQL (Testcontainers):
 * the explicit mapping, the early end with its justification (RN15), the "in force on a
 * day" filter agreeing with the domain, and the paginated listing (RNF14).
 */
@Import({TestcontainersConfiguration.class, SuspensionPersistenceAdapter.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class SuspensionPersistenceAdapterTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 18);

    @Autowired
    private SuspensionPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private LoanTestData data;
    private long readerId;
    private long loanId;

    @BeforeEach
    void createParents() {
        data = new LoanTestData(jdbcTemplate);
        readerId = data.newReader();
        loanId = data.newReturnedLoan(data.newCopy(), readerId, LocalDate.of(2026, 11, 1));
    }

    private Suspension saveSuspension(int days) {
        return adapter.save(Suspension.create(readerId, loanId, START, days));
    }

    private void clearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    private static PageQuery byId() {
        return PageQuery.of(0, 20, "id");
    }

    @Test
    void shouldSaveAndFindASuspensionWithTheSameState() {
        var saved = saveSuspension(3);
        clearPersistenceContext();

        var found = adapter.findById(saved.getId()).orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(found.getReaderId()).isEqualTo(readerId);
        assertThat(found.getLoanId()).isEqualTo(loanId);
        assertThat(found.getStartDate()).isEqualTo(START);
        assertThat(found.getEndDate()).isEqualTo(START.plusDays(3));
        assertThat(found.getEndedEarlyOn()).isNull();
        assertThat(found.getEndedEarlyReason()).isNull();
    }

    @Test
    void shouldNotFindAnUnknownSuspension() {
        assertThat(adapter.findById(-1L)).isEmpty();
    }

    @Test
    void shouldSaveAnEarlyEndWithItsJustification() {
        var suspension = saveSuspension(5);
        suspension.endEarly(START.plusDays(1), "Pardoned by the librarian");
        adapter.save(suspension);
        clearPersistenceContext();

        var found = adapter.findById(suspension.getId()).orElseThrow();

        assertThat(found.getEndedEarlyOn()).isEqualTo(START.plusDays(1));
        assertThat(found.getEndedEarlyReason()).isEqualTo("Pardoned by the librarian");
        assertThat(found.getEndDate()).isEqualTo(START.plusDays(5));
    }

    @Test
    void shouldKeepTheIdOfASavedSuspensionInsteadOfCreatingAnother() {
        var suspension = saveSuspension(5);
        suspension.endEarly(START.plusDays(1), "Pardoned");
        adapter.save(suspension);
        clearPersistenceContext();

        assertThat(adapter.findAll(SuspensionFilter.none(), byId()).totalItems()).isEqualTo(1);
    }

    @Test
    void shouldRefuseToSaveASuspensionThatDoesNotExistInTheDatabase() {
        var ghost = Suspension.reconstitute(-5L, readerId, loanId, START, START.plusDays(3), null, null);

        assertThatThrownBy(() -> adapter.save(ghost)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldListTheSuspensionsOfAReaderAndOfALoan() {
        var mine = saveSuspension(3);
        var otherLoan = data.newReturnedLoan(data.newCopy(), data.newReader(), LocalDate.of(2026, 11, 1));
        adapter.save(Suspension.create(data.newReader(), otherLoan, START, 3));

        assertThat(adapter.findAll(new SuspensionFilter(readerId, null, null), byId()).items())
                .extracting(Suspension::getId).containsExactly(mine.getId());
        assertThat(adapter.findAll(new SuspensionFilter(null, loanId, null), byId()).items())
                .extracting(Suspension::getId).containsExactly(mine.getId());
    }

    @Test
    void shouldFilterTheOnesInForceOnADayTheSameWayTheDomainDoes() {
        var running = saveSuspension(3);
        var endedEarly = adapter.save(Suspension.create(readerId, loanId, START, 10));
        endedEarly.endEarly(START.plusDays(1), "Pardoned");
        adapter.save(endedEarly);
        clearPersistenceContext();
        var all = adapter.findAll(new SuspensionFilter(readerId, null, null), byId()).items();

        for (var offset = -1; offset <= 11; offset++) {
            var day = START.plusDays(offset);
            var expected = all.stream().filter(s -> s.isInForce(day)).map(Suspension::getId).toList();

            var inForce = adapter.findAll(new SuspensionFilter(readerId, null, day), byId()).items();

            assertThat(inForce).as("day %s", day).extracting(Suspension::getId)
                    .containsExactlyElementsOf(expected);
        }
        assertThat(adapter.findAll(new SuspensionFilter(readerId, null, START), byId()).items())
                .extracting(Suspension::getId).containsExactly(running.getId(), endedEarly.getId());
    }

    @Test
    void shouldFindTheSuspensionsOfSeveralLoansInOneCall() {
        var otherLoan = data.newReturnedLoan(data.newCopy(), readerId, LocalDate.of(2026, 11, 1));
        var withoutSuspension = data.newReturnedLoan(data.newCopy(), readerId, LocalDate.of(2026, 11, 1));
        var first = saveSuspension(3);
        var second = adapter.save(Suspension.create(readerId, otherLoan, START, 5));

        var found = adapter.findByLoanIds(java.util.Set.of(loanId, otherLoan, withoutSuspension));

        assertThat(found).containsOnlyKeys(loanId, otherLoan);
        assertThat(found.get(loanId).getId()).isEqualTo(first.getId());
        assertThat(found.get(otherLoan).getId()).isEqualTo(second.getId());
    }

    @Test
    void shouldKeepTheLatestWhenALoanHasMoreThanOneSuspension() {
        saveSuspension(3);
        var latest = saveSuspension(9);

        var found = adapter.findByLoanIds(java.util.Set.of(loanId));

        assertThat(found.get(loanId).getId()).isEqualTo(latest.getId());
    }

    @Test
    void shouldFindNothingForNoLoans() {
        assertThat(adapter.findByLoanIds(java.util.Set.of())).isEmpty();
    }

    @Test
    void shouldPaginateAndSort() {
        for (var days : new int[] {3, 1, 2}) {
            saveSuspension(days);
        }

        var page = adapter.findAll(new SuspensionFilter(readerId, null, null), PageQuery.of(0, 2, "endDate,desc"));

        assertThat(page.items()).extracting(Suspension::getEndDate)
                .containsExactly(START.plusDays(3), START.plusDays(2));
        assertThat(page.totalItems()).isEqualTo(3);
    }
}
