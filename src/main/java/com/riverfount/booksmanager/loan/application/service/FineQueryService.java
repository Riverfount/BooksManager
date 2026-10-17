package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.in.ListFinesQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListFinesUseCase;
import com.riverfount.booksmanager.loan.application.port.out.FineFilter;
import com.riverfount.booksmanager.loan.application.port.out.FineRepository;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.shared.domain.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Queries on fines (RF22, HU11, RNF14): only reads, always a page. */
@Service
@Transactional(readOnly = true)
class FineQueryService implements ListFinesUseCase {

    private final FineRepository fineRepository;

    FineQueryService(FineRepository fineRepository) {
        this.fineRepository = fineRepository;
    }

    @Override
    public PageResult<Fine> list(ListFinesQuery query) {
        return fineRepository.findAll(new FineFilter(query.readerId(), null, query.status()), query.page());
    }
}
