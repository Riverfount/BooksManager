package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.GetReaderBorrowingStateUseCase;
import com.riverfount.booksmanager.user.application.port.in.ReaderBorrowingState;
import com.riverfount.booksmanager.user.application.port.out.ReaderRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the user module offers the loan module (RN11, RF10): the state of a reader for
 * borrowing, so the loan never reaches the {@code Reader} class. A class of its own, apart
 * from {@link ReaderService}, because it serves another module; the user module never
 * depends on the loan, so the dependency points one way only. The decision is the domain's
 * ({@code Reader.canBorrow}); the facts are given too, so the loan can report every reason.
 */
@Service
class ReaderBorrowingStateService implements GetReaderBorrowingStateUseCase {

    private final ReaderRepository readerRepository;
    private final Clock clock;

    ReaderBorrowingStateService(ReaderRepository readerRepository, Clock clock) {
        this.readerRepository = readerRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ReaderBorrowingState getBorrowingState(Long readerId) {
        var reader = readerRepository.findById(readerId)
                .orElseThrow(() -> new ResourceNotFoundException("leitor não encontrado: " + readerId));
        return new ReaderBorrowingState(reader.getId(), reader.isActive(), reader.isBlocked(),
                reader.canBorrow(LocalDate.now(clock)));
    }
}
