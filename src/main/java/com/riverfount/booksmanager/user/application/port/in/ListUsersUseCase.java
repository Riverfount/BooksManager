package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.user.domain.User;

public interface ListUsersUseCase {

    PageResult<User> list(ListUsersQuery query);
}
