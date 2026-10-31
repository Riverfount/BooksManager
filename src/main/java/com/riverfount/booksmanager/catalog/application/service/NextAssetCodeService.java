package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.SuggestNextAssetCodeUseCase;
import com.riverfount.booksmanager.catalog.application.port.out.AssetCodePrefix;
import com.riverfount.booksmanager.catalog.application.port.out.CopyRepository;
import com.riverfount.booksmanager.catalog.domain.AssetCodes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suggests the next asset code (RN02): the prefix of the library's configuration, and the highest number already used
 * with it (inactive copies included, since their codes stay taken) plus one. The format is the domain's
 * ({@link AssetCodes}); this class only asks for the two facts and puts them together. Read-only: it registers and
 * reserves nothing.
 */
@Service
@Transactional(readOnly = true)
class NextAssetCodeService implements SuggestNextAssetCodeUseCase {

    private final CopyRepository copyRepository;
    private final AssetCodePrefix assetCodePrefix;

    NextAssetCodeService(CopyRepository copyRepository, AssetCodePrefix assetCodePrefix) {
        this.copyRepository = copyRepository;
        this.assetCodePrefix = assetCodePrefix;
    }

    @Override
    public String suggest() {
        var prefix = assetCodePrefix.prefix();
        return AssetCodes.next(prefix, copyRepository.findHighestAssetCodeNumber(prefix));
    }
}
