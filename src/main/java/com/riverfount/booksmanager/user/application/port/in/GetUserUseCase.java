package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.user.domain.User;
import java.util.Optional;

/** Finds a system user by id (RF09, HU28), active or not, blocked or not. */
public interface GetUserUseCase {

    Optional<User> findById(Long id);
}
