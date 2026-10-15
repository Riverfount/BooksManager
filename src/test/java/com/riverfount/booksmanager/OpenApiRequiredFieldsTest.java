package com.riverfount.booksmanager;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Verifies that the generated OpenAPI contract (RNF24) marks as required
 * the fields the API always sends or always demands, so that a typed
 * client generated from it (RNF29) does not see every field as optional.
 * Fields that can be null stay out of {@code required}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiRequiredFieldsTest {

    @Autowired
    private MockMvc mockMvc;

    private ResultActions apiDocs;

    @BeforeEach
    void loadTheContract() throws Exception {
        apiDocs = mockMvc.perform(get("/v3/api-docs").with(user("operator"))).andExpect(status().isOk());
    }

    @Test
    void bookResponseShouldRequireEverythingExceptWhatCanBeNull() throws Exception {
        apiDocs.andExpect(requiredOf("BookResponse", "id", "isbn", "title", "categoryId", "authorIds",
                "active", "availableCopies"));
    }

    @Test
    void authorResponseShouldRequireAllItsFields() throws Exception {
        apiDocs.andExpect(requiredOf("AuthorResponse", "id", "name", "active"));
    }

    @Test
    void categoryResponseShouldRequireAllItsFields() throws Exception {
        apiDocs.andExpect(requiredOf("CategoryResponse", "id", "name", "active"));
    }

    @Test
    void copyResponseShouldRequireAllItsFields() throws Exception {
        apiDocs.andExpect(requiredOf("CopyResponse", "id", "bookId", "assetCode", "status", "active"));
    }

    @Test
    void everyPageShouldRequireItsItemsAndMetadata() throws Exception {
        for (var schema : new String[] {"PageResponseBookResponse", "PageResponseAuthorResponse",
                "PageResponseCategoryResponse", "PageResponseCopyResponse"}) {
            apiDocs.andExpect(requiredOf(schema, "items", "page", "size", "totalItems", "totalPages"));
        }
    }

    @Test
    void registrationRequestsShouldRequireWhatTheyValidate() throws Exception {
        apiDocs.andExpect(requiredOf("RegisterBookRequest", "isbn", "title", "categoryId", "authorIds"))
                .andExpect(requiredOf("RegisterAuthorRequest", "name"))
                .andExpect(requiredOf("RegisterCategoryRequest", "name"))
                .andExpect(requiredOf("RegisterCopyRequest", "bookId", "assetCode"));
    }

    @Test
    void partialUpdateRequestsShouldRequireNothing() throws Exception {
        for (var schema : new String[] {"UpdateBookRequest", "UpdateAuthorRequest", "UpdateCategoryRequest",
                "UpdateCopyRequest"}) {
            apiDocs.andExpect(jsonPath("$.components.schemas." + schema + ".required").doesNotExist());
        }
    }

    private static org.springframework.test.web.servlet.ResultMatcher requiredOf(String schema,
            String... fields) {
        return jsonPath("$.components.schemas." + schema + ".required", containsInAnyOrder(fields));
    }
}
