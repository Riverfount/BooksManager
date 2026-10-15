package com.riverfount.booksmanager.user.adapter.out.password;

import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt implementation of the password hashing port (RNF07), with its salt
 * and default cost. The encoder is private on purpose, not a shared
 * {@code PasswordEncoder} bean: while the authentication phase does not
 * exist, such a bean makes Spring Boot compare the generated password of its
 * default user as a hash, and the provisional login stops working. The bean
 * can come back with the JWT security chain (issue #101).
 */
@Component
class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
