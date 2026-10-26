package com.riverfount.booksmanager.shared.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.shared.domain.RateLimitExceededException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Going over a limit of attempts is a 429 in ProblemDetail with the time to wait, and nothing else (Phase 6). */
@WebMvcTest(RateLimitExceededHandlerTest.Probe.class)
@Import({GlobalExceptionHandler.class, RateLimitExceededHandlerTest.Probe.class})
class RateLimitExceededHandlerTest {

    @PublicEndpoint
    @RestController
    static class Probe {
        @GetMapping("/_probe/limited")
        String limited() {
            throw new RateLimitExceededException(Duration.ofMillis(17_200));
        }

        @GetMapping("/_probe/limited-now")
        String limitedNow() {
            throw new RateLimitExceededException(Duration.ZERO);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void shouldAnswer429WithTheWaitRoundedUpInSeconds() throws Exception {
        mockMvc.perform(get("/_probe/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "18"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.detail").value("Muitas requisições. Aguarde 18 segundos e tente de novo."));
    }

    @Test
    @WithMockUser
    void shouldNeverAskToWaitLessThanOneSecond() throws Exception {
        mockMvc.perform(get("/_probe/limited-now"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"));
    }
}
