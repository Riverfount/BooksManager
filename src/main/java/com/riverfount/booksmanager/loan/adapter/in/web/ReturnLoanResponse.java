package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.application.port.in.ReturnLoanResult;
import io.swagger.v3.oas.annotations.media.Schema;

record ReturnLoanResponse(
        @Schema(requiredMode = REQUIRED, description = "O empréstimo, já encerrado")
        LoanResponse loan,
        @Schema(requiredMode = REQUIRED, description = "Dias de atraso; zero se voltou no prazo ou antes",
                example = "3")
        long daysLate,
        @Schema(description = "Multa gerada; nula quando a política vigente não aplicou multa")
        FineSummary fine,
        @Schema(description = "Suspensão gerada; nula quando a política vigente não aplicou suspensão")
        SuspensionSummary suspension) {

    static ReturnLoanResponse from(ReturnLoanResult result) {
        return new ReturnLoanResponse(LoanResponse.from(result.loan()), result.daysLate(),
                result.fine() == null ? null : FineSummary.from(result.fine()),
                result.suspension() == null ? null : SuspensionSummary.from(result.suspension()));
    }
}
