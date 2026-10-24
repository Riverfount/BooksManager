package com.riverfount.booksmanager.shared.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The 400 of an invalid request (RNF04): the status is still 400 (the request is wrong in its form; the 422 is the
 * answer to a business rule), but the answer now says in Portuguese which field failed and why, as a list
 * {@code errors} of {@code {field, message}}, so the front-end marks each field. The path of a nested field is whole
 * ({@code guardian.email}, {@code ids[1]}), a body that cannot be read or has a value of the wrong type is in the same
 * list when Jackson knows the field, and nothing internal (an exception name, a stack trace, Jackson's text) is
 * leaked. A probe controller with the usual constraints stands in for the real routes.
 */
@WebMvcTest(ValidationErrorsTest.Probe.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class, ValidationErrorsTest.Probe.class})
class ValidationErrorsTest {

    @Autowired
    private MockMvc mockMvc;

    record Guardian(@NotBlank(message = "o e-mail é obrigatório") String email) {
    }

    record Body(
            @NotBlank(message = "o nome é obrigatório") String name,
            @Size(max = 3, message = "o código deve ter até 3 caracteres") String code,
            @Valid @NotNull(message = "o responsável é obrigatório") Guardian guardian,
            List<@NotNull(message = "o id não pode ser nulo") Long> ids,
            LocalDate date) {
    }

    @RestController
    @RequestMapping("/probe")
    @AdminOrOperator
    static class Probe {

        @PostMapping
        void create(@Valid @RequestBody Body body) {
        }

        @GetMapping("/{id}")
        void get(@PathVariable Long id) {
        }

        @GetMapping("/need")
        void need(@RequestParam int x) {
        }
    }

    private ResultActions send(String json) throws Exception {
        return mockMvc.perform(post("/probe").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static final String VALID = "{\"name\":\"Ana\",\"guardian\":{\"email\":\"a@b.com\"}}";

    @Test
    @WithOperator
    void aValidRequestShouldPass() throws Exception {
        send(VALID).andExpect(status().isOk());
    }

    @Test
    @WithOperator
    void shouldListEveryInvalidFieldWithItsMessageInPortuguese() throws Exception {
        send("{\"code\":\"abcd\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("A requisição tem campos inválidos."))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                // sorted by field, so the answer does not depend on the order the validator found them
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.errors[0].message").value("o código deve ter até 3 caracteres"))
                .andExpect(jsonPath("$.errors[1].field").value("guardian"))
                .andExpect(jsonPath("$.errors[1].message").value("o responsável é obrigatório"))
                .andExpect(jsonPath("$.errors[2].field").value("name"))
                .andExpect(jsonPath("$.errors[2].message").value("o nome é obrigatório"));
    }

    @Test
    @WithOperator
    void aNestedFieldShouldComeWithItsWholePath() throws Exception {
        send("{\"name\":\"Ana\",\"guardian\":{\"email\":\"\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("guardian.email"))
                .andExpect(jsonPath("$.errors[0].message").value("o e-mail é obrigatório"));
    }

    @Test
    @WithOperator
    void anElementOfAListShouldComeWithItsPosition() throws Exception {
        send("{\"name\":\"Ana\",\"guardian\":{\"email\":\"a@b.com\"},\"ids\":[1,null]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ids[1]"))
                .andExpect(jsonPath("$.errors[0].message").value("o id não pode ser nulo"));
    }

    @Test
    @WithOperator
    void aMalformedDateShouldBeInTheSameListWithoutJacksonsText() throws Exception {
        send("{\"name\":\"Ana\",\"guardian\":{\"email\":\"a@b.com\"},\"date\":\"31/02/2026\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value("A requisição tem campos inválidos."))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("date"))
                .andExpect(jsonPath("$.errors[0].message").value("valor inválido"))
                .andExpect(content().string(not(containsString("31/02"))))
                .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    @WithOperator
    void aValueOfTheWrongTypeShouldNameTheField() throws Exception {
        send("{\"name\":\"Ana\",\"guardian\":{\"email\":\"a@b.com\"},\"ids\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ids"))
                .andExpect(jsonPath("$.errors[0].message").value("valor inválido"));
    }

    @Test
    @WithOperator
    void aBrokenJsonShouldBeA400InPortugueseWithNoList() throws Exception {
        send("{\"name\":")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value("O corpo da requisição não é um JSON válido."))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(content().string(not(containsString("Unexpected"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    @WithOperator
    void anEmptyBodyShouldSayTheBodyIsRequired() throws Exception {
        mockMvc.perform(post("/probe").with(csrf()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O corpo da requisição é obrigatório."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @WithOperator
    void aPathVariableOfTheWrongTypeShouldNameTheParameter() throws Exception {
        mockMvc.perform(get("/probe/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A requisição tem campos inválidos."))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("valor inválido"))
                .andExpect(content().string(not(containsString("Failed to convert"))));
    }

    @Test
    @WithOperator
    void aMissingRequiredParameterShouldNameIt() throws Exception {
        mockMvc.perform(get("/probe/need"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("x"))
                .andExpect(jsonPath("$.errors[0].message").value("é obrigatório"));
    }

    @Test
    @WithOperator
    void aParameterOfTheWrongTypeShouldNameIt() throws Exception {
        mockMvc.perform(get("/probe/need").param("x", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("x"))
                .andExpect(jsonPath("$.errors[0].message").value("valor inválido"));
    }
}
