package com.riverfount.booksmanager;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * A persistence test against the real PostgreSQL ({@link TestcontainersConfiguration}), rolled back at the end
 * of each test. Every persistence test uses this same annotation, with the same adapters, so they all share one
 * Spring context instead of starting one each: it brings in all the persistence adapters of the modules (the
 * classes named {@code *PersistenceAdapter} in {@code adapter.out.persistence}), and a test just injects the
 * one it exercises. A test that needs a different setup should not use this annotation, or it would start a
 * context of its own.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(TestcontainersConfiguration.class)
@DataJpaTest(includeFilters = @Filter(type = FilterType.REGEX, pattern = ".*\\.adapter\\.out\\.persistence\\..*PersistenceAdapter"))
@AutoConfigureTestDatabase(replace = Replace.NONE)
public @interface PersistenceTest {
}
