package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.user.domain.Reader;

public interface UpdateReaderUseCase {

    Reader update(UpdateReaderCommand command);
}
