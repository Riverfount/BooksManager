package com.riverfount.booksmanager.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.user.application.port.out.ReaderFilter;
import com.riverfount.booksmanager.user.domain.Guardian;
import com.riverfount.booksmanager.user.domain.Reader;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests the Reader persistence adapter against a real PostgreSQL
 * (Testcontainers): the mapping between the domain and the JPA entity, the
 * unique document (RF07) and the listing filters.
 */
@PersistenceTest
class ReaderPersistenceAdapterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final LocalDate ADULT_BIRTH = LocalDate.of(1990, 5, 10);

    @Autowired
    private ReaderPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Reader saveReader(String name, String document) {
        return adapter.save(Reader.create(name, document, "reader@example.com", null, ADULT_BIRTH, null, TODAY));
    }

    private static PageQuery byName() {
        return PageQuery.of(0, 20, "name");
    }

    // mapping

    @Test
    void shouldSaveAndFindAdultReaderWithTheSameState() {
        var saved = adapter.save(Reader.create("Maria Silva", "123.456.789-00", "maria@example.com",
                "11 91234-5678", ADULT_BIRTH, null, TODAY));

        var found = adapter.findById(saved.getId()).orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(found.getName()).isEqualTo("Maria Silva");
        assertThat(found.getDocument()).isEqualTo("12345678900");
        assertThat(found.getEmail()).isEqualTo("maria@example.com");
        assertThat(found.getPhone()).isEqualTo("11 91234-5678");
        assertThat(found.getBirthDate()).isEqualTo(ADULT_BIRTH);
        assertThat(found.getGuardian()).isNull();
        assertThat(found.isActive()).isTrue();
        assertThat(found.isBlocked()).isFalse();
    }

    @Test
    void shouldSaveAndFindMinorReaderWithTheGuardian() {
        var guardian = new Guardian("Ana Souza", "ana@example.com", "11 99999-0000");
        var saved = adapter.save(Reader.create("Júlia", "MAT-1", "julia@example.com", null,
                LocalDate.of(2015, 3, 1), guardian, TODAY));

        var found = adapter.findById(saved.getId()).orElseThrow();

        assertThat(found.getGuardian()).isEqualTo(guardian);
        assertThat(found.isMinor(TODAY)).isTrue();
    }

    @Test
    void shouldSaveAGuardianWithoutPhone() {
        var guardian = new Guardian("Ana Souza", "ana@example.com", null);
        var saved = adapter.save(Reader.create("Júlia", "MAT-1", "julia@example.com", null,
                LocalDate.of(2015, 3, 1), guardian, TODAY));

        assertThat(adapter.findById(saved.getId()).orElseThrow().getGuardian()).isEqualTo(guardian);
    }

    @Test
    void shouldPersistEditsAndStateChangesOfAnExistingReader() {
        var saved = saveReader("Maria Silva", "111");
        saved.updateDetails("Maria Souza", "222", "souza@example.com", "11 91234-5678", ADULT_BIRTH, null, TODAY);
        saved.block();
        adapter.save(saved);

        var found = adapter.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getName()).isEqualTo("Maria Souza");
        assertThat(found.getDocument()).isEqualTo("222");
        assertThat(found.getPhone()).isEqualTo("11 91234-5678");
        assertThat(found.isBlocked()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from reader", Integer.class)).isEqualTo(1);
    }

    @Test
    void deactivatedReaderShouldStayInTheDatabase() {
        var saved = saveReader("Maria Silva", "111");
        saved.deactivate();
        adapter.save(saved);

        assertThat(adapter.findById(saved.getId()).orElseThrow().isActive()).isFalse();
        assertThat(jdbcTemplate.queryForObject("select count(*) from reader", Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyForNonExistentId() {
        assertThat(adapter.findById(999_999L)).isEmpty();
    }

    // unique document (RF07)

    @Test
    void duplicateDocumentShouldViolateTheUniqueConstraintInTheDatabase() {
        saveReader("Maria Silva", "DOC-1");

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into reader (name, document, email, birth_date)
                values ('Outro', 'DOC1', 'o@example.com', date '1990-01-01')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateDocumentShouldBecomeBusinessRuleExceptionOnCreation() {
        saveReader("Maria Silva", "DOC-1");

        assertThatThrownBy(() -> saveReader("João", "doc 1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("DOC1");
    }

    @Test
    void duplicateDocumentShouldBecomeBusinessRuleExceptionOnUpdate() {
        saveReader("Maria Silva", "111");
        var other = saveReader("João", "222");
        other.updateDetails("João", "111", "reader@example.com", null, ADULT_BIRTH, null, TODAY);

        assertThatThrownBy(() -> adapter.save(other)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotConfuseOtherIntegrityViolationsWithADuplicateDocument() {
        // an e-mail longer than the column is not a duplicate document
        var tooLong = Reader.reconstitute(null, "Maria", "111", "a".repeat(300) + "@example.com", null,
                ADULT_BIRTH, null, true, false);

        assertThatThrownBy(() -> adapter.save(tooLong))
                .isNotInstanceOf(BusinessRuleException.class);
    }

    @Test
    void existsByDocumentShouldReflectWhatHasAlreadyBeenSaved() {
        saveReader("Maria Silva", "111");

        assertThat(adapter.existsByDocument("111")).isTrue();
        assertThat(adapter.existsByDocument("999")).isFalse();
    }

    @Test
    void existsByDocumentAndIdNotShouldIgnoreTheReaderItself() {
        var maria = saveReader("Maria Silva", "111");
        var joao = saveReader("João", "222");

        assertThat(adapter.existsByDocumentAndIdNot("111", maria.getId())).isFalse();
        assertThat(adapter.existsByDocumentAndIdNot("111", joao.getId())).isTrue();
        assertThat(adapter.existsByDocumentAndIdNot("999", joao.getId())).isFalse();
    }

    // listing and filters (RF07, RNF14)

    @Test
    void shouldListAllReadersWithoutFilters() {
        saveReader("Bruno", "2");
        saveReader("Ana", "1");

        var page = adapter.findAll(ReaderFilter.none(), byName());

        assertThat(page.items()).extracting(Reader::getName).containsExactly("Ana", "Bruno");
        assertThat(page.totalItems()).isEqualTo(2);
    }

    @Test
    void shouldFilterByNameContainingIgnoringCase() {
        saveReader("Maria da Silva", "1");
        saveReader("Mariana Costa", "2");
        saveReader("Pedro Alves", "3");

        var page = adapter.findAll(new ReaderFilter("MARIA", null, null, null), byName());

        assertThat(page.items()).extracting(Reader::getName).containsExactly("Maria da Silva", "Mariana Costa");
        assertThat(adapter.findAll(new ReaderFilter("da s", null, null, null), byName()).items())
                .extracting(Reader::getName).containsExactly("Maria da Silva");
    }

    @Test
    void shouldTreatLikeWildcardsInTheNameFilterLiterally() {
        saveReader("100% Leitor", "1");
        saveReader("Ana_Maria", "2");
        saveReader("Anaxmaria", "3");
        saveReader("Pedro", "4");

        assertThat(adapter.findAll(new ReaderFilter("%", null, null, null), byName()).items())
                .extracting(Reader::getName).containsExactly("100% Leitor");
        assertThat(adapter.findAll(new ReaderFilter("_", null, null, null), byName()).items())
                .extracting(Reader::getName).containsExactly("Ana_Maria");
        assertThat(adapter.findAll(new ReaderFilter("!", null, null, null), byName()).items()).isEmpty();
    }

    @Test
    void shouldFilterByDocumentByEqualityNotByPart() {
        saveReader("Maria", "12345678900");
        saveReader("João", "123456789001");

        var found = adapter.findAll(new ReaderFilter(null, "123.456.789-00", null, null), byName());

        assertThat(found.items()).extracting(Reader::getName).containsExactly("Maria");
        assertThat(adapter.findAll(new ReaderFilter(null, "1234", null, null), byName()).items()).isEmpty();
    }

    @Test
    void shouldFilterByActiveAndByBlocked() {
        var active = saveReader("Ativo", "1");
        var blocked = saveReader("Bloqueado", "2");
        blocked.block();
        adapter.save(blocked);
        var inactive = saveReader("Inativo", "3");
        inactive.deactivate();
        adapter.save(inactive);

        assertThat(adapter.findAll(new ReaderFilter(null, null, true, null), byName()).items())
                .extracting(Reader::getName).containsExactly("Ativo", "Bloqueado");
        assertThat(adapter.findAll(new ReaderFilter(null, null, false, null), byName()).items())
                .extracting(Reader::getName).containsExactly("Inativo");
        assertThat(adapter.findAll(new ReaderFilter(null, null, null, true), byName()).items())
                .extracting(Reader::getName).containsExactly("Bloqueado");
        assertThat(adapter.findAll(new ReaderFilter(null, null, true, false), byName()).items())
                .extracting(Reader::getId).containsExactly(active.getId());
    }

    @Test
    void shouldCombineAllTheFiltersWithAnd() {
        saveReader("Maria Silva", "1");
        var blocked = saveReader("Maria Costa", "2");
        blocked.block();
        adapter.save(blocked);

        var found = adapter.findAll(new ReaderFilter("maria", "2", true, true), byName());

        assertThat(found.items()).extracting(Reader::getName).containsExactly("Maria Costa");
        assertThat(adapter.findAll(new ReaderFilter("maria", "1", true, true), byName()).items()).isEmpty();
    }

    @Test
    void shouldPaginateAndSortUsingPageQueryAndPageResult() {
        saveReader("Ana", "1");
        saveReader("Bruno", "2");
        saveReader("Carla", "3");

        var second = adapter.findAll(ReaderFilter.none(), PageQuery.of(1, 2, "name,desc"));

        assertThat(second.items()).extracting(Reader::getName).containsExactly("Ana");
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(2);
        assertThat(second.totalItems()).isEqualTo(3);
    }

    @Test
    void shouldReturnAnEmptyPageWhenNothingMatches() {
        saveReader("Ana", "1");

        var page = adapter.findAll(new ReaderFilter("zzz", null, null, null), byName());

        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
    }


    @Test
    void shouldFindTheNamesOfSeveralReadersInOneCall() {
        var maria = saveReader("Maria Silva", "111");
        var joao = saveReader("João Souza", "222");
        saveReader("Ana Lima", "333");

        var names = adapter.findNamesByIds(java.util.Set.of(maria.getId(), joao.getId(), -1L));

        assertThat(names).containsOnlyKeys(maria.getId(), joao.getId());
        assertThat(names.get(maria.getId())).isEqualTo("Maria Silva");
        assertThat(names.get(joao.getId())).isEqualTo("João Souza");
    }

    @Test
    void shouldFindTheContactsOfSeveralReadersInOneCall() {
        var maria = saveReader("Maria Silva", "111");
        saveReader("Ana Lima", "333");

        var contacts = adapter.findContactsByIds(java.util.Set.of(maria.getId(), -1L));

        assertThat(contacts).containsOnlyKeys(maria.getId());
        assertThat(contacts.get(maria.getId()).name()).isEqualTo("Maria Silva");
        assertThat(contacts.get(maria.getId()).email()).isEqualTo(maria.getEmail());
    }

    @Test
    void shouldFindNoContactsForNoIds() {
        assertThat(adapter.findContactsByIds(java.util.Set.of())).isEmpty();
    }

    @Test
    void shouldFindNoNamesForNoIds() {
        assertThat(adapter.findNamesByIds(java.util.Set.of())).isEmpty();
    }

    // e-mail lookup for the password reset (Phase 6)

    @Test
    void shouldFindTheActiveReadersByEmailIgnoringCase() {
        var ana = adapter.save(Reader.create("Ana", "EM-1", "Ana@Example.com", null, ADULT_BIRTH, null, TODAY));
        adapter.save(Reader.create("Bia", "EM-2", "bia@example.com", null, ADULT_BIRTH, null, TODAY));

        assertThat(adapter.findActiveByEmail("  ana@example.COM ")).extracting(Reader::getId)
                .containsExactly(ana.getId());
        assertThat(adapter.findActiveByEmail("ninguem@example.com")).isEmpty();
    }

    @Test
    void shouldNotFindAnInactiveReaderByEmail() {
        var ana = adapter.save(Reader.create("Ana", "EM-3", "ana3@example.com", null, ADULT_BIRTH, null, TODAY));
        ana.deactivate();
        adapter.save(ana);

        assertThat(adapter.findActiveByEmail("ana3@example.com")).isEmpty();
    }

    @Test
    void shouldFindEveryActiveReaderThatSharesTheEmail() {
        adapter.save(Reader.create("Pai", "EM-4", "familia@example.com", null, ADULT_BIRTH, null, TODAY));
        adapter.save(Reader.create("Mae", "EM-5", "familia@example.com", null, ADULT_BIRTH, null, TODAY));

        assertThat(adapter.findActiveByEmail("familia@example.com")).hasSize(2);
    }
}
