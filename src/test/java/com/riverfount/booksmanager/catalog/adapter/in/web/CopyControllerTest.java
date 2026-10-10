package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.catalog.application.port.in.GetCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCopyUseCase;
import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CopyController.class)
@Import(GlobalExceptionHandler.class)
class CopyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterCopyUseCase registerCopyUseCase;

    @MockitoBean
    private GetCopyUseCase getCopyUseCase;

    @Test
    @WithMockUser
    void shouldRegisterCopyAndReturn201WithLocation() throws Exception {
        given(registerCopyUseCase.register(any())).willReturn(Copy.create(1L, "PAT-1"));

        mockMvc.perform(post("/api/v1/copies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":1,\"assetCode\":\"PAT-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/copies/")))
                .andExpect(jsonPath("$.bookId").value(1))
                .andExpect(jsonPath("$.assetCode").value("PAT-1"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser
    void shouldReturn400WithProblemDetailForBlankAssetCode() throws Exception {
        mockMvc.perform(post("/api/v1/copies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":1,\"assetCode\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForNullBookId() throws Exception {
        mockMvc.perform(post("/api/v1/copies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetCode\":\"PAT-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn422ForNonExistentBook() throws Exception {
        given(registerCopyUseCase.register(any()))
                .willThrow(new BusinessRuleException("livro não encontrado: 999999"));

        mockMvc.perform(post("/api/v1/copies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":999999,\"assetCode\":\"PAT-1\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value("livro não encontrado: 999999"));
    }

    @Test
    @WithMockUser
    void shouldReturn422ForDuplicateAssetCode() throws Exception {
        given(registerCopyUseCase.register(any()))
                .willThrow(new BusinessRuleException("já existe um exemplar com o código de patrimônio 'PAT-1'"));

        mockMvc.perform(post("/api/v1/copies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":1,\"assetCode\":\"PAT-1\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @WithMockUser
    void shouldFindExistingCopyById() throws Exception {
        given(getCopyUseCase.findById(eq(1L))).willReturn(Optional.of(Copy.create(1L, "PAT-1")));

        mockMvc.perform(get("/api/v1/copies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetCode").value("PAT-1"));
    }

    @Test
    @WithMockUser
    void shouldReturn404ForNonExistentId() throws Exception {
        given(getCopyUseCase.findById(eq(99L))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/copies/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }
}
