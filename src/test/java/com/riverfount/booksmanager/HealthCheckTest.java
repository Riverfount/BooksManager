package com.riverfount.booksmanager;

import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the health check required by RNF23: it also reports the
 * database connection, it is public, and nothing else of the Actuator
 * is exposed.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class HealthCheckTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private DataSource dataSource;

    @Test
    void shouldReportUpWithTheDatabaseComponentWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void shouldNotLeakDatabaseDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.db.details").doesNotExist());
    }

    @Test
    void shouldReport503WhenTheDatabaseIsUnreachable() throws Exception {
        doThrow(new SQLException("connection refused")).when(dataSource).getConnection();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.db.status").value("DOWN"));
    }

    @Test
    void shouldNotExposeOtherActuatorEndpointsEvenToAuthenticatedUsers() throws Exception {
        mockMvc.perform(get("/actuator/env").with(jwt()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldKeepTheApiBehindAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isUnauthorized());
    }
}
