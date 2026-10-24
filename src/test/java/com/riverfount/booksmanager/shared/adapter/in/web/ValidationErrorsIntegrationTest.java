package com.riverfount.booksmanager.shared.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The 400 of an invalid request on the real routes (RNF04), through the real path (security chain, the handler and
 * the real DTOs): the list {@code errors} names each field in Portuguese, a nested field has its whole path, and a
 * body or a parameter that cannot be read is in the same list. Nothing is written, so there is nothing to clean up.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ValidationErrorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request
                .header("Authorization", "Bearer " + TestTokens.valid(encoder, properties, "OPERATOR"))
                .header("X-Requested-With", "XMLHttpRequest"));
    }

    @Test
    void anEmptyBookRegistrationShouldNameEveryRequiredFieldIncludingThePublisherAndTheYear() throws Exception {
        send(post("/api/v1/books"), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value("A requisição tem campos inválidos."))
                .andExpect(jsonPath("$.errors[?(@.field == 'publisher')].message").value(hasItem("a editora é obrigatória")))
                .andExpect(jsonPath("$.errors[?(@.field == 'publicationYear')].message")
                        .value(hasItem("o ano de publicação é obrigatório")))
                .andExpect(jsonPath("$.errors[?(@.field == 'isbn')].message").value(hasItem("o ISBN é obrigatório")))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')].message").value(hasItem("o título é obrigatório")))
                .andExpect(jsonPath("$.errors[?(@.field == 'categoryId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'authorIds')]").exists())
                .andExpect(content().string(not(containsString("Invalid request content"))));
    }

    @Test
    void aTooLongPublisherOnUpdateShouldNameTheField() throws Exception {
        send(patch("/api/v1/books/1"), "{\"publisher\":\"" + "e".repeat(256) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("publisher"))
                .andExpect(jsonPath("$.errors[0].message").value("a editora deve ter até 255 caracteres"));
    }

    @Test
    void aMalformedBirthDateShouldNameTheField() throws Exception {
        send(post("/api/v1/readers"), """
                {"name":"Ana","document":"123","email":"a@example.com","birthDate":"31/02/1990"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A requisição tem campos inválidos."))
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"))
                .andExpect(jsonPath("$.errors[0].message").value("valor inválido"))
                .andExpect(content().string(not(containsString("Failed to read"))));
    }

    @Test
    void aBrokenJsonShouldBeA400InPortuguese() throws Exception {
        send(post("/api/v1/authors"), "{\"name\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O corpo da requisição não é um JSON válido."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void anIdThatIsNotANumberShouldNameTheParameter() throws Exception {
        send(get("/api/v1/books/abc"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("valor inválido"));
    }

    @Test
    void aBusinessRuleStaysA422WithoutTheList() throws Exception {
        send(post("/api/v1/books"), """
                {"isbn":"9788533302270","title":"T","publisher":"E","publicationYear":1899,"categoryId":1,"authorIds":[1]}
                """)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
