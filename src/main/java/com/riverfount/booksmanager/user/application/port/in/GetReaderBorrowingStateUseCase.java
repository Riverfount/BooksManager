package com.riverfount.booksmanager.user.application.port.in;

/** Public port for the loan module: the state of a reader for borrowing (RN11, RF10). */
public interface GetReaderBorrowingStateUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the reader does not exist
     */
    ReaderBorrowingState getBorrowingState(Long readerId);
}
