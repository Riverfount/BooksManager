package com.riverfount.booksmanager.catalog.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;

/**
 * Copy of the collection (RF04): the physical unit of the book, the unit
 * of the loan. References the book by identifier (bookId). The version is
 * only carried by the domain; the actual optimistic lock (RNF17) belongs
 * to the persistence adapter, via {@code @Version}.
 */
public class Copy {

    public static final int MAX_ASSET_CODE_LENGTH = 50;

    private final Long id;
    private final Long bookId;
    private final String assetCode;
    private CopyStatus status;
    private boolean active;
    private final Long version;

    private Copy(Long id, Long bookId, String assetCode, CopyStatus status, boolean active, Long version) {
        this.id = id;
        this.bookId = bookId;
        this.assetCode = assetCode;
        this.status = status;
        this.active = active;
        this.version = version;
    }

    public static Copy create(Long bookId, String assetCode) {
        validateBookId(bookId);
        validateAssetCode(assetCode);
        return new Copy(null, bookId, assetCode, CopyStatus.AVAILABLE, true, 0L);
    }

    public static Copy reconstitute(Long id, Long bookId, String assetCode, CopyStatus status, boolean active,
            Long version) {
        return new Copy(id, bookId, assetCode, status, active, version);
    }

    public void checkOut() {
        if (status != CopyStatus.AVAILABLE || !active) {
            throw new BusinessRuleException("o exemplar só pode ser emprestado se estiver disponível e ativo");
        }
        this.status = CopyStatus.BORROWED;
    }

    public void checkIn() {
        if (status != CopyStatus.BORROWED) {
            throw new BusinessRuleException("o exemplar só pode ser devolvido se estiver emprestado");
        }
        this.status = CopyStatus.AVAILABLE;
    }

    public void markAsDamaged() {
        this.status = CopyStatus.DAMAGED;
    }

    public void markAsLost() {
        this.status = CopyStatus.LOST;
    }

    public void deactivate() {
        if (status == CopyStatus.BORROWED) {
            throw new BusinessRuleException("o exemplar não pode ser inativado enquanto estiver emprestado");
        }
        this.active = false;
    }

    private static void validateBookId(Long bookId) {
        if (bookId == null) {
            throw new BusinessRuleException("o id do livro é obrigatório");
        }
    }

    private static void validateAssetCode(String assetCode) {
        if (assetCode == null || assetCode.isBlank()) {
            throw new BusinessRuleException("o código de patrimônio é obrigatório");
        }
        if (!assetCode.equals(assetCode.strip())) {
            throw new BusinessRuleException("o código de patrimônio não pode ter espaços nas pontas");
        }
        if (assetCode.length() > MAX_ASSET_CODE_LENGTH) {
            throw new BusinessRuleException(
                    "o código de patrimônio deve ter até " + MAX_ASSET_CODE_LENGTH + " caracteres");
        }
    }

    /**
     * Decides whether the asset code can be used, given whether another
     * copy already has it. Whoever fetches that fact (the application, via
     * the repository) does not decide; it only brings the fact for the
     * domain to decide.
     */
    public static void validateUniqueAssetCode(String assetCode, boolean alreadyExistsWithThisCode) {
        if (alreadyExistsWithThisCode) {
            throw new BusinessRuleException(
                    "já existe um exemplar com o código de patrimônio '" + assetCode + "'");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public CopyStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return active;
    }

    public Long getVersion() {
        return version;
    }
}
