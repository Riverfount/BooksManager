package com.riverfount.booksmanager.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReaderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final LocalDate ADULT_BIRTH = LocalDate.of(1990, 5, 10);
    private static final Guardian GUARDIAN = new Guardian("Ana Souza", "ana@example.com", null);

    private static Reader adult() {
        return Reader.create("Maria Silva", "123.456.789-00", "maria@example.com", "11 91234-5678",
                ADULT_BIRTH, null, TODAY);
    }

    // creation (RF07)

    @Test
    void shouldCreateActiveAndNotBlockedReader() {
        var reader = adult();

        assertThat(reader.getId()).isNull();
        assertThat(reader.getName()).isEqualTo("Maria Silva");
        assertThat(reader.getEmail()).isEqualTo("maria@example.com");
        assertThat(reader.getPhone()).isEqualTo("11 91234-5678");
        assertThat(reader.getBirthDate()).isEqualTo(ADULT_BIRTH);
        assertThat(reader.getGuardian()).isNull();
        assertThat(reader.isActive()).isTrue();
        assertThat(reader.isBlocked()).isFalse();
    }

    @Test
    void shouldReconstituteReaderWithPersistedState() {
        var reader = Reader.reconstitute(7L, "Maria Silva", "12345678900", "maria@example.com", null,
                ADULT_BIRTH, null, false, true);

        assertThat(reader.getId()).isEqualTo(7L);
        assertThat(reader.isActive()).isFalse();
        assertThat(reader.isBlocked()).isTrue();
    }

    @Test
    void shouldAcceptReaderWithoutPhone() {
        var reader = Reader.create("Maria Silva", "123", "maria@example.com", null, ADULT_BIRTH, null, TODAY);

        assertThat(reader.getPhone()).isNull();
    }

    // name

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", " Maria", "Maria "})
    void shouldRejectNameThatIsMissingOrHasSurroundingSpaces(String name) {
        assertThatThrownBy(() -> Reader.create(name, "123", "maria@example.com", null, ADULT_BIRTH, null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nome do leitor");
    }

    @Test
    void shouldRejectNameLongerThanTheLimit() {
        var name = "a".repeat(Reader.MAX_NAME_LENGTH + 1);

        assertThatThrownBy(() -> Reader.create(name, "123", "maria@example.com", null, ADULT_BIRTH, null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nome do leitor");
    }

    // document

    @Test
    void shouldNormalizeDocumentRemovingPunctuationAndSpacesAndUppercasing() {
        var cpf = Reader.create("Maria", "123.456.789-00", "maria@example.com", null, ADULT_BIRTH, null, TODAY);
        var registration = Reader.create("Maria", " ab-123 / x ", "maria@example.com", null, ADULT_BIRTH, null,
                TODAY);

        assertThat(cpf.getDocument()).isEqualTo("12345678900");
        assertThat(registration.getDocument()).isEqualTo("AB123X");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", ".-/"})
    void shouldRejectDocumentThatIsMissingOrEmptyAfterNormalization(String document) {
        assertThatThrownBy(() -> Reader.create("Maria", document, "maria@example.com", null, ADULT_BIRTH, null,
                TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("documento");
    }

    @Test
    void shouldRejectDocumentLongerThanTheLimitAfterNormalization() {
        var document = "1".repeat(Reader.MAX_DOCUMENT_LENGTH + 1);

        assertThatThrownBy(() -> Reader.create("Maria", document, "maria@example.com", null, ADULT_BIRTH, null,
                TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("documento");
    }

    // email and phone

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "maria", "maria@", "@example.com", "maria@example", "ma ria@example.com"})
    void shouldRejectEmailThatIsMissingOrInvalid(String email) {
        assertThatThrownBy(() -> Reader.create("Maria", "123", email, null, ADULT_BIRTH, null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("e-mail");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "123", "11 9999-99x9", "1234567890123456"})
    void shouldRejectPhoneWithInvalidFormat(String phone) {
        assertThatThrownBy(() -> Reader.create("Maria", "123", "maria@example.com", phone, ADULT_BIRTH, null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("telefone");
    }

    // birth date

    @Test
    void shouldRejectMissingBirthDate() {
        assertThatThrownBy(() -> Reader.create("Maria", "123", "maria@example.com", null, null, null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("data de nascimento");
    }

    @Test
    void shouldRejectFutureBirthDateButAcceptTodayAsReference() {
        assertThatThrownBy(() -> Reader.create("Maria", "123", "maria@example.com", null, TODAY.plusDays(1), null,
                TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("data de nascimento");

        var newborn = Reader.create("Bebê", "123", "maria@example.com", null, TODAY, GUARDIAN, TODAY);

        assertThat(newborn.getBirthDate()).isEqualTo(TODAY);
    }

    // minor and guardian (RNF11)

    @Test
    void shouldConsiderReaderMinorUntilTheEighteenthBirthday() {
        var birth = LocalDate.of(2008, 10, 16);
        var reader = Reader.create("Júlia", "123", "julia@example.com", null, birth, GUARDIAN, TODAY);

        assertThat(reader.isMinor(LocalDate.of(2026, 10, 15))).isTrue();
        assertThat(reader.isMinor(LocalDate.of(2026, 10, 16))).isFalse();
    }

    @Test
    void shouldRejectMinorWithoutGuardianOnCreation() {
        var birth = LocalDate.of(2015, 3, 1);

        assertThatThrownBy(() -> Reader.create("Júlia", "123", "julia@example.com", null, birth, null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("responsável");
    }

    @Test
    void shouldAcceptMinorWithGuardian() {
        var birth = LocalDate.of(2015, 3, 1);

        var reader = Reader.create("Júlia", "123", "julia@example.com", null, birth, GUARDIAN, TODAY);

        assertThat(reader.getGuardian()).isEqualTo(GUARDIAN);
    }

    @Test
    void shouldAllowAdultWithoutGuardian() {
        assertThat(adult().getGuardian()).isNull();
    }

    @Test
    void shouldNotRequireGuardianOnceTheReaderTurnsEighteenWithoutChangingTheRecord() {
        var birth = LocalDate.of(2008, 10, 16);
        var minorToday = Reader.reconstitute(1L, "Júlia", "123", "julia@example.com", null, birth, GUARDIAN,
                true, false);

        assertThat(minorToday.isMinor(TODAY)).isTrue();
        assertThat(minorToday.isMinor(TODAY.plusDays(1))).isFalse();
        // as an adult, the same record can be edited with no guardian
        minorToday.updateDetails("Júlia", "123", "julia@example.com", null, birth, null, TODAY.plusDays(1));
        assertThat(minorToday.getGuardian()).isNull();
    }

    // update

    @Test
    void shouldUpdateDetails() {
        var reader = adult();

        reader.updateDetails("Maria Souza", "987.654.321-00", "souza@example.com", null,
                LocalDate.of(1991, 1, 2), null, TODAY);

        assertThat(reader.getName()).isEqualTo("Maria Souza");
        assertThat(reader.getDocument()).isEqualTo("98765432100");
        assertThat(reader.getEmail()).isEqualTo("souza@example.com");
        assertThat(reader.getPhone()).isNull();
        assertThat(reader.getBirthDate()).isEqualTo(LocalDate.of(1991, 1, 2));
    }

    @Test
    void shouldRejectUpdateThatLeavesMinorWithoutGuardian() {
        var reader = adult();
        var minorBirth = LocalDate.of(2015, 3, 1);

        assertThatThrownBy(() -> reader.updateDetails("Maria", "123", "maria@example.com", null, minorBirth, null,
                TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("responsável");
    }

    @Test
    void shouldKeepTheCurrentStateWhenUpdateIsRejected() {
        var reader = adult();

        assertThatThrownBy(() -> reader.updateDetails("Maria", "123", "invalid", null, ADULT_BIRTH, null, TODAY))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(reader.getEmail()).isEqualTo("maria@example.com");
        assertThat(reader.getDocument()).isEqualTo("12345678900");
    }

    // block and deactivate (RF10, RN14)

    @Test
    void shouldBlockAndUnblockReader() {
        var reader = adult();

        reader.block();
        assertThat(reader.isBlocked()).isTrue();

        reader.unblock();
        assertThat(reader.isBlocked()).isFalse();
    }

    @Test
    void blockAndUnblockShouldBeIdempotent() {
        var reader = adult();

        reader.unblock();
        assertThat(reader.isBlocked()).isFalse();

        reader.block();
        reader.block();
        assertThat(reader.isBlocked()).isTrue();

        reader.unblock();
        reader.unblock();
        assertThat(reader.isBlocked()).isFalse();
    }

    @Test
    void shouldNotBlockInactiveReader() {
        var reader = adult();
        reader.deactivate();

        assertThatThrownBy(reader::block)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inativo");
        assertThat(reader.isBlocked()).isFalse();
    }

    @Test
    void shouldDeactivateBlockedReaderKeepingItBlocked() {
        var reader = adult();
        reader.block();

        reader.deactivate();

        assertThat(reader.isActive()).isFalse();
        assertThat(reader.isBlocked()).isTrue();
    }

    @Test
    void shouldUnblockInactiveReaderThatWasBlockedBeforeBeingDeactivated() {
        var reader = adult();
        reader.block();
        reader.deactivate();

        reader.unblock();

        assertThat(reader.isBlocked()).isFalse();
        assertThat(reader.isActive()).isFalse();
    }

    @Test
    void blockingAnAlreadyBlockedInactiveReaderShouldBeANoOp() {
        var reader = adult();
        reader.block();
        reader.deactivate();

        reader.block();

        assertThat(reader.isBlocked()).isTrue();
    }

    @Test
    void deactivateShouldBeIdempotent() {
        var reader = adult();

        reader.deactivate();
        reader.deactivate();

        assertThat(reader.isActive()).isFalse();
    }

    // borrowing (RN11)

    @Test
    void activeAndNotBlockedReaderCanBorrow() {
        assertThat(adult().canBorrow(TODAY)).isTrue();
    }

    @Test
    void blockedReaderCannotBorrow() {
        var reader = adult();
        reader.block();

        assertThat(reader.canBorrow(TODAY)).isFalse();
    }

    @Test
    void inactiveReaderCannotBorrow() {
        var reader = adult();
        reader.deactivate();

        assertThat(reader.canBorrow(TODAY)).isFalse();
    }

    // unique document (RF07)

    @Test
    void shouldAcceptADocumentThatDoesNotExistYet() {
        Reader.validateUniqueDocument("12345678900", false);
    }

    @Test
    void shouldRejectADocumentThatAlreadyExists() {
        assertThatThrownBy(() -> Reader.validateUniqueDocument("12345678900", true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("já existe um leitor")
                .hasMessageContaining("12345678900");
    }

    // own contact data (RF32, RN22)

    private static Reader minor() {
        return Reader.create("Pedro Silva", "987", "pedro@example.com", null, LocalDate.of(2012, 3, 1), GUARDIAN,
                TODAY);
    }

    @Test
    void shouldUpdateTheContactDataKeepingTheIdentityData() {
        var reader = adult();

        reader.updateContact("21 98888-7777", "novo@example.com", null, TODAY);

        assertThat(reader.getPhone()).isEqualTo("21 98888-7777");
        assertThat(reader.getEmail()).isEqualTo("novo@example.com");
        assertThat(reader.getName()).isEqualTo("Maria Silva");
        assertThat(reader.getDocument()).isEqualTo("12345678900");
        assertThat(reader.getBirthDate()).isEqualTo(ADULT_BIRTH);
    }

    @Test
    void shouldUpdateTheGuardianOfAMinor() {
        var reader = minor();
        var other = new Guardian("Carla Souza", "carla@example.com", "11 90000-1111");

        reader.updateContact(null, "pedro@example.com", other, TODAY);

        assertThat(reader.getGuardian()).isEqualTo(other);
    }

    @Test
    void shouldNotChangeTheGuardianOfAnAdult() {
        var reader = adult();

        assertThatThrownBy(() -> reader.updateContact(reader.getPhone(), reader.getEmail(), GUARDIAN, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("responsável")
                .hasMessageContaining("18");
        assertThat(reader.getGuardian()).isNull();
    }

    @Test
    void shouldAllowAnAdultToKeepTheGuardianHeAlreadyHad() {
        // the reader turned 18 and still has the guardian of the time he was a minor
        var reader = Reader.reconstitute(5L, "Pedro Silva", "987", "pedro@example.com", null,
                LocalDate.of(2008, 3, 1), GUARDIAN, true, false);

        reader.updateContact("11 91234-5678", "pedro@example.com", GUARDIAN, TODAY);

        assertThat(reader.getGuardian()).isEqualTo(GUARDIAN);
        assertThat(reader.getPhone()).isEqualTo("11 91234-5678");
    }

    @Test
    void shouldRequireTheGuardianOfAMinor() {
        var reader = minor();

        assertThatThrownBy(() -> reader.updateContact(null, "pedro@example.com", null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("responsável");
    }

    @ParameterizedTest
    @ValueSource(strings = { "sem-arroba", "a@b", "a b@c.com" })
    void shouldRejectAnInvalidEmailOnContactUpdateAndChangeNothing(String email) {
        var reader = adult();

        assertThatThrownBy(() -> reader.updateContact("21 98888-7777", email, null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("e-mail");
        assertThat(reader.getPhone()).isEqualTo("11 91234-5678");
        assertThat(reader.getEmail()).isEqualTo("maria@example.com");
    }

    @Test
    void shouldRejectAnInvalidPhoneOnContactUpdate() {
        var reader = adult();

        assertThatThrownBy(() -> reader.updateContact("abc", reader.getEmail(), null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("telefone");
    }

    @Test
    void shouldKeepTheBlockedAndActiveStateOnContactUpdate() {
        var reader = Reader.reconstitute(9L, "Maria Silva", "123", "maria@example.com", null, ADULT_BIRTH, null,
                true, true);

        reader.updateContact("21 98888-7777", "maria@example.com", null, TODAY);

        assertThat(reader.isBlocked()).isTrue();
        assertThat(reader.isActive()).isTrue();
    }
}
