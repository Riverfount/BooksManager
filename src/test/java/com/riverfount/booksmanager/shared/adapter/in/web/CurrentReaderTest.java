package com.riverfount.booksmanager.shared.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one way a route of the reader learns who the reader is (Phase 6): the {@code readerId} claim of the token,
 * never the path, a parameter or the body.
 */
@WebMvcTest(CurrentReaderTest.Probe.class)
@Import({MethodSecurityConfiguration.class, CurrentReaderTest.Probe.class})
class CurrentReaderTest {

    @ReaderOnly
    @RestController
    @RequestMapping("/api/v1/me/_probe")
    static class Probe {
        @GetMapping
        String reader(@CurrentReader Long readerId) {
            return "reader " + readerId;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor reader(Object readerIdClaim) {
        return jwt().jwt(token -> {
            token.subject("5");
            if (readerIdClaim != null) {
                token.claim("readerId", readerIdClaim);
            }
        }).authorities(new SimpleGrantedAuthority("ROLE_READER"));
    }

    @Test
    void shouldGiveTheRouteTheReaderOfTheToken() throws Exception {
        mockMvc.perform(get("/api/v1/me/_probe").with(reader(42L)))
                .andExpect(status().isOk()).andExpect(content().string("reader 42"));
        mockMvc.perform(get("/api/v1/me/_probe").with(reader(7)))
                .andExpect(status().isOk()).andExpect(content().string("reader 7"));
    }

    @Test
    void shouldIgnoreAReaderIdSentInTheRequest() throws Exception {
        mockMvc.perform(get("/api/v1/me/_probe?readerId=99").header("X-Reader-Id", "99").with(reader(42L)))
                .andExpect(status().isOk()).andExpect(content().string("reader 42"));
    }

    @Test
    void shouldRefuseATokenThatHasNoValidReader() throws Exception {
        for (var claim : new Object[] {null, "abc", "", 0L, -3L, 4.5, true}) {
            mockMvc.perform(get("/api/v1/me/_probe").with(reader(claim))).andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser(roles = "READER")
    void shouldRefuseACallerThatIsNotATokenAtAll() throws Exception {
        mockMvc.perform(get("/api/v1/me/_probe")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"ADMIN", "OPERATOR"})
    void shouldRefuseTheSystemUsersOnTheReaderRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/me/_probe")).andExpect(status().isForbidden());
    }

    @Test
    void shouldRefuseAnAdministratorWhoCarriesAReaderIdClaim() throws Exception {
        mockMvc.perform(get("/api/v1/me/_probe").with(jwt().jwt(token -> token.claim("readerId", 42L))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isForbidden());
    }
}
