package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.application.port.in.ReturnLoanResult;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.Suspension;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

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

    record FineSummary(
            @Schema(requiredMode = REQUIRED, description = "Identificador da multa", example = "5")
            Long id,
            @Schema(requiredMode = REQUIRED, description = "Valor da multa, em reais", example = "3.00")
            BigDecimal amount,
            @Schema(requiredMode = REQUIRED, description = "Situação da multa", example = "PENDING",
                    allowableValues = {"PENDING", "PAID", "WAIVED"})
            String status) {

        static FineSummary from(Fine fine) {
            return new FineSummary(fine.getId(), fine.getAmount(), fine.getStatus().name());
        }
    }

    record SuspensionSummary(
            @Schema(requiredMode = REQUIRED, description = "Identificador da suspensão", example = "6")
            Long id,
            @Schema(requiredMode = REQUIRED, description = "Primeiro dia suspenso (o da devolução)",
                    example = "2026-11-18")
            LocalDate startDate,
            @Schema(requiredMode = REQUIRED, description = "Dia em que o leitor volta a poder emprestar",
                    example = "2026-11-21")
            LocalDate endDate) {

        static SuspensionSummary from(Suspension suspension) {
            return new SuspensionSummary(suspension.getId(), suspension.getStartDate(), suspension.getEndDate());
        }
    }
}
