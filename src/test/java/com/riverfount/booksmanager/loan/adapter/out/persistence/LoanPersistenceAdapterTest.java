package com.riverfount.booksmanager.loan.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.out.LoanFilter;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.ReturnCondition;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests the Loan persistence adapter against a real PostgreSQL (Testcontainers): the
 * explicit mapping between the domain and the JPA entity, the single active loan per
 * copy (RN03, RNF17) turned into a business rule, and the paginated listing (RNF14).
 */
@Import({TestcontainersConfiguration.class, LoanPersistenceAdapter.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class LoanPersistenceAdapterTest {

    private static final LocalDate PICKUP = LocalDate.of(2026, 11, 1);

    @Autowired
    private LoanPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private LoanTestData data;
    private long readerId;
    private long copyId;

    @BeforeEach
    void createParents() {
        data = new LoanTestData(jdbcTemplate);
        readerId = data.newReader();
        copyId = data.newCopy();
    }

    private Loan saveLoan(long copy, long reader) {
        return adapter.save(Loan.create(copy, reader, PICKUP, 14));
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
    void shouldSaveAndFindALoanWithTheSameState() {
        var saved = saveLoan(copyId, readerId);
        clearPersistenceContext();

        var found = adapter.findById(saved.getId()).orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(found.getCopyId()).isEqualTo(copyId);
        assertThat(found.getReaderId()).isEqualTo(readerId);
        assertThat(found.getPickupDate()).isEqualTo(PICKUP);
        assertThat(found.getDueDate()).isEqualTo(PICKUP.plusDays(14));
        assertThat(found.getReturnDate()).isNull();
        assertThat(found.getRenewals()).isZero();
        assertThat(found.isActive()).isTrue();
    }

    @Test
    void theCountShouldAgreeWithTheTotalOfTheListingForEveryFilter() {
        var returned = adapter.save(Loan.create(copyId, readerId, LocalDate.of(2026, 1, 1), 14));
        returned.returnCopy(LocalDate.of(2026, 1, 5));
        adapter.save(returned);
        var secondCopy = data.newCopy();
        adapter.save(Loan.create(secondCopy, readerId, LocalDate.of(2026, 1, 1), 14));
        var thirdCopy = data.newCopy();
        adapter.save(Loan.create(thirdCopy, readerId, LocalDate.of(2026, 3, 1), 14));
        clearPersistenceContext();

        var day = LocalDate.of(2026, 2, 1);
        for (var filter : new LoanFilter[] {LoanFilter.none(), new LoanFilter(readerId, null, true, null),
                new LoanFilter(readerId, null, false, null), new LoanFilter(null, secondCopy, null, null),
                new LoanFilter(null, null, true, day), new LoanFilter(null, null, null, day),
                new LoanFilter(-1L, null, null, null)}) {
            assertThat(adapter.count(filter)).as(filter.toString())
                    .isEqualTo(adapter.findAll(filter, byId()).totalItems());
        }
        assertThat(adapter.count(new LoanFilter(null, null, true, day))).isEqualTo(1L);
    }

    @Test
    void shouldNotFindAnUnknownLoan() {
        assertThat(adapter.findById(-1L)).isEmpty();
    }

    @Test
    void shouldSaveARenewal() {
        var loan = saveLoan(copyId, readerId);
        loan.renew(PICKUP.plusDays(3), 2, 14);
        adapter.save(loan);
        clearPersistenceContext();

        var found = adapter.findById(loan.getId()).orElseThrow();

        assertThat(found.getRenewals()).isEqualTo(1);
        assertThat(found.getDueDate()).isEqualTo(PICKUP.plusDays(28));
    }

    @Test
    void shouldSaveAReturn() {
        var loan = saveLoan(copyId, readerId);
        loan.returnCopy(PICKUP.plusDays(10));
        adapter.save(loan);
        clearPersistenceContext();

        var found = adapter.findById(loan.getId()).orElseThrow();

        assertThat(found.getReturnDate()).isEqualTo(PICKUP.plusDays(10));
        assertThat(found.isReturned()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ReturnCondition.class)
    void shouldSaveAndReadBackTheConditionOfTheReturn(ReturnCondition condition) {
        var loan = saveLoan(copyId, readerId);
        loan.returnCopy(PICKUP.plusDays(10), condition);
        adapter.save(loan);
        clearPersistenceContext();

        var found = adapter.findById(loan.getId()).orElseThrow();

        assertThat(found.getReturnCondition()).isEqualTo(condition);
        assertThat(found.getReturnDate()).isEqualTo(PICKUP.plusDays(10));
    }

    @Test
    void anActiveLoanIsReadBackWithoutCondition() {
        var loan = saveLoan(copyId, readerId);
        clearPersistenceContext();

        assertThat(adapter.findById(loan.getId()).orElseThrow().getReturnCondition()).isNull();
    }

    @Test
    void shouldCreateTheLoanAlreadyReturnedWithItsCondition() {
        var returned = Loan.reconstitute(null, copyId, readerId, PICKUP, PICKUP.plusDays(14), PICKUP.plusDays(3), 0,
                ReturnCondition.LOST);

        var saved = adapter.save(returned);
        clearPersistenceContext();

        assertThat(adapter.findById(saved.getId()).orElseThrow().getReturnCondition())
                .isEqualTo(ReturnCondition.LOST);
    }

    @Test
    void shouldKeepTheIdOfASavedLoanInsteadOfCreatingAnother() {
        var loan = saveLoan(copyId, readerId);
        loan.returnCopy(PICKUP.plusDays(10));
        adapter.save(loan);
        clearPersistenceContext();

        assertThat(adapter.findAll(LoanFilter.none(), byId()).totalItems()).isEqualTo(1);
    }

    @Test
    void shouldCountTheVersionOfEachChange() {
        var loan = saveLoan(copyId, readerId);
        loan.renew(PICKUP.plusDays(3), 2, 14);
        adapter.save(loan);

        var version = jdbcTemplate.queryForObject("select version from loan where id = ?", Long.class, loan.getId());

        assertThat(version).isEqualTo(1L);
    }

    @Test
    void shouldRefuseToSaveALoanThatDoesNotExistInTheDatabase() {
        var ghost = Loan.reconstitute(-5L, copyId, readerId, PICKUP, PICKUP.plusDays(14), null, 0);

        assertThatThrownBy(() -> adapter.save(ghost)).isInstanceOf(IllegalStateException.class);
    }

    // one active loan per copy (RN03, RNF17)

    @Test
    void shouldTranslateASecondActiveLoanOfTheSameCopyIntoABusinessRule() {
        saveLoan(copyId, readerId);

        assertThatThrownBy(() -> saveLoan(copyId, data.newReader()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empréstimo ativo");
    }

    @Test
    void shouldAllowANewLoanOfTheCopyOnceItWasReturned() {
        var first = saveLoan(copyId, readerId);
        first.returnCopy(PICKUP.plusDays(5));
        adapter.save(first);

        var second = adapter.save(Loan.create(copyId, data.newReader(), PICKUP.plusDays(6), 14));

        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void shouldNotTranslateAnUnknownCopyIntoABusinessRule() {
        assertThatThrownBy(() -> saveLoan(-1L, readerId))
                .isNotInstanceOf(BusinessRuleException.class)
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // listing (RF14, RNF14)

    @Test
    void shouldListTheLoansOfAReader() {
        var mine = saveLoan(copyId, readerId);
        saveLoan(data.newCopy(), data.newReader());

        var page = adapter.findAll(new LoanFilter(readerId, null, null, null), byId());

        assertThat(page.items()).extracting(Loan::getId).containsExactly(mine.getId());
    }

    @Test
    void shouldListTheLoansOfACopyIncludingTheReturnedOnes() {
        var first = saveLoan(copyId, readerId);
        first.returnCopy(PICKUP.plusDays(5));
        adapter.save(first);
        var second = adapter.save(Loan.create(copyId, readerId, PICKUP.plusDays(6), 14));
        saveLoan(data.newCopy(), readerId);

        var page = adapter.findAll(new LoanFilter(null, copyId, null, null), byId());

        assertThat(page.items()).extracting(Loan::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void shouldFilterBySituation() {
        var returned = saveLoan(copyId, readerId);
        returned.returnCopy(PICKUP.plusDays(5));
        adapter.save(returned);
        var active = saveLoan(data.newCopy(), readerId);

        assertThat(adapter.findAll(new LoanFilter(readerId, null, true, null), byId()).items())
                .extracting(Loan::getId).containsExactly(active.getId());
        assertThat(adapter.findAll(new LoanFilter(readerId, null, false, null), byId()).items())
                .extracting(Loan::getId).containsExactly(returned.getId());
    }

    @Test
    void shouldFilterTheOverdueLoansTheSameWayTheDomainDoes() {
        var onTime = adapter.save(Loan.create(copyId, readerId, PICKUP, 14));
        var late = adapter.save(Loan.create(data.newCopy(), readerId, PICKUP.minusDays(20), 14));
        var lateButReturned = adapter.save(Loan.create(data.newCopy(), readerId, PICKUP.minusDays(20), 14));
        lateButReturned.returnCopy(PICKUP);
        adapter.save(lateButReturned);
        clearPersistenceContext();
        var all = adapter.findAll(new LoanFilter(readerId, null, null, null), byId()).items();

        for (var offset = -1; offset <= 1; offset++) {
            var day = onTime.getDueDate().plusDays(offset);
            var expected = all.stream().filter(loan -> loan.isOverdue(day)).map(Loan::getId).toList();

            var overdue = adapter.findAll(new LoanFilter(readerId, null, null, day), byId()).items();

            assertThat(overdue).as("day %s", day).extracting(Loan::getId).containsExactlyElementsOf(expected);
        }
        assertThat(adapter.findAll(new LoanFilter(readerId, null, null, PICKUP), byId()).items())
                .extracting(Loan::getId).containsExactly(late.getId());
    }

    @Test
    void theOverdueLoansSortedByDueDateShouldComeFromTheMostToTheLeastLate() {
        var today = PICKUP.plusDays(60);
        var least = adapter.save(Loan.create(data.newCopy(), readerId, today.minusDays(16), 14));   // 2 days late
        var most = adapter.save(Loan.create(data.newCopy(), readerId, today.minusDays(40), 14));    // 26 days late
        var middle = adapter.save(Loan.create(data.newCopy(), readerId, today.minusDays(25), 14));  // 11 days late
        adapter.save(Loan.create(data.newCopy(), readerId, today.minusDays(14), 14));               // due today
        clearPersistenceContext();

        var page = adapter.findAll(new LoanFilter(null, null, null, today), PageQuery.of(0, 2, "dueDate"));
        var next = adapter.findAll(new LoanFilter(null, null, null, today), PageQuery.of(1, 2, "dueDate"));

        assertThat(page.items()).extracting(Loan::getId).containsExactly(most.getId(), middle.getId());
        assertThat(next.items()).extracting(Loan::getId).containsExactly(least.getId());
        assertThat(page.totalItems()).isEqualTo(3);
    }

    @Test
    void shouldPaginateAndSort() {
        for (var day = 0; day < 3; day++) {
            adapter.save(Loan.create(data.newCopy(), readerId, PICKUP.plusDays(day), 14));
        }

        var page = adapter.findAll(new LoanFilter(readerId, null, null, null),
                PageQuery.of(0, 2, "pickupDate,desc"));

        assertThat(page.items()).extracting(Loan::getPickupDate)
                .containsExactly(PICKUP.plusDays(2), PICKUP.plusDays(1));
        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(2);
    }

    @Test
    void shouldFindTheCopiesOfTheActiveLoansOfAReaderOnly() {
        var otherCopy = data.newCopy();
        var returnedCopy = data.newCopy();
        saveLoan(copyId, readerId);
        saveLoan(otherCopy, readerId);
        var returned = saveLoan(returnedCopy, readerId);
        returned.returnCopy(PICKUP.plusDays(2));
        adapter.save(returned);
        saveLoan(data.newCopy(), data.newReader());
        clearPersistenceContext();

        assertThat(adapter.findActiveCopyIdsByReader(readerId)).containsExactlyInAnyOrder(copyId, otherCopy);
        assertThat(adapter.findActiveCopyIdsByReader(-1L)).isEmpty();
    }
}
