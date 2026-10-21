package com.riverfount.booksmanager.loan.adapter.out.persistence;

import com.riverfount.booksmanager.loan.application.port.out.ReservationFilter;
import com.riverfount.booksmanager.loan.application.port.out.ReservationRepository;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import com.riverfount.booksmanager.shared.adapter.out.persistence.ConstraintViolations;
import com.riverfount.booksmanager.shared.adapter.out.persistence.SpringDataPages;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class ReservationPersistenceAdapter implements ReservationRepository {

    private final ReservationJpaRepository jpaRepository;

    ReservationPersistenceAdapter(ReservationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Reservation save(Reservation reservation) {
        var entity = reservation.getId() == null ? create(reservation) : update(reservation);
        try {
            // flushed here so that the violation of a unique index fails inside this try
            return toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // the partial unique indexes are what actually enforce the rules (RN12), including the
            // race between two requests; here we only translate them, and only when the violation
            // really is one of them.
            if (ConstraintViolations.violates(e, "uq_reservation_active_reader_book")) {
                throw new BusinessRuleException("o leitor já tem uma reserva ativa deste livro");
            }
            if (ConstraintViolations.violates(e, "uq_reservation_active_copy")) {
                throw new BusinessRuleException("o exemplar já está guardado para outra reserva ativa");
            }
            throw e;
        }
    }

    @Override
    public Optional<Reservation> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public PageResult<Reservation> findAll(ReservationFilter filter, PageQuery page) {
        var result = jpaRepository.search(filter.readerId(), filter.bookId(), filter.status(),
                SpringDataPages.toPageable(page));
        return SpringDataPages.toPageResult(result, this::toDomain);
    }

    @Override
    public Optional<Reservation> findActiveByCopyId(Long copyId) {
        return jpaRepository.findByCopyIdAndStatus(copyId, ReservationStatus.ACTIVE).map(this::toDomain);
    }

    @Override
    public Optional<Reservation> findFirstWaiting(Long bookId) {
        return jpaRepository.findFirstByBookIdAndStatusAndCopyIdIsNullOrderByIdAsc(bookId, ReservationStatus.ACTIVE)
                .map(this::toDomain);
    }

    @Override
    public Optional<Long> queuePosition(Long reservationId) {
        return jpaRepository.findById(reservationId)
                .filter(entity -> entity.getStatus() == ReservationStatus.ACTIVE && entity.getCopyId() == null)
                .map(entity -> jpaRepository.countByBookIdAndStatusAndCopyIdIsNullAndIdLessThanEqual(
                        entity.getBookId(), ReservationStatus.ACTIVE, entity.getId()));
    }

    @Override
    public Map<Long, Long> queuePositions(Set<Long> reservationIds) {
        var positions = new HashMap<Long, Long>();
        if (reservationIds.isEmpty()) {
            return positions;
        }
        for (var row : jpaRepository.queuePositions(reservationIds, ReservationStatus.ACTIVE)) {
            positions.put((Long) row[0], (Long) row[1]);
        }
        return positions;
    }

    @Override
    public long countActiveByReader(Long readerId) {
        return jpaRepository.countByReaderIdAndStatus(readerId, ReservationStatus.ACTIVE);
    }

    @Override
    public List<Reservation> findPastDeadline(LocalDate today) {
        return jpaRepository
                .findByStatusAndCopyIdIsNotNullAndPickupDeadlineBeforeOrderByPickupDeadlineAscIdAsc(
                        ReservationStatus.ACTIVE, today)
                .stream().map(this::toDomain).toList();
    }

    private static ReservationJpaEntity create(Reservation reservation) {
        return new ReservationJpaEntity(reservation.getBookId(), reservation.getReaderId(),
                reservation.getReservationDate(), reservation.getStatus(), reservation.getCopyId(),
                reservation.getPickupDeadline());
    }

    /**
     * Updates the entity loaded in the current transaction instead of building a new one,
     * so its version is the one read by the use case and a concurrent change fails the save.
     */
    private ReservationJpaEntity update(Reservation reservation) {
        var entity = jpaRepository.findById(reservation.getId()).orElseThrow(
                () -> new IllegalStateException("the reservation " + reservation.getId() + " does not exist"));
        entity.update(reservation.getStatus(), reservation.getCopyId(), reservation.getPickupDeadline());
        return entity;
    }

    private Reservation toDomain(ReservationJpaEntity entity) {
        return Reservation.reconstitute(entity.getId(), entity.getBookId(), entity.getReaderId(),
                entity.getReservationDate(), entity.getStatus(), entity.getCopyId(), entity.getPickupDeadline());
    }
}
