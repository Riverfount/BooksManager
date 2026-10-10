package com.riverfount.booksmanager.catalog.application.port.out;

import com.riverfount.booksmanager.catalog.domain.Copy;
import java.util.Optional;

/**
 * Outbound port for Copy persistence, in the domain's language.
 */
public interface CopyRepository {

    Copy save(Copy copy);

    Optional<Copy> findById(Long id);

    boolean existsByAssetCode(String assetCode);
}
