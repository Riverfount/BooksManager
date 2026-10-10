package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.GetCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCopyCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.out.BookRepository;
import com.riverfount.booksmanager.catalog.application.port.out.CopyRepository;
import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates registering and querying Copy: fetches what the domain
 * needs to decide (does the book exist and is it active? does the asset
 * code already exist?), calls the domain and persists. The business
 * decision itself lives in the domain (Copy.create,
 * Copy.validateUniqueAssetCode).
 */
@Service
@Transactional
class CopyService implements RegisterCopyUseCase, GetCopyUseCase {

    private final CopyRepository copyRepository;
    private final BookRepository bookRepository;

    CopyService(CopyRepository copyRepository, BookRepository bookRepository) {
        this.copyRepository = copyRepository;
        this.bookRepository = bookRepository;
    }

    @Override
    public Copy register(RegisterCopyCommand command) {
        // built before the cross-checks: this way, bookId is already
        // guaranteed non-null when we query BookRepository.
        var copy = Copy.create(command.bookId(), command.assetCode());
        validateBookExistsAndIsActive(copy.getBookId());
        Copy.validateUniqueAssetCode(copy.getAssetCode(),
                copyRepository.existsByAssetCode(copy.getAssetCode()));
        return copyRepository.save(copy);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Copy> findById(Long id) {
        return copyRepository.findById(id);
    }

    private void validateBookExistsAndIsActive(Long bookId) {
        var book = bookRepository.findById(bookId);
        if (book.isEmpty()) {
            throw new BusinessRuleException("livro não encontrado: " + bookId);
        }
        if (!book.get().isActive()) {
            throw new BusinessRuleException("livro inativo: " + bookId);
        }
    }
}
