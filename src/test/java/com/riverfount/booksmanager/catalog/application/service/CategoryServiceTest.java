package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalog.application.port.in.ListCategoriesQuery;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateCategoryCommand;
import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CategoryServiceTest {

    private InMemoryCategoryRepository repository;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCategoryRepository();
        service = new CategoryService(repository);
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

    @Test
    void shouldListOnlyActiveCategoriesByDefault() {
        service.register(new RegisterCategoryCommand("Ficção"));
        deactivated("Categoria Inativa");

        var result = service.list(new ListCategoriesQuery(null, true, PageQuery.of(0, 20, "name")));

        assertThat(result.items()).extracting(Category::getName).containsExactly("Ficção");
    }

    @Test
    void shouldListInactiveCategoriesWhenRequested() {
        service.register(new RegisterCategoryCommand("Ficção"));
        deactivated("Categoria Inativa");

        var result = service.list(new ListCategoriesQuery(null, false, PageQuery.of(0, 20, "name")));

        assertThat(result.items()).extracting(Category::getName).containsExactly("Categoria Inativa");
    }

    @Test
    void shouldFilterByNameContainingIgnoringCase() {
        service.register(new RegisterCategoryCommand("Ficção Científica"));
        service.register(new RegisterCategoryCommand("Romance"));

        var result = service.list(new ListCategoriesQuery("CIENT", true, PageQuery.of(0, 20, "name")));

        assertThat(result.items()).extracting(Category::getName).containsExactly("Ficção Científica");
    }

    @Test
    void shouldIgnoreBlankNameFilter() {
        service.register(new RegisterCategoryCommand("Ficção"));
        service.register(new RegisterCategoryCommand("Romance"));

        var result = service.list(new ListCategoriesQuery("  ", true, PageQuery.of(0, 20, "name")));

        assertThat(result.totalItems()).isEqualTo(2);
    }

    @Test
    void shouldReturnRequestedPageWithPaginationMetadata() {
        service.register(new RegisterCategoryCommand("Categoria A"));
        service.register(new RegisterCategoryCommand("Categoria B"));
        service.register(new RegisterCategoryCommand("Categoria C"));

        var result = service.list(new ListCategoriesQuery(null, true, PageQuery.of(1, 2, "name")));

        assertThat(result.items()).extracting(Category::getName).containsExactly("Categoria C");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalItems()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldSortByNameDescending() {
        service.register(new RegisterCategoryCommand("Categoria A"));
        service.register(new RegisterCategoryCommand("Categoria B"));

        var result = service.list(new ListCategoriesQuery(null, true, PageQuery.of(0, 20, "name,desc")));

        assertThat(result.items()).extracting(Category::getName).containsExactly("Categoria B", "Categoria A");
    }

    @Test
    void shouldRejectUnknownSortField() {
        assertThatThrownBy(() -> new ListCategoriesQuery(null, true, PageQuery.of(0, 20, "createdAt")))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void shouldRenameExistingCategoryAndPersistTheChange() {
        var registered = service.register(new RegisterCategoryCommand("Ficcão"));

        var updated = service.update(new UpdateCategoryCommand(registered.getId(), "Ficção"));

        assertThat(updated.getId()).isEqualTo(registered.getId());
        assertThat(updated.getName()).isEqualTo("Ficção");
        assertThat(repository.findById(registered.getId()).orElseThrow().getName()).isEqualTo("Ficção");
    }

    @Test
    void shouldKeepNameWhenItIsNotSent() {
        var registered = service.register(new RegisterCategoryCommand("Ficção"));

        var updated = service.update(new UpdateCategoryCommand(registered.getId(), null));

        assertThat(updated.getName()).isEqualTo("Ficção");
    }

    @Test
    void shouldNotRenameToNameAlreadyUsedByAnotherCategory() {
        service.register(new RegisterCategoryCommand("Romance"));
        var registered = service.register(new RegisterCategoryCommand("Ficção"));

        assertThatThrownBy(() -> service.update(new UpdateCategoryCommand(registered.getId(), "Romance")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Romance");
        assertThat(repository.findById(registered.getId()).orElseThrow().getName()).isEqualTo("Ficção");
    }

    @Test
    void shouldAcceptRenamingToItsOwnCurrentName() {
        var registered = service.register(new RegisterCategoryCommand("Ficção"));

        var updated = service.update(new UpdateCategoryCommand(registered.getId(), "Ficção"));

        assertThat(updated.getName()).isEqualTo("Ficção");
    }

    @Test
    void shouldKeepActiveFlagWhenUpdating() {
        var category = service.register(new RegisterCategoryCommand("Categoria Inativa"));
        category.deactivate();
        repository.save(category);

        var updated = service.update(new UpdateCategoryCommand(category.getId(), "Categoria Renomeada"));

        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void shouldRejectUpdateOfNonExistentCategory() {
        assertThatThrownBy(() -> service.update(new UpdateCategoryCommand(99L, "Qualquer Nome")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void shouldRejectBlankNameWhenUpdating() {
        var registered = service.register(new RegisterCategoryCommand("Ficção"));

        assertThatThrownBy(() -> service.update(new UpdateCategoryCommand(registered.getId(), " ")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(repository.findById(registered.getId()).orElseThrow().getName()).isEqualTo("Ficção");
    }

    @Test
    void shouldDeactivateExistingCategoryKeepingItStored() {
        var registered = service.register(new RegisterCategoryCommand("Ficção"));

        service.deactivate(registered.getId());

        var stored = repository.findById(registered.getId());
        assertThat(stored).isPresent();
        assertThat(stored.get().isActive()).isFalse();
        assertThat(stored.get().getName()).isEqualTo("Ficção");
    }

    @Test
    void shouldBeIdempotentWhenDeactivatingAnAlreadyInactiveCategory() {
        var registered = service.register(new RegisterCategoryCommand("Ficção"));
        service.deactivate(registered.getId());

        service.deactivate(registered.getId());

        assertThat(repository.findById(registered.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void shouldRejectDeactivationOfNonExistentCategory() {
        assertThatThrownBy(() -> service.deactivate(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    private void deactivated(String name) {
        var category = service.register(new RegisterCategoryCommand(name));
        category.deactivate();
        repository.save(category);
    }

    @Test
    void shouldReactivateAnInactiveCategoryKeepingItStored() {
        var registered = service.register(new RegisterCategoryCommand("Ficção"));
        service.deactivate(registered.getId());

        var reactivated = service.reactivate(registered.getId());

        assertThat(reactivated.isActive()).isTrue();
        assertThat(repository.findById(registered.getId()).orElseThrow().isActive()).isTrue();
        assertThat(repository.findById(registered.getId()).orElseThrow().getName()).isEqualTo("Ficção");
    }

    @Test
    void shouldBeIdempotentWhenReactivatingAnActiveCategory() {
        var registered = service.register(new RegisterCategoryCommand("Ficção"));

        assertThat(service.reactivate(registered.getId()).isActive()).isTrue();
    }

    @Test
    void shouldFailWhenReactivatingACategoryThatDoesNotExist() {
        assertThatThrownBy(() -> service.reactivate(999L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
