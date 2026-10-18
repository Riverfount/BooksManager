package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.domain.ReturnCondition;
import io.swagger.v3.oas.annotations.media.Schema;

record ReturnLoanRequest(
        @Schema(description = "Condição em que o exemplar voltou (RF15); omitida vale GOOD. Só registra a "
                + "ocorrência, sem cobrança: DAMAGED e LOST mantêm o exemplar fora do acervo disponível",
                example = "DAMAGED")
        ReturnCondition condition) {
}
