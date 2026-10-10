package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.Copy;

public interface RegisterCopyUseCase {

    Copy register(RegisterCopyCommand command);
}
