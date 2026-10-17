package com.riverfount.booksmanager.loan.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.out.LoanFilter;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
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

        var page = adapter.findAll(new LoanFilter(readerId, null, null), byId());

        assertThat(page.items()).extracting(Loan::getId).containsExactly(mine.getId());
    }

    @Test
    void shouldListTheLoansOfACopyIncludingTheReturnedOnes() {
        var first = saveLoan(copyId, readerId);
        first.returnCopy(PICKUP.plusDays(5));
        adapter.save(first);
        var second = adapter.save(Loan.create(copyId, readerId, PICKUP.plusDays(6), 14));
        saveLoan(data.newCopy(), readerId);

        var page = adapter.findAll(new LoanFilter(null, copyId, null), byId());

        assertThat(page.items()).extracting(Loan::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void shouldFilterBySituation() {
        var returned = saveLoan(copyId, readerId);
        returned.returnCopy(PICKUP.plusDays(5));
        adapter.save(returned);
        var active = saveLoan(data.newCopy(), readerId);

        assertThat(adapter.findAll(new LoanFilter(readerId, null, true), byId()).items())
                .extracting(Loan::getId).containsExactly(active.getId());
        assertThat(adapter.findAll(new LoanFilter(readerId, null, false), byId()).items())
                .extracting(Loan::getId).containsExactly(returned.getId());
    }

    @Test
    void shouldPaginateAndSort() {
        for (var day = 0; day < 3; day++) {
            adapter.save(Loan.create(data.newCopy(), readerId, PICKUP.plusDays(day), 14));
        }

        var page = adapter.findAll(new LoanFilter(readerId, null, null),
                PageQuery.of(0, 2, "pickupDate,desc"));

        assertThat(page.items()).extracting(Loan::getPickupDate)
                .containsExactly(PICKUP.plusDays(2), PICKUP.plusDays(1));
        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(2);
    }
}
