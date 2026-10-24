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

    /**
     * The copy is kept for a reader in the queue of its book (RF16, RF18): only an available and
     * active copy can be reserved (RN03).
     */
    public void reserve() {
        if (status != CopyStatus.AVAILABLE || !active) {
            throw new BusinessRuleException("o exemplar só pode ser reservado se estiver disponível e ativo");
        }
        this.status = CopyStatus.RESERVED;
    }

    /**
     * The reservation that held the copy ended without a pickup (cancelled or expired, RF17, RF19):
     * the copy goes back to available. Only a reserved copy can be released.
     */
    public void releaseReservation() {
        requireReserved();
        this.status = CopyStatus.AVAILABLE;
    }

    /**
     * The reader picked up the copy kept for them (RF17): a reserved copy becomes borrowed. It must
     * still be active, like any copy that goes out on a loan.
     */
    public void checkOutReserved() {
        requireReserved();
        if (!active) {
            throw new BusinessRuleException("o exemplar inativo não pode ser emprestado");
        }
        this.status = CopyStatus.BORROWED;
    }

    private void requireReserved() {
        if (status != CopyStatus.RESERVED) {
            throw new BusinessRuleException("o exemplar não está reservado");
        }
    }

    /**
     * The copy came back from a loan in good condition: a borrowed copy becomes available. A
     * damaged or lost copy stays as it is, since coming back does not repair it; it only returns
     * to available by the manual change (RF04). Any other status is a mistake and is refused.
     */
    public void checkIn() {
        checkIn(CopyReturnCondition.GOOD);
    }

    /**
     * The copy came back from a loan in the given condition (RF15): in good condition it becomes
     * available, damaged it becomes DAMAGED and lost it becomes LOST, so it never goes back on
     * the shelf by itself. This is the only way a borrowed copy turns damaged or lost; the manual
     * change refuses it (RN10). A copy already damaged or lost keeps its status, and any other
     * status that is not BORROWED is a mistake and is refused.
     */
    public void checkIn(CopyReturnCondition condition) {
        if (condition == null) {
            throw new BusinessRuleException("a condição do exemplar na devolução é obrigatória");
        }
        if (status == CopyStatus.DAMAGED || status == CopyStatus.LOST) {
            return;
        }
        if (status != CopyStatus.BORROWED) {
            throw new BusinessRuleException("o exemplar só pode ser devolvido se estiver emprestado");
        }
        this.status = switch (condition) {
            case GOOD -> CopyStatus.AVAILABLE;
            case DAMAGED -> CopyStatus.DAMAGED;
            case LOST -> CopyStatus.LOST;
        };
    }

    public void markAsDamaged() {
        this.status = CopyStatus.DAMAGED;
    }

    public void markAsLost() {
        this.status = CopyStatus.LOST;
    }

    /**
     * Manual status change (RF04, HU23). Only operational transitions are
     * allowed here: damaged, lost and back to available once a damaged or
     * lost copy is recovered. A borrowed copy cannot be marked damaged or
     * lost by hand (RN10), and BORROWED/RESERVED are set only by the loan
     * and reservation flows. Setting the current status is a no-op.
     */
    public void changeStatus(CopyStatus target) {
        if (target == null) {
            throw new BusinessRuleException("o status do exemplar é obrigatório");
        }
        if (target == status) {
            return;
        }
        switch (target) {
            case DAMAGED, LOST -> {
                if (status == CopyStatus.BORROWED) {
                    throw new BusinessRuleException(
                            "o exemplar emprestado não pode ser marcado como danificado ou extraviado "
                                    + "manualmente; registre pela devolução");
                }
                this.status = target;
            }
            case AVAILABLE -> {
                if (status != CopyStatus.DAMAGED && status != CopyStatus.LOST) {
                    throw new BusinessRuleException(
                            "só um exemplar danificado ou extraviado pode voltar a disponível manualmente");
                }
                this.status = CopyStatus.AVAILABLE;
            }
            case BORROWED, RESERVED -> throw new BusinessRuleException(
                    "o status " + target + " só muda pelos fluxos de empréstimo, devolução e reserva");
        }
    }

    /**
     * Deactivates the copy (RN10, RN14): refused while it is out on a loan. The loan itself is
     * the source of truth, so whoever calls brings that fact ({@code hasActiveLoan}) the way
     * {@link #validateUniqueAssetCode} gets its fact; the status {@code BORROWED} is checked as
     * well, so a copy marked as borrowed is never deactivated either.
     *
     * @param hasActiveLoan whether the copy has a loan that was not returned yet
     */
    public void deactivate(boolean hasActiveLoan) {
        if (hasActiveLoan || status == CopyStatus.BORROWED) {
            throw new BusinessRuleException(
                    "o exemplar não pode ser inativado enquanto estiver emprestado: há um empréstimo ativo");
        }
        this.active = false;
    }

    /**
     * Brings an inactivated copy back (RN14), keeping its status. A copy is only registered for an active
     * book, so it is only reactivated when its book is active; whoever calls brings that fact. Reactivating
     * an active copy changes nothing.
     *
     * @param bookActive whether the book of the copy is active
     */
    public void reactivate(boolean bookActive) {
        if (active) {
            return;
        }
        if (!bookActive) {
            throw new BusinessRuleException("o exemplar não pode ser reativado: o livro está inativo");
        }
        this.active = true;
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
