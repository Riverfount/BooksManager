package com.riverfount.booksmanager.loan.adapter.out.catalog;

import com.riverfount.booksmanager.catalog.application.port.out.AssetCodePrefix;
import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import org.springframework.stereotype.Component;

/**
 * Answers the catalog's question "which prefix do the suggested asset codes have?" from the library configuration
 * (RF26, RN16), which belongs to the loan module. It reads the configuration at each call, so a change made by the
 * administrator applies to the next suggestion.
 */
@Component
class AssetCodePrefixAdapter implements AssetCodePrefix {

    private final LibraryConfigurationRepository configurationRepository;

    AssetCodePrefixAdapter(LibraryConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }

    @Override
    public String prefix() {
        return configurationRepository.get().getAssetCodePrefix();
    }
}
