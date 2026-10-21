package com.riverfount.booksmanager.loan.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.riverfount.booksmanager.loan.application.port.out.FineFilter;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import java.math.BigDecimal;

/**
 * Tests the Fine persistence adapter against a real PostgreSQL (Testcontainers): the
 * explicit mapping, payment and waiver (RF21), one fine per loan (RN05) turned into a
 * business rule, and the paginated listing (RNF14).
 */
@PersistenceTest
class FinePersistenceAdapterTest {

    private static final LocalDate PICKUP = LocalDate.of(2026, 11, 1);

    @Autowired
    private FinePersistenceAdapter adapter;

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
        loanId = data.newReturnedLoan(data.newCopy(), readerId, PICKUP);
    }

    private Fine saveFine(long loan, String amount) {
        return adapter.save(Fine.create(loan, readerId, new BigDecimal(amount)));
    }

    private void clearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    private static PageQuery byId() {
        return PageQuery.of(0, 20, "id");
    }

    @Test
    void shouldSaveAndFindAPendingFineWithTheSameState() {
        var saved = saveFine(loanId, "3.5");
        clearPersistenceContext();

        var found = adapter.findById(saved.getId()).orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(found.getLoanId()).isEqualTo(loanId);
        assertThat(found.getReaderId()).isEqualTo(readerId);
        assertThat(found.getAmount()).isEqualByComparingTo("3.50");
        assertThat(found.getStatus()).isEqualTo(FineStatus.PENDING);
        assertThat(found.getWaiverReason()).isNull();
    }

    @Test
    void shouldNotFindAnUnknownFine() {
        assertThat(adapter.findById(-1L)).isEmpty();
    }

    @Test
    void shouldSaveAPayment() {
        var fine = saveFine(loanId, "3.00");
        fine.pay();
        adapter.save(fine);
        clearPersistenceContext();

        assertThat(adapter.findById(fine.getId()).orElseThrow().getStatus()).isEqualTo(FineStatus.PAID);
    }

    @Test
    void shouldSaveAWaiverWithItsReason() {
        var fine = saveFine(loanId, "3.00");
        fine.waive("Hospital stay");
        adapter.save(fine);
        clearPersistenceContext();

        var found = adapter.findById(fine.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(FineStatus.WAIVED);
        assertThat(found.getWaiverReason()).isEqualTo("Hospital stay");
    }

    @Test
    void shouldKeepTheIdOfASavedFineInsteadOfCreatingAnother() {
        var fine = saveFine(loanId, "3.00");
        fine.pay();
        adapter.save(fine);
        clearPersistenceContext();

        assertThat(adapter.findAll(FineFilter.none(), byId()).totalItems()).isEqualTo(1);
    }

    @Test
    void shouldRefuseToSaveAFineThatDoesNotExistInTheDatabase() {
        var ghost = Fine.reconstitute(-5L, loanId, readerId, new BigDecimal("3.00"), FineStatus.PENDING, null);

        assertThatThrownBy(() -> adapter.save(ghost)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldTranslateASecondFineForTheSameLoanIntoABusinessRule() {
        saveFine(loanId, "3.00");

        assertThatThrownBy(() -> saveFine(loanId, "4.00"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("já tem multa");
    }

    @Test
    void shouldNotTranslateAnUnknownLoanIntoABusinessRule() {
        assertThatThrownBy(() -> saveFine(-1L, "3.00"))
                .isNotInstanceOf(BusinessRuleException.class)
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void shouldListTheFinesOfAReaderFilteredByStatus() {
        var pending = saveFine(loanId, "3.00");
        var paid = saveFine(data.newReturnedLoan(data.newCopy(), readerId, PICKUP), "4.00");
        paid.pay();
        adapter.save(paid);
        var otherReader = data.newReader();
        adapter.save(Fine.create(data.newReturnedLoan(data.newCopy(), otherReader, PICKUP), otherReader,
                new BigDecimal("5.00")));

        assertThat(adapter.findAll(new FineFilter(readerId, null, null), byId()).items())
                .extracting(Fine::getId).containsExactly(pending.getId(), paid.getId());
        assertThat(adapter.findAll(new FineFilter(readerId, null, FineStatus.PENDING), byId()).items())
                .extracting(Fine::getId).containsExactly(pending.getId());
    }

    @Test
    void shouldFindTheFineOfALoan() {
        var fine = saveFine(loanId, "3.00");

        assertThat(adapter.findAll(new FineFilter(null, loanId, null), byId()).items())
                .extracting(Fine::getId).containsExactly(fine.getId());
    }

    @Test
    void shouldFindTheFinesOfSeveralLoansInOneCall() {
        var otherLoan = data.newReturnedLoan(data.newCopy(), readerId, PICKUP);
        var withoutFine = data.newReturnedLoan(data.newCopy(), readerId, PICKUP);
        var first = saveFine(loanId, "3.00");
        var second = saveFine(otherLoan, "4.00");

        var found = adapter.findByLoanIds(java.util.Set.of(loanId, otherLoan, withoutFine));

        assertThat(found).containsOnlyKeys(loanId, otherLoan);
        assertThat(found.get(loanId).getId()).isEqualTo(first.getId());
        assertThat(found.get(otherLoan).getId()).isEqualTo(second.getId());
    }

    @Test
    void shouldFindNothingForNoLoans() {
        assertThat(adapter.findByLoanIds(java.util.Set.of())).isEmpty();
    }

    @Test
    void shouldPaginateAndSort() {
        for (var amount : new String[] {"3.00", "1.00", "2.00"}) {
            saveFine(data.newReturnedLoan(data.newCopy(), readerId, PICKUP), amount);
        }

        var page = adapter.findAll(new FineFilter(readerId, null, null), PageQuery.of(0, 2, "amount,desc"));

        assertThat(page.items()).extracting(Fine::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("3.00"), new BigDecimal("2.00"));
        assertThat(page.totalItems()).isEqualTo(3);
    }
}
