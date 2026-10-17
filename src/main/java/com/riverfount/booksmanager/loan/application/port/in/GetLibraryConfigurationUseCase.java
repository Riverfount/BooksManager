package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.LibraryConfiguration;

/** Reads the library configuration (RF26, HU13). */
public interface GetLibraryConfigurationUseCase {

    LibraryConfiguration get();
}
