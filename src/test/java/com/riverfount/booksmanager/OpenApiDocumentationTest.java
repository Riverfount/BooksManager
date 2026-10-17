package com.riverfount.booksmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The OpenAPI contract is what the front-end generates its client from (RNF05, RNF29), so every
 * route of the API must be described in it: a new endpoint without a summary, a description or its
 * error answers breaks the build instead of reaching the front-end half documented.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class OpenApiDocumentationTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mockMvc;

    private static JsonNode contract;

    @BeforeAll
    static void clearTheContract() {
        contract = null;
    }

    private JsonNode contract() throws Exception {
        if (contract == null) {
            var body = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString();
            contract = new ObjectMapper().readTree(body);
        }
        return contract;
    }

    @Test
    void everyOperationShouldHaveATagASummaryAndADescription() throws Exception {
        var problems = new ArrayList<String>();
        forEachOperation((where, operation) -> {
            if (operation.path("tags").isEmpty()) {
                problems.add(where + " has no tag");
            }
            if (operation.path("summary").asText().isBlank()) {
                problems.add(where + " has no summary");
            }
            if (operation.path("description").asText().isBlank()) {
                problems.add(where + " has no description");
            }
        });

        assertThat(problems).isEmpty();
    }

    @Test
    void everyOperationShouldDeclareItsSuccessAnswerWithABodyUnlessItHasNone() throws Exception {
        var problems = new ArrayList<String>();
        forEachOperation((where, operation) -> {
            var success = new ArrayList<String>();
            operation.path("responses").fieldNames().forEachRemaining(code -> {
                if (code.startsWith("2")) {
                    success.add(code);
                }
            });
            if (success.isEmpty()) {
                problems.add(where + " declares no success answer");
                return;
            }
            var noBody = success.stream().allMatch(code -> code.equals("204"));
            var hasBody = success.stream().anyMatch(code -> !operation.path("responses").path(code)
                    .path("content").isEmpty());
            if (!noBody && !hasBody) {
                problems.add(where + " declares " + success + " but no body schema");
            }
        });

        assertThat(problems).isEmpty();
    }

    @Test
    void everyErrorAnswerShouldBeDescribedAsAProblemDetail() throws Exception {
        var problems = new ArrayList<String>();
        forEachOperation((where, operation) -> operation.path("responses").fields().forEachRemaining(entry -> {
            var code = entry.getKey();
            var isError = code.startsWith("4") || code.startsWith("5");
            // 401 and 403 are answered by the security filters, in the same format, and added globally
            if (isError && !code.equals("401") && !code.equals("403")
                    && !entry.getValue().path("content").has(PROBLEM_JSON)) {
                problems.add(where + " answers " + code + " without " + PROBLEM_JSON);
            }
        }));

        assertThat(problems).isEmpty();
    }

    @Test
    void everyOperationThatTakesABodyShouldDeclareTheBadRequestAnswer() throws Exception {
        var problems = new ArrayList<String>();
        forEachOperation((where, operation) -> {
            if (operation.has("requestBody") && !operation.path("responses").has("400")) {
                problems.add(where + " takes a body but does not declare 400");
            }
        });

        assertThat(problems).isEmpty();
    }

    @Test
    void theDocumentedRoutesShouldBeAtLeastThoseOfTheFirstThreePhases() throws Exception {
        var paths = new ArrayList<String>();
        contract().path("paths").fieldNames().forEachRemaining(paths::add);

        assertThat(paths).contains("/api/v1/loans", "/api/v1/loans/{id}/return", "/api/v1/loans/{id}/renew",
                "/api/v1/fines/{id}/pay", "/api/v1/fines/{id}/waive", "/api/v1/suspensions/{id}/end",
                "/api/v1/reports/overdue-loans", "/api/v1/library-configuration", "/api/v1/readers",
                "/api/v1/books", "/api/v1/copies", "/api/v1/users", "/api/v1/auth/login");
    }

    private void forEachOperation(java.util.function.BiConsumer<String, JsonNode> check) throws Exception {
        contract().path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(
                method -> check.accept(method.getKey().toUpperCase() + " " + path.getKey(), method.getValue())));
    }
}
