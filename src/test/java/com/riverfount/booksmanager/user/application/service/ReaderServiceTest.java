package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.user.application.port.in.ListReadersQuery;
import com.riverfount.booksmanager.user.application.port.in.RegisterReaderCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReaderServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate ADULT_BIRTH = LocalDate.of(1990, 5, 10);
    private static final LocalDate MINOR_BIRTH = LocalDate.of(2015, 3, 1);

    private InMemoryReaderRepository repository;
    private ReaderService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryReaderRepository();
        service = new ReaderService(repository, FIXED_CLOCK);
    }

    private static RegisterReaderCommand adult(String document) {
        return new RegisterReaderCommand("Maria Silva", document, "maria@example.com", null, ADULT_BIRTH,
                null, null, null);
    }

    @Test
    void shouldRegisterAdultReaderWithIdAndNormalizedDocument() {
        var reader = service.register(new RegisterReaderCommand("Maria Silva", "123.456.789-00",
                "maria@example.com", "11 91234-5678", ADULT_BIRTH, null, null, null));

        assertThat(reader.getId()).isNotNull();
        assertThat(reader.getDocument()).isEqualTo("12345678900");
        assertThat(reader.getGuardian()).isNull();
        assertThat(reader.isActive()).isTrue();
        assertThat(reader.isBlocked()).isFalse();
        assertThat(repository.findById(reader.getId())).isPresent();
    }

    @Test
    void shouldRegisterMinorReaderWithTheGuardian() {
        var reader = service.register(new RegisterReaderCommand("Júlia", "MAT-1", "julia@example.com", null,
                MINOR_BIRTH, "Ana Souza", "ana@example.com", "11 99999-0000"));

        assertThat(reader.getGuardian().name()).isEqualTo("Ana Souza");
        assertThat(reader.getGuardian().email()).isEqualTo("ana@example.com");
        assertThat(reader.getGuardian().phone()).isEqualTo("11 99999-0000");
    }

    @Test
    void shouldUseTheClockToDecideWhoIsAMinor() {
        // 18 years old exactly on the clock's date: an adult, so no guardian is needed
        var birth = LocalDate.of(2008, 10, 15);

        var reader = service.register(new RegisterReaderCommand("Júlia", "MAT-1", "julia@example.com", null,
                birth, null, null, null));

        assertThat(reader.getGuardian()).isNull();
    }

    @Test
    void shouldRejectDuplicateDocumentEvenAfterNormalization() {
        service.register(adult("123.456.789-00"));

        assertThatThrownBy(() -> service.register(adult("12345678900")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("já existe um leitor");
    }

    @Test
    void shouldNotSaveTheReaderWhenTheDocumentIsDuplicate() {
        service.register(adult("111"));

        assertThatThrownBy(() -> service.register(adult("1-1-1"))).isInstanceOf(BusinessRuleException.class);

        assertThat(repository.existsByDocument("111")).isTrue();
        assertThat(repository.findAll(com.riverfount.booksmanager.user.application.port.out.ReaderFilter.none(),
                com.riverfount.booksmanager.shared.domain.PageQuery.of(0, 20, "id")).totalItems()).isEqualTo(1);
    }

    @Test
    void shouldRejectMinorWithoutGuardian() {
        var command = new RegisterReaderCommand("Júlia", "MAT-1", "julia@example.com", null, MINOR_BIRTH,
                null, null, null);

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("responsável");
    }

    @Test
    void shouldRejectMinorWhoseGuardianHasNoName() {
        var command = new RegisterReaderCommand("Júlia", "MAT-1", "julia@example.com", null, MINOR_BIRTH,
                null, "ana@example.com", null);

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nome do responsável");
    }

    @Test
    void shouldRejectMinorWhoseGuardianHasNoEmail() {
        var command = new RegisterReaderCommand("Júlia", "MAT-1", "julia@example.com", null, MINOR_BIRTH,
                "Ana Souza", null, "11 99999-0000");

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("e-mail do responsável");
    }

    @Test
    void shouldRejectInvalidReaderData() {
        var command = new RegisterReaderCommand("Maria", "123", "not-an-email", null, ADULT_BIRTH, null, null,
                null);

        assertThatThrownBy(() -> service.register(command)).isInstanceOf(BusinessRuleException.class);
    }

    // query (HU25)

    @Test
    void shouldFindExistingReaderById() {
        var registered = service.register(adult("123"));

        var found = service.findById(registered.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Maria Silva");
    }

    @Test
    void shouldFindInactiveAndBlockedReaders() {
        var registered = service.register(adult("123"));
        registered.block();
        registered.deactivate();
        repository.save(registered);

        var found = service.findById(registered.getId()).orElseThrow();

        assertThat(found.isActive()).isFalse();
        assertThat(found.isBlocked()).isTrue();
    }

    @Test
    void shouldFindMinorReaderWithTheGuardian() {
        var registered = service.register(new RegisterReaderCommand("Júlia", "MAT-1", "julia@example.com", null,
                MINOR_BIRTH, "Ana Souza", "ana@example.com", null));

        assertThat(service.findById(registered.getId()).orElseThrow().getGuardian().name())
                .isEqualTo("Ana Souza");
    }

    @Test
    void shouldReturnEmptyWhenFindingNonExistentId() {
        assertThat(service.findById(-1L)).isEmpty();
    }

    @Test
    void shouldRejectNullIdWhenFinding() {
        assertThatThrownBy(() -> service.findById(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // listing (HU25, RNF14)

    private static ListReadersQuery query(String name, String document, boolean active, Boolean blocked) {
        return new ListReadersQuery(name, document, active, blocked, PageQuery.of(0, 20, "name"));
    }

    private void register(String name, String document) {
        service.register(new RegisterReaderCommand(name, document, "reader@example.com", null, ADULT_BIRTH,
                null, null, null));
    }

    private void deactivate(String document) {
        var reader = repository.findAll(com.riverfount.booksmanager.user.application.port.out.ReaderFilter.none(),
                PageQuery.of(0, 100, "id")).items().stream()
                .filter(r -> r.getDocument().equals(document)).findFirst().orElseThrow();
        reader.deactivate();
        repository.save(reader);
    }

    @Test
    void shouldListOnlyActiveReadersByDefault() {
        register("Ana", "1");
        register("Bruno", "2");
        deactivate("2");

        var page = service.list(query(null, null, true, null));

        assertThat(page.items()).extracting(r -> r.getName()).containsExactly("Ana");
        assertThat(service.list(query(null, null, false, null)).items())
                .extracting(r -> r.getName()).containsExactly("Bruno");
    }

    @Test
    void shouldFilterByNameContainingAndByNormalizedDocument() {
        register("Maria Silva", "123.456.789-00");
        register("Mariana Costa", "222");

        assertThat(service.list(query("MARIA", null, true, null)).items()).hasSize(2);
        assertThat(service.list(query(null, "123456789-00", true, null)).items())
                .extracting(r -> r.getName()).containsExactly("Maria Silva");
        assertThat(service.list(query(null, "12345", true, null)).items()).isEmpty();
    }

    @Test
    void shouldFilterByBlocked() {
        register("Ana", "1");
        register("Bruno", "2");
        var bruno = service.list(query("Bruno", null, true, null)).items().get(0);
        bruno.block();
        repository.save(bruno);

        assertThat(service.list(query(null, null, true, true)).items())
                .extracting(r -> r.getName()).containsExactly("Bruno");
        assertThat(service.list(query(null, null, true, false)).items())
                .extracting(r -> r.getName()).containsExactly("Ana");
    }

    @Test
    void shouldPaginateAndSort() {
        register("Ana", "1");
        register("Bruno", "2");
        register("Carla", "3");

        var page = service.list(new ListReadersQuery(null, null, true, null, PageQuery.of(1, 2, "name,desc")));

        assertThat(page.items()).extracting(r -> r.getName()).containsExactly("Ana");
        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
    }
}
