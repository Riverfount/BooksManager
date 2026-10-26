package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.loan.application.port.in.GetLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnLoanQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListLoansUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListOwnLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.LoanDetails;
import com.riverfount.booksmanager.loan.application.port.in.LoanStatus;
import com.riverfount.booksmanager.loan.application.port.in.RenewLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RenewLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.in.RenewOwnLoanCommand;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * What a reader does with their own loans (Phase 6, RF13, RF14): the reader of the token is the only reader that is
 * ever acted for, a loan of another reader is answered exactly like one that does not exist (before any other rule,
 * so its state is not revealed), and the renewal keeps every rule of the desk (RN07, RN09).
 */
class OwnLoanServiceTest {

    private static final long ME = 42L;
    private static final long ANOTHER = 43L;
    private static final LocalDate PICKUP = LocalDate.of(2026, 11, 1);

    private GetLoanUseCase get;
    private ListLoansUseCase list;
    private RenewLoanUseCase renew;
    private OwnLoanService service;

    @BeforeEach
    void setUp() {
        get = mock(GetLoanUseCase.class);
        list = mock(ListLoansUseCase.class);
        renew = mock(RenewLoanUseCase.class);
        service = new OwnLoanService(get, list, renew);
    }

    private static Loan loan(long id, long readerId, int renewals, LocalDate returnDate) {
        return Loan.reconstitute(id, 7L, readerId, PICKUP, PICKUP.plusDays(14), returnDate, renewals);
    }

    private static LoanDetails details(Loan loan) {
        return new LoanDetails(loan, null, null, "Maria Silva",
                new CopySummary(7L, "PAT-0007", 3L, "Dom Casmurro"), null);
    }

    // listing

    @Test
    void shouldListOnlyTheLoansOfTheReaderWhateverTheFilters() {
        var page = PageQuery.of(0, 20, "pickupDate,desc");
        given(list.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        service.list(new ListOwnLoansQuery(ME, LoanStatus.ACTIVE, true, page));

        var captor = ArgumentCaptor.forClass(ListLoansQuery.class);
        then(list).should().list(captor.capture());
        assertThat(captor.getValue().readerId()).isEqualTo(ME);
        assertThat(captor.getValue().copyId()).isNull();
        assertThat(captor.getValue().status()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(captor.getValue().overdue()).isTrue();
        assertThat(captor.getValue().page()).isEqualTo(page);
    }

    // looking at one

    @Test
    void shouldShowALoanOfTheReader() {
        given(get.findById(5L)).willReturn(Optional.of(details(loan(5L, ME, 0, null))));

        assertThat(service.get(new GetOwnLoanQuery(5L, ME)).loan().getId()).isEqualTo(5L);
    }

    @Test
    void aLoanOfAnotherReaderShouldBeAsNotFoundAsOneThatDoesNotExist() {
        given(get.findById(5L)).willReturn(Optional.of(details(loan(5L, ANOTHER, 0, null))));
        given(get.findById(6L)).willReturn(Optional.empty());

        var another = notFoundMessage(() -> service.get(new GetOwnLoanQuery(5L, ME)));
        var missing = notFoundMessage(() -> service.get(new GetOwnLoanQuery(6L, ME)));

        assertThat(another.replaceAll("\\d+", "N")).isEqualTo(missing.replaceAll("\\d+", "N"));
    }

    // renewing

    @Test
    void shouldRenewALoanOfTheReaderAndReturnItWithTheNewDate() {
        var before = loan(5L, ME, 0, null);
        var after = Loan.reconstitute(5L, 7L, ME, PICKUP, PICKUP.plusDays(28), null, 1);
        given(get.findById(5L)).willReturn(Optional.of(details(before)), Optional.of(details(after)));
        given(renew.renew(new RenewLoanCommand(5L))).willReturn(after);

        var renewed = service.renew(new RenewOwnLoanCommand(5L, ME));

        assertThat(renewed.loan().getRenewals()).isEqualTo(1);
        assertThat(renewed.loan().getDueDate()).isEqualTo(PICKUP.plusDays(28));
    }

    @Test
    void shouldNeverRenewALoanOfAnotherReaderNorSayItExists() {
        given(get.findById(5L)).willReturn(Optional.of(details(loan(5L, ANOTHER, 0, null))));
        given(get.findById(6L)).willReturn(Optional.empty());

        var another = notFoundMessage(() -> service.renew(new RenewOwnLoanCommand(5L, ME)));
        var missing = notFoundMessage(() -> service.renew(new RenewOwnLoanCommand(6L, ME)));

        assertThat(another.replaceAll("\\d+", "N")).isEqualTo(missing.replaceAll("\\d+", "N"));
        then(renew).should(never()).renew(any());
    }

    @Test
    void aClosedLoanOfAnotherReaderIsStillNotFoundNotAnUnprocessableOne() {
        // otherwise "already returned" would tell the state of somebody else's loan
        given(get.findById(5L)).willReturn(Optional.of(details(loan(5L, ANOTHER, 0, PICKUP.plusDays(3)))));

        assertThatThrownBy(() -> service.renew(new RenewOwnLoanCommand(5L, ME)))
                .isInstanceOf(ResourceNotFoundException.class);
        then(renew).should(never()).renew(any());
    }

    @Test
    void shouldKeepTheRefusalOfTheRulesOfTheDeskForTheOwnLoan() {
        given(get.findById(5L)).willReturn(Optional.of(details(loan(5L, ME, 2, null))));
        given(renew.renew(any())).willThrow(new BusinessRuleException("o limite de renovações (2) deste empréstimo já foi atingido"));

        assertThatThrownBy(() -> service.renew(new RenewOwnLoanCommand(5L, ME)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("limite de renovações");
    }

    private static String notFoundMessage(Runnable action) {
        try {
            action.run();
        } catch (ResourceNotFoundException e) {
            return e.getMessage();
        }
        throw new AssertionError("expected a ResourceNotFoundException");
    }
}
