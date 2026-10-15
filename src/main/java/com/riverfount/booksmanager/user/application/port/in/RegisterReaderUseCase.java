package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.user.domain.Reader;

public interface RegisterReaderUseCase {

    Reader register(RegisterReaderCommand command);
}
