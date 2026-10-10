package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Copy;

record CopyResponse(Long id, Long bookId, String assetCode, String status, boolean active) {

    static CopyResponse from(Copy copy) {
        return new CopyResponse(copy.getId(), copy.getBookId(), copy.getAssetCode(),
                copy.getStatus().name(), copy.isActive());
    }
}
