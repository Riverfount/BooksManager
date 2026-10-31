package com.riverfount.booksmanager.user.adapter.in.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.user.application.port.in.AdminPasswordResetResult;
import com.riverfount.booksmanager.user.application.port.in.ResetAdminPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.ResetAdminPasswordUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * The recovery of the administrator at startup (RF08, RF09): nothing happens, and nothing is logged, unless
 * APP_ADMIN_RESET_PASSWORD is set; once it is set, every startup warns to remove it; each outcome is one fixed
 * message, and no login, password or hash ever reaches the log. No outcome stops the startup.
 */
@ExtendWith(OutputCaptureExtension.class)
class AdminPasswordResetRunnerTest {

    private static final String LOGIN = "admin-secreto";
    private static final String PASSWORD = "Nova#Senha2026";

    private ResetAdminPasswordUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = mock(ResetAdminPasswordUseCase.class);
    }

    private AdminPasswordResetRunner runner(String login, String resetPassword) {
        return new AdminPasswordResetRunner(useCase, new AdminProperties(login, "irrelevante", resetPassword));
    }

    private void runWith(AdminPasswordResetResult result, CapturedOutput output) {
        given(useCase.reset(any())).willReturn(result);
        runner(LOGIN, PASSWORD).run(null);
        assertNoSecret(output);
    }

    private static void assertNoSecret(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(LOGIN).doesNotContain("irrelevante");
    }

    @Test
    void shouldDoNothingAndLogNothingWhenTheVariableIsNotSet(CapturedOutput output) {
        runner(LOGIN, null).run(null);
        runner(LOGIN, "").run(null);
        runner(LOGIN, "   ").run(null);

        then(useCase).should(never()).reset(any());
        assertThat(output.getAll()).doesNotContain("APP_ADMIN_RESET_PASSWORD");
    }

    @Test
    void shouldHandTheLoginAndThePasswordOfTheEnvironmentToTheUseCase() {
        given(useCase.reset(any())).willReturn(AdminPasswordResetResult.RESET);

        runner(LOGIN, PASSWORD).run(null);

        var captor = ArgumentCaptor.forClass(ResetAdminPasswordCommand.class);
        then(useCase).should().reset(captor.capture());
        assertThat(captor.getValue().login()).isEqualTo(LOGIN);
        assertThat(captor.getValue().password()).isEqualTo(PASSWORD);
    }

    @Test
    void shouldAlwaysWarnToRemoveTheVariableOnceItIsSet(CapturedOutput output) {
        for (var result : AdminPasswordResetResult.values()) {
            given(useCase.reset(any())).willReturn(result);

            runner(LOGIN, PASSWORD).run(null);
        }

        var warnings = output.getAll().lines().filter(l -> l.contains("Remove APP_ADMIN_RESET_PASSWORD")).count();
        assertThat(warnings).isEqualTo(AdminPasswordResetResult.values().length);
        assertNoSecret(output);
    }

    @Test
    void shouldSayTheAccessWasRecovered(CapturedOutput output) {
        runWith(AdminPasswordResetResult.RESET, output);

        assertThat(output.getAll()).contains("The password of the administrator was reset");
    }

    @Test
    void shouldSayNothingChangedWhenThePasswordWasAlreadyThat(CapturedOutput output) {
        runWith(AdminPasswordResetResult.ALREADY_UP_TO_DATE, output);

        assertThat(output.getAll()).contains("already has this password");
    }

    @Test
    void shouldSayWhatIsWrongWhenTheLoginIsMissingOrUnknown(CapturedOutput output) {
        runWith(AdminPasswordResetResult.NOT_CONFIGURED, output);
        runWith(AdminPasswordResetResult.NOT_FOUND, output);

        assertThat(output.getAll()).contains("APP_ADMIN_LOGIN is not set").contains("no user with the login");
    }

    @Test
    void shouldSayOnlyAnAdministratorIsRecoveredThisWay(CapturedOutput output) {
        runWith(AdminPasswordResetResult.NOT_AN_ADMINISTRATOR, output);

        assertThat(output.getAll()).contains("is not an administrator");
    }

    @Test
    void shouldSayAnInactiveAdministratorStaysInactive(CapturedOutput output) {
        runWith(AdminPasswordResetResult.INACTIVE, output);

        assertThat(output.getAll()).contains("is inactive and stays inactive");
    }

    @Test
    void aPasswordThatBreaksThePolicyShouldLogWhatIsMissingWithoutStoppingTheStartup(CapturedOutput output) {
        given(useCase.reset(any())).willThrow(new BusinessRuleException(
                "a senha não atende às regras: deve ter ao menos 8 caracteres"));

        runner(LOGIN, "curta").run(null);

        assertThat(output.getAll()).contains("was not applied").contains("a senha não atende às regras");
        assertNoSecret(output);
    }
}
