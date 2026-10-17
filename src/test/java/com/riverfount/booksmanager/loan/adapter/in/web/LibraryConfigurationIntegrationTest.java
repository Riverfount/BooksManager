package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The library configuration (RF26, HU13, RN16) with the real security chain and
 * PostgreSQL: the administrator reads and changes it, anybody else is refused, and
 * a refused change leaves the stored values alone. Each test runs in a transaction
 * that is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class LibraryConfigurationIntegrationTest {

    private static final String URL = "/api/v1/library-configuration";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private String token(String role) {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", role, Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private ResultActions edit(String role, String json) throws Exception {
        return mockMvc.perform(patch(URL).header("Authorization", token(role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void anAdministratorShouldReadTheInitialValuesTheMigrationCreated() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penaltyPolicy").value("FINE"))
                .andExpect(jsonPath("$.dailyFineAmount").value(1.00))
                .andExpect(jsonPath("$.fineCap").value(10.00))
                .andExpect(jsonPath("$.suspensionDaysPerDay").value(1))
                .andExpect(jsonPath("$.suspensionCapDays").value(365))
                .andExpect(jsonPath("$.loanPeriodDays").value(14))
                .andExpect(jsonPath("$.loanLimit").value(3))
                .andExpect(jsonPath("$.renewalLimit").value(2))
                .andExpect(jsonPath("$.reservationPickupDays").value(3));
    }

    @Test
    void anOperatorShouldReadOnlyTheLoanRulesAndStillBeRefusedTheWholeConfiguration() throws Exception {
        mockMvc.perform(get(URL + "/rules").header("Authorization", token("OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanPeriodDays").value(14))
                .andExpect(jsonPath("$.loanLimit").value(3))
                .andExpect(jsonPath("$.renewalLimit").value(2))
                .andExpect(jsonPath("$.reservationPickupDays").value(3))
                .andExpect(jsonPath("$.penaltyPolicy").doesNotExist())
                .andExpect(jsonPath("$.dailyFineAmount").doesNotExist());
        mockMvc.perform(get(URL).header("Authorization", token("OPERATOR"))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/rules")).andExpect(status().isUnauthorized());
    }

    @Test
    void theRulesShouldFollowAChangeMadeByTheAdministrator() throws Exception {
        edit("ADMIN", "{\"loanPeriodDays\":21,\"renewalLimit\":1}").andExpect(status().isOk());

        mockMvc.perform(get(URL + "/rules").header("Authorization", token("OPERATOR")))
                .andExpect(jsonPath("$.loanPeriodDays").value(21))
                .andExpect(jsonPath("$.renewalLimit").value(1));
    }

    @Test
    void anAdministratorShouldChangeSomeValuesAndTheRestStays() throws Exception {
        edit("ADMIN", "{\"penaltyPolicy\":\"SUSPENSION\",\"loanPeriodDays\":21}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penaltyPolicy").value("SUSPENSION"))
                .andExpect(jsonPath("$.loanPeriodDays").value(21))
                .andExpect(jsonPath("$.loanLimit").value(3));

        var row = jdbcTemplate.queryForMap("select penalty_policy, loan_period_days, loan_limit, fine_cap "
                + "from library_configuration");
        assertThat(row.get("penalty_policy")).isEqualTo("SUSPENSION");
        assertThat(row.get("loan_period_days")).isEqualTo(21);
        assertThat(row.get("loan_limit")).isEqualTo(3);
        assertThat(row.get("fine_cap").toString()).isEqualTo("10.00");
        mockMvc.perform(get(URL).header("Authorization", token("ADMIN")))
                .andExpect(jsonPath("$.penaltyPolicy").value("SUSPENSION"))
                .andExpect(jsonPath("$.loanPeriodDays").value(21));
    }

    @Test
    void shouldChangeTheMoneyKeepingTwoDecimalPlaces() throws Exception {
        edit("ADMIN", "{\"dailyFineAmount\":2.5,\"fineCap\":30}").andExpect(status().isOk());

        var row = jdbcTemplate.queryForMap("select daily_fine_amount, fine_cap from library_configuration");
        assertThat(row.get("daily_fine_amount").toString()).isEqualTo("2.50");
        assertThat(row.get("fine_cap").toString()).isEqualTo("30.00");
    }

    @Test
    void thePatchAnswersTheMoneyWithTwoDecimalPlacesJustLikeTheGet() throws Exception {
        var patched = edit("ADMIN", "{\"dailyFineAmount\":1.5,\"fineCap\":20}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var read = mockMvc.perform(get(URL).header("Authorization", token("ADMIN"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(patched).contains("\"dailyFineAmount\":1.50").contains("\"fineCap\":20.00");
        assertThat(read).contains("\"dailyFineAmount\":1.50").contains("\"fineCap\":20.00");
        assertThat(patched).isEqualTo(read);
    }

    @Test
    void aHugeExponentGets422QuicklyAndChangesNothing() throws Exception {
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            edit("ADMIN", "{\"fineCap\":1e999999999}").andExpect(status().isUnprocessableContent());
            edit("ADMIN", "{\"dailyFineAmount\":1e-999999999}").andExpect(status().isUnprocessableContent());
        });

        assertThat(jdbcTemplate.queryForObject("select fine_cap from library_configuration", java.math.BigDecimal.class))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void aValueThatBreaksARuleGets422AndChangesNothing() throws Exception {
        for (var json : new String[] {"{\"loanPeriodDays\":0}", "{\"loanLimit\":-1}", "{\"renewalLimit\":-1}",
                "{\"dailyFineAmount\":-1}", "{\"dailyFineAmount\":1.001}", "{\"fineCap\":0}",
                "{\"dailyFineAmount\":20}", "{\"suspensionCapDays\":0}"}) {
            var body = edit("ADMIN", json).andExpect(status().isUnprocessableContent())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).as(json).contains("\"status\":422").doesNotContain("Exception");
        }

        var row = jdbcTemplate.queryForMap("select penalty_policy, loan_period_days, loan_limit from "
                + "library_configuration");
        assertThat(row.get("penalty_policy")).isEqualTo("FINE");
        assertThat(row.get("loan_period_days")).isEqualTo(14);
        assertThat(row.get("loan_limit")).isEqualTo(3);
    }

    @Test
    void aBadRequestBodyGets400() throws Exception {
        edit("ADMIN", "{\"penaltyPolicy\":\"MONEY\"}").andExpect(status().isBadRequest());
        edit("ADMIN", "{\"loanPeriodDays\":\"abc\"}").andExpect(status().isBadRequest());
    }

    @Test
    void anOperatorAndAnUnknownProfileGet403AndNoTokenGets401AndNothingChanges() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", token("OPERATOR"))).andExpect(status().isForbidden());
        edit("OPERATOR", "{\"loanPeriodDays\":21}").andExpect(status().isForbidden());
        edit("SUPERUSER", "{\"loanPeriodDays\":21}").andExpect(status().isForbidden());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"loanPeriodDays\":21}"))
                .andExpect(status().isUnauthorized());

        assertThat(jdbcTemplate.queryForObject("select loan_period_days from library_configuration", Integer.class))
                .isEqualTo(14);
    }
}
