package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryCommand;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CategoryServiceTest {

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(new InMemoryCategoryRepository());
    }

    @Test
    void shouldRegisterNewCategoryWithId() {
        var category = service.register(new RegisterCategoryCommand("Ficção"));

        assertThat(category.getId()).isNotNull();
        assertThat(category.getName()).isEqualTo("Ficção");
        assertThat(category.isActive()).isTrue();
    }

    @Test
    void shouldNotRegisterCategoryWithAlreadyExistingName() {
        service.register(new RegisterCategoryCommand("Ficção"));

        assertThatThrownBy(() -> service.register(new RegisterCategoryCommand("Ficção")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldFindExistingCategoryById() {
        var registered = service.register(new RegisterCategoryCommand("Suspense"));

        var found = service.findById(registered.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Suspense");
    }

    @Test
    void shouldReturnEmptyWhenFindingNonExistentId() {
        assertThat(service.findById(-1L)).isEmpty();
    }

    @Test
    void shouldRejectNullIdWhenFinding() {
        // the fake needs to behave like the real JPA adapter
        // (JpaRepository.findById rejects a null id), otherwise this
        // scenario would pass here and only break in production.
        assertThatThrownBy(() -> service.findById(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
