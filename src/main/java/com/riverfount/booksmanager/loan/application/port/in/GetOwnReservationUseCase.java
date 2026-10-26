package com.riverfount.booksmanager.loan.application.port.in;

/** Shows a reservation of the reader, active or closed (Phase 6, RF16). */
public interface GetOwnReservationUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the reservation does not exist
     *         or belongs to another reader: the two cases cannot be told apart
     */
    ReservationDetails get(GetOwnReservationQuery query);
}
