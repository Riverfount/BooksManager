package com.riverfount.booksmanager.loan.adapter.out.persistence;

import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import com.riverfount.booksmanager.loan.domain.LibraryConfiguration;
import org.springframework.stereotype.Component;

@Component
class LibraryConfigurationPersistenceAdapter implements LibraryConfigurationRepository {

    /** The only row there is: the table fixes the id at 1. */
    private static final Short THE_ROW = 1;

    private final LibraryConfigurationJpaRepository jpaRepository;

    LibraryConfigurationPersistenceAdapter(LibraryConfigurationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public LibraryConfiguration get() {
        return toDomain(jpaRepository.findById(THE_ROW).orElseThrow(LibraryConfigurationPersistenceAdapter::missing));
    }

    @Override
    public LibraryConfiguration getForUpdate() {
        return toDomain(jpaRepository.findByIdForUpdate(THE_ROW)
                .orElseThrow(LibraryConfigurationPersistenceAdapter::missing));
    }

    @Override
    public LibraryConfiguration save(LibraryConfiguration configuration) {
        var entity = new LibraryConfigurationJpaEntity(THE_ROW, configuration.getPenaltyPolicy(),
                configuration.getDailyFineAmount(), configuration.getFineCap(),
                configuration.getSuspensionDaysPerDay(), configuration.getSuspensionCapDays(),
                configuration.getLoanPeriodDays(), configuration.getLoanLimit(), configuration.getRenewalLimit(),
                configuration.getReservationPickupDays());
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    private static IllegalStateException missing() {
        return new IllegalStateException(
                "the library configuration row is missing; migration V6__create_library_configuration.sql creates it");
    }

    private LibraryConfiguration toDomain(LibraryConfigurationJpaEntity entity) {
        return LibraryConfiguration.reconstitute(entity.getId().longValue(), entity.getPenaltyPolicy(),
                entity.getDailyFineAmount(), entity.getFineCap(), entity.getSuspensionDaysPerDay(),
                entity.getSuspensionCapDays(), entity.getLoanPeriodDays(), entity.getLoanLimit(),
                entity.getRenewalLimit(), entity.getReservationPickupDays());
    }
}
