package com.riverfount.booksmanager.user.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Reader of the library (RF07, RF10), separate from the system user. Holds
 * only the minimal personal data (RNF11). Readers with loan history are
 * deactivated, never physically deleted (RN14).
 *
 * <p>Dates come by parameter: the reference date ({@code today}) is never
 * read from the system clock here, so the age rules are deterministic.
 *
 * <p>State rules: {@link #block()}, {@link #unblock()} and
 * {@link #deactivate()} are idempotent. An inactive reader cannot be
 * blocked, but a blocked reader can be deactivated and stays blocked, so
 * unblocking remains possible afterwards.
 */
public class Reader {

    public static final int MAX_NAME_LENGTH = PersonalData.MAX_NAME_LENGTH;
    public static final int MAX_DOCUMENT_LENGTH = 30;
    public static final int ADULT_AGE = 18;

    private final Long id;
    private String name;
    private String document;
    private String email;
    private String phone;
    private LocalDate birthDate;
    private Guardian guardian;
    private boolean active;
    private boolean blocked;

    private Reader(Long id, String name, String document, String email, String phone, LocalDate birthDate,
            Guardian guardian, boolean active, boolean blocked) {
        this.id = id;
        this.name = name;
        this.document = document;
        this.email = email;
        this.phone = phone;
        this.birthDate = birthDate;
        this.guardian = guardian;
        this.active = active;
        this.blocked = blocked;
    }

    public static Reader create(String name, String document, String email, String phone, LocalDate birthDate,
            Guardian guardian, LocalDate today) {
        var reader = new Reader(null, null, null, null, null, null, null, true, false);
        reader.updateDetails(name, document, email, phone, birthDate, guardian, today);
        return reader;
    }

    public static Reader reconstitute(Long id, String name, String document, String email, String phone,
            LocalDate birthDate, Guardian guardian, boolean active, boolean blocked) {
        return new Reader(id, name, document, email, phone, birthDate, guardian, active, blocked);
    }

    /**
     * Unique document rule (RF07). Like {@code Copy.validateUniqueAssetCode},
     * the application service looks up whether the (already normalized)
     * document exists and the domain decides.
     */
    public static void validateUniqueDocument(String document, boolean alreadyExistsWithThisDocument) {
        if (alreadyExistsWithThisDocument) {
            throw new BusinessRuleException("já existe um leitor com o documento '" + document + "'");
        }
    }

    /**
     * Replaces the personal data. Nothing changes if any value is invalid.
     * A reader under 18 on {@code today} requires the guardian; an adult may
     * have none.
     */
    public void updateDetails(String name, String document, String email, String phone, LocalDate birthDate,
            Guardian guardian, LocalDate today) {
        PersonalData.validateName(name, "do leitor");
        var normalizedDocument = validDocument(document);
        PersonalData.validateEmail(email, "do leitor");
        PersonalData.validateOptionalPhone(phone, "do leitor");
        validateBirthDate(birthDate, today);
        if (isMinor(birthDate, today) && guardian == null) {
            throw new BusinessRuleException("o responsável é obrigatório para leitor menor de "
                    + ADULT_AGE + " anos");
        }
        this.name = name;
        this.document = normalizedDocument;
        this.email = email;
        this.phone = phone;
        this.birthDate = birthDate;
        this.guardian = guardian;
    }

    public void deactivate() {
        this.active = false;
    }

    public void block() {
        if (blocked) {
            return;
        }
        if (!active) {
            throw new BusinessRuleException("o leitor inativo não pode ser bloqueado");
        }
        this.blocked = true;
    }

    public void unblock() {
        this.blocked = false;
    }

    public boolean isMinor(LocalDate today) {
        return isMinor(birthDate, today);
    }

    /**
     * Whether the reader may borrow (RN11): active and not blocked. The
     * suspension joins this check in phase 3.
     */
    public boolean canBorrow(LocalDate today) {
        return active && !blocked;
    }

    private static boolean isMinor(LocalDate birthDate, LocalDate today) {
        return birthDate.plusYears(ADULT_AGE).isAfter(today);
    }

    /**
     * Document normalization (RF07): no punctuation or spaces, uppercase. A
     * null document becomes empty. Public so that searches by document use
     * the same rule as the registration.
     */
    public static String normalizeDocument(String document) {
        return document == null ? "" : document.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase(Locale.ROOT);
    }

    private static String validDocument(String document) {
        var normalized = normalizeDocument(document);
        if (normalized.isEmpty()) {
            throw new BusinessRuleException("o documento do leitor é obrigatório");
        }
        if (normalized.length() > MAX_DOCUMENT_LENGTH) {
            throw new BusinessRuleException(
                    "o documento do leitor deve ter até " + MAX_DOCUMENT_LENGTH + " caracteres");
        }
        return normalized;
    }

    private static void validateBirthDate(LocalDate birthDate, LocalDate today) {
        if (birthDate == null) {
            throw new BusinessRuleException("a data de nascimento do leitor é obrigatória");
        }
        if (birthDate.isAfter(today)) {
            throw new BusinessRuleException("a data de nascimento do leitor não pode ser futura");
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDocument() {
        return document;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Guardian getGuardian() {
        return guardian;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isBlocked() {
        return blocked;
    }
}
