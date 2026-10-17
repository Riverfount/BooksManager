package com.riverfount.booksmanager;

import static org.hamcrest.Matchers.containsInAnyOrder;
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
        apiDocs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void bookResponseShouldRequireEverythingExceptWhatCanBeNull() throws Exception {
        apiDocs.andExpect(requiredOf("BookResponse", "id", "isbn", "title", "categoryId", "authorIds",
                "active", "availableCopies", "totalCopies", "category", "authors"));
    }

    @Test
    void libraryConfigurationResponseShouldRequireAllItsFields() throws Exception {
        apiDocs.andExpect(requiredOf("LibraryConfigurationResponse", "penaltyPolicy", "dailyFineAmount", "fineCap",
                "suspensionDaysPerDay", "suspensionCapDays", "loanPeriodDays", "loanLimit", "renewalLimit",
                "reservationPickupDays"));
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
    void readerResponseShouldRequireEverythingExceptWhatCanBeNull() throws Exception {
        apiDocs.andExpect(requiredOf("ReaderResponse", "id", "name", "document", "email", "birthDate", "active",
                        "blocked"))
                .andExpect(requiredOf("GuardianResponse", "name", "email"));
    }

    @Test
    void readerSummaryShouldRequireEverythingItAlwaysSends() throws Exception {
        apiDocs.andExpect(requiredOf("ReaderSummaryResponse", "id", "name", "maskedDocument", "email", "active",
                "blocked"));
    }

    @Test
    void theUserContractShouldRequireWhatItAlwaysSendsAndDemandsAndCarryNoSecret() throws Exception {
        apiDocs.andExpect(requiredOf("CreateUserRequest", "login", "password", "role"))
                .andExpect(requiredOf("UserResponse", "id", "login", "role", "active", "blocked"))
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.password").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.failedLoginAttempts").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.blockedUntil").exists());
    }

    @Test
    void theLoginContractShouldRequireWhatItAlwaysSendsAndDemands() throws Exception {
        apiDocs.andExpect(requiredOf("LoginRequest", "login", "password"))
                .andExpect(requiredOf("LoginResponse", "accessToken", "tokenType", "expiresIn"));
    }

    @Test
    void everyPageShouldRequireItsItemsAndMetadata() throws Exception {
        for (var schema : new String[] {"PageResponseBookResponse", "PageResponseAuthorResponse",
                "PageResponseCategoryResponse", "PageResponseCopyResponse", "PageResponseReaderSummaryResponse",
                "PageResponseUserResponse"}) {
            apiDocs.andExpect(requiredOf(schema, "items", "page", "size", "totalItems", "totalPages"));
        }
    }

    @Test
    void registrationRequestsShouldRequireWhatTheyValidate() throws Exception {
        apiDocs.andExpect(requiredOf("RegisterBookRequest", "isbn", "title", "categoryId", "authorIds"))
                .andExpect(requiredOf("RegisterAuthorRequest", "name"))
                .andExpect(requiredOf("RegisterCategoryRequest", "name"))
                .andExpect(requiredOf("RegisterCopyRequest", "bookId", "assetCode"))
                .andExpect(requiredOf("RegisterReaderRequest", "name", "document", "email", "birthDate"))
                // the guardian's fields are checked by the domain, which answers 422 with a clear message
                .andExpect(jsonPath("$.components.schemas.GuardianRequest.required").doesNotExist());
    }

    @Test
    void partialUpdateRequestsShouldRequireNothing() throws Exception {
        for (var schema : new String[] {"UpdateBookRequest", "UpdateAuthorRequest", "UpdateCategoryRequest",
                "UpdateCopyRequest", "UpdateReaderRequest", "UpdateUserRequest"}) {
            apiDocs.andExpect(jsonPath("$.components.schemas." + schema + ".required").doesNotExist());
        }
    }

    private static org.springframework.test.web.servlet.ResultMatcher requiredOf(String schema,
            String... fields) {
        return jsonPath("$.components.schemas." + schema + ".required", containsInAnyOrder(fields));
    }
}
