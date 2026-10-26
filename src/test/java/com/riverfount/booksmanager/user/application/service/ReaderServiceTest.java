package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.user.application.port.in.ListReadersQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.audit.application.RecordedAudit;
import com.riverfount.booksmanager.user.application.port.in.RegisterReaderCommand;
import com.riverfount.booksmanager.user.application.port.in.UpdateReaderCommand;
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
        service = new ReaderService(repository, FIXED_CLOCK, new RecordedAudit(), readerId -> { });
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

    // partial update (HU26, RNF11)

    private static UpdateReaderCommand change(Long id, String name, String document, String email, String phone,
            LocalDate birthDate, String guardianName, String guardianEmail, String guardianPhone) {
        return new UpdateReaderCommand(id, name, document, email, phone, birthDate, guardianName, guardianEmail,
                guardianPhone);
    }

    private static UpdateReaderCommand onlyName(Long id, String name) {
        return change(id, name, null, null, null, null, null, null, null);
    }

    private Long registerAdult(String name, String document) {
        return service.register(new RegisterReaderCommand(name, document, "reader@example.com",
                "11 91234-5678", ADULT_BIRTH, null, null, null)).getId();
    }

    private Long registerMinor(String document) {
        return service.register(new RegisterReaderCommand("Júlia", document, "julia@example.com", null,
                MINOR_BIRTH, "Ana Souza", "ana@example.com", "11 99999-0000")).getId();
    }

    @Test
    void shouldChangeOnlyTheSentFields() {
        var id = registerAdult("Maria Silva", "123");

        var updated = service.update(onlyName(id, "Maria Souza"));

        assertThat(updated.getName()).isEqualTo("Maria Souza");
        assertThat(updated.getDocument()).isEqualTo("123");
        assertThat(updated.getEmail()).isEqualTo("reader@example.com");
        assertThat(updated.getPhone()).isEqualTo("11 91234-5678");
        assertThat(updated.getBirthDate()).isEqualTo(ADULT_BIRTH);
        assertThat(repository.findById(id).orElseThrow().getName()).isEqualTo("Maria Souza");
    }

    @Test
    void shouldKeepEverythingWhenNothingIsSent() {
        var id = registerAdult("Maria Silva", "123");

        var updated = service.update(change(id, null, null, null, null, null, null, null, null));

        assertThat(updated.getName()).isEqualTo("Maria Silva");
        assertThat(updated.getDocument()).isEqualTo("123");
        assertThat(updated.getPhone()).isEqualTo("11 91234-5678");
    }

    @Test
    void shouldChangeEveryPersonalField() {
        var id = registerAdult("Maria Silva", "123");

        var updated = service.update(change(id, "Maria Souza", "987.654.321-00", "souza@example.com",
                "11 98888-7777", LocalDate.of(1991, 1, 2), null, null, null));

        assertThat(updated.getName()).isEqualTo("Maria Souza");
        assertThat(updated.getDocument()).isEqualTo("98765432100");
        assertThat(updated.getEmail()).isEqualTo("souza@example.com");
        assertThat(updated.getPhone()).isEqualTo("11 98888-7777");
        assertThat(updated.getBirthDate()).isEqualTo(LocalDate.of(1991, 1, 2));
    }

    @Test
    void shouldNotChangeTheStateOfTheReader() {
        var id = registerAdult("Maria Silva", "123");
        var reader = repository.findById(id).orElseThrow();
        reader.block();
        reader.deactivate();
        repository.save(reader);

        var updated = service.update(onlyName(id, "Maria Souza"));

        assertThat(updated.isActive()).isFalse();
        assertThat(updated.isBlocked()).isTrue();
    }

    @Test
    void shouldRefuseTheDocumentOfAnotherReaderEvenAfterNormalization() {
        registerAdult("Maria Silva", "12345678900");
        var otherId = registerAdult("João", "222");

        var command = change(otherId, null, "123.456.789-00", null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("já existe um leitor");
        assertThat(repository.findById(otherId).orElseThrow().getDocument()).isEqualTo("222");
    }

    @Test
    void keepingTheOwnDocumentIsNotARepetition() {
        var id = registerAdult("Maria Silva", "12345678900");

        var updated = service.update(change(id, null, "123.456.789-00", null, null, null, null, null, null));

        assertThat(updated.getDocument()).isEqualTo("12345678900");
    }

    @Test
    void shouldRefuseABlankDocumentWhenSent() {
        var id = registerAdult("Maria Silva", "123");

        assertThatThrownBy(() -> service.update(change(id, null, ".-/", null, null, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("documento");
    }

    @Test
    void shouldRefuseInvalidDataAndKeepTheStoredReader() {
        var id = registerAdult("Maria Silva", "123");

        assertThatThrownBy(() -> service.update(change(id, null, null, "not-an-email", null, null, null, null,
                null)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(repository.findById(id).orElseThrow().getEmail()).isEqualTo("reader@example.com");
    }

    @Test
    void shouldRefuseAMinorBirthDateWithoutTheGuardianAndKeepTheStoredReader() {
        var id = registerAdult("Maria Silva", "123");

        assertThatThrownBy(() -> service.update(change(id, null, null, null, null, MINOR_BIRTH, null, null,
                null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("responsável");

        assertThat(repository.findById(id).orElseThrow().getBirthDate()).isEqualTo(ADULT_BIRTH);
    }

    @Test
    void shouldAcceptAMinorBirthDateWhenTheGuardianComesInTheSameEdit() {
        var id = registerAdult("Maria Silva", "123");

        var updated = service.update(change(id, null, null, null, null, MINOR_BIRTH, "Ana Souza",
                "ana@example.com", null));

        assertThat(updated.isMinor(LocalDate.of(2026, 10, 15))).isTrue();
        assertThat(updated.getGuardian().name()).isEqualTo("Ana Souza");
    }

    @Test
    void shouldKeepTheGuardianWhenEditingOtherFieldsOfAMinor() {
        var id = registerMinor("MAT-1");

        var updated = service.update(onlyName(id, "Júlia Souza"));

        assertThat(updated.getGuardian().name()).isEqualTo("Ana Souza");
        assertThat(updated.getGuardian().email()).isEqualTo("ana@example.com");
        assertThat(updated.getGuardian().phone()).isEqualTo("11 99999-0000");
    }

    @Test
    void shouldMergeTheGuardianFieldsSentWithTheCurrentOnes() {
        var id = registerMinor("MAT-1");

        var updated = service.update(change(id, null, null, null, null, null, null, "novo@example.com", null));

        assertThat(updated.getGuardian().name()).isEqualTo("Ana Souza");
        assertThat(updated.getGuardian().email()).isEqualTo("novo@example.com");
        assertThat(updated.getGuardian().phone()).isEqualTo("11 99999-0000");
    }

    @Test
    void shouldRefuseAGuardianWithoutNameOrEmailEvenForAnAdult() {
        var id = registerAdult("Maria Silva", "123");

        assertThatThrownBy(() -> service.update(change(id, null, null, null, null, null, null, null,
                "11 99999-0000")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nome do responsável");
    }

    @Test
    void shouldAllowAnAdultToReceiveAGuardian() {
        var id = registerAdult("Maria Silva", "123");

        var updated = service.update(change(id, null, null, null, null, null, "Ana Souza", "ana@example.com",
                null));

        assertThat(updated.getGuardian().name()).isEqualTo("Ana Souza");
    }

    @Test
    void shouldRejectUnknownReader() {
        assertThatThrownBy(() -> service.update(onlyName(99L, "Maria")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // deactivation (HU27, RN11, RN14)

    @Test
    void shouldDeactivateReaderKeepingTheRecord() {
        var id = registerAdult("Maria Silva", "123");

        service.deactivate(id);

        var stored = repository.findById(id).orElseThrow();
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.getName()).isEqualTo("Maria Silva");
        assertThat(stored.getDocument()).isEqualTo("123");
        assertThat(stored.getBirthDate()).isEqualTo(ADULT_BIRTH);
    }

    @Test
    void deactivatingAnInactiveReaderShouldBeIdempotent() {
        var id = registerAdult("Maria Silva", "123");

        service.deactivate(id);
        service.deactivate(id);

        assertThat(repository.findById(id).orElseThrow().isActive()).isFalse();
    }

    @Test
    void shouldDeactivateABlockedReaderKeepingItBlocked() {
        var id = registerAdult("Maria Silva", "123");
        var reader = repository.findById(id).orElseThrow();
        reader.block();
        repository.save(reader);

        service.deactivate(id);

        var stored = repository.findById(id).orElseThrow();
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.isBlocked()).isTrue();
    }

    @Test
    void aDeactivatedReaderShouldLeaveTheDefaultListingAndNotBeAbleToBorrow() {
        var id = registerAdult("Maria Silva", "123");
        registerAdult("Pedro", "456");

        service.deactivate(id);

        assertThat(service.list(query(null, null, true, null)).items())
                .extracting(r -> r.getName()).containsExactly("Pedro");
        assertThat(service.list(query(null, null, false, null)).items())
                .extracting(r -> r.getName()).containsExactly("Maria Silva");
        assertThat(service.findById(id).orElseThrow().canBorrow(LocalDate.of(2026, 10, 15))).isFalse();
    }

    @Test
    void shouldRejectDeactivationOfUnknownReader() {
        assertThatThrownBy(() -> service.deactivate(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // block and unblock (RF10, HU06, RN11)

    @Test
    void shouldBlockReaderKeepingTheRestOfTheRecord() {
        var id = registerAdult("Maria Silva", "123");

        service.block(id);

        var stored = repository.findById(id).orElseThrow();
        assertThat(stored.isBlocked()).isTrue();
        assertThat(stored.isActive()).isTrue();
        assertThat(stored.getName()).isEqualTo("Maria Silva");
        assertThat(stored.getDocument()).isEqualTo("123");
    }

    @Test
    void shouldUnblockReader() {
        var id = registerAdult("Maria Silva", "123");
        service.block(id);

        service.unblock(id);

        assertThat(repository.findById(id).orElseThrow().isBlocked()).isFalse();
    }

    @Test
    void blockAndUnblockShouldBeIdempotent() {
        var id = registerAdult("Maria Silva", "123");

        service.unblock(id);
        assertThat(repository.findById(id).orElseThrow().isBlocked()).isFalse();

        service.block(id);
        service.block(id);
        assertThat(repository.findById(id).orElseThrow().isBlocked()).isTrue();

        service.unblock(id);
        service.unblock(id);
        assertThat(repository.findById(id).orElseThrow().isBlocked()).isFalse();
    }

    @Test
    void aBlockedReaderShouldNotBeAbleToBorrowAndShowItsStateInTheQuery() {
        var id = registerAdult("Maria Silva", "123");

        service.block(id);

        var found = service.findById(id).orElseThrow();
        assertThat(found.isBlocked()).isTrue();
        assertThat(found.canBorrow(LocalDate.of(2026, 10, 15))).isFalse();
    }

    @Test
    void shouldAllowFilteringTheListingByBlocked() {
        var blockedId = registerAdult("Bruno", "2");
        registerAdult("Ana", "1");

        service.block(blockedId);

        assertThat(service.list(query(null, null, true, true)).items())
                .extracting(r -> r.getName()).containsExactly("Bruno");
        assertThat(service.list(query(null, null, true, false)).items())
                .extracting(r -> r.getName()).containsExactly("Ana");
    }

    @Test
    void shouldRefuseToBlockAnInactiveReaderAndKeepItUnblocked() {
        var id = registerAdult("Maria Silva", "123");
        service.deactivate(id);

        assertThatThrownBy(() -> service.block(id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inativo");

        assertThat(repository.findById(id).orElseThrow().isBlocked()).isFalse();
    }

    @Test
    void shouldUnblockAnInactiveReaderThatWasBlockedBeforeBeingDeactivated() {
        var id = registerAdult("Maria Silva", "123");
        service.block(id);
        service.deactivate(id);

        service.unblock(id);

        var stored = repository.findById(id).orElseThrow();
        assertThat(stored.isBlocked()).isFalse();
        assertThat(stored.isActive()).isFalse();
    }

    @Test
    void shouldRejectBlockAndUnblockOfUnknownReader() {
        assertThatThrownBy(() -> service.block(99L))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("99");
        assertThatThrownBy(() -> service.unblock(99L))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("99");
    }
}
