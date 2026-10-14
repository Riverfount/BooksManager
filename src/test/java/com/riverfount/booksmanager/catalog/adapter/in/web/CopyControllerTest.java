package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.catalog.application.port.in.GetCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListCopiesQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListCopiesUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCopyUseCase;
import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    @MockitoBean
    private ListCopiesUseCase listCopiesUseCase;

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

    @Test
    @WithMockUser
    void shouldListCopiesWithPaginationMetadata() throws Exception {
        given(listCopiesUseCase.list(any())).willReturn(new PageResult<>(
                List.of(Copy.reconstitute(1L, 7L, "PAT-1", CopyStatus.AVAILABLE, true, 0L),
                        Copy.reconstitute(2L, 7L, "PAT-2", CopyStatus.BORROWED, true, 0L)),
                0, 2, 3));

        mockMvc.perform(get("/api/v1/copies").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].assetCode").value("PAT-1"))
                .andExpect(jsonPath("$.items[1].status").value("BORROWED"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @WithMockUser
    void shouldListOnlyActiveCopiesSortedByAssetCodeByDefault() throws Exception {
        given(listCopiesUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/copies")).andExpect(status().isOk());

        var query = capturedQuery();
        assertThat(query.bookId()).isNull();
        assertThat(query.status()).isNull();
        assertThat(query.active()).isTrue();
        assertThat(query.page().page()).isZero();
        assertThat(query.page().size()).isEqualTo(20);
        assertThat(query.page().sortBy()).isEqualTo("assetCode");
        assertThat(query.page().direction()).isEqualTo(SortDirection.ASC);
    }

    @Test
    @WithMockUser
    void shouldPassFiltersPaginationAndSortToTheUseCase() throws Exception {
        given(listCopiesUseCase.list(any())).willReturn(new PageResult<>(List.of(), 1, 5, 0));

        mockMvc.perform(get("/api/v1/copies")
                        .param("bookId", "7")
                        .param("status", "BORROWED")
                        .param("active", "false")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "id,desc"))
                .andExpect(status().isOk());

        var query = capturedQuery();
        assertThat(query.bookId()).isEqualTo(7L);
        assertThat(query.status()).isEqualTo(CopyStatus.BORROWED);
        assertThat(query.active()).isFalse();
        assertThat(query.page().page()).isEqualTo(1);
        assertThat(query.page().size()).isEqualTo(5);
        assertThat(query.page().sortBy()).isEqualTo("id");
        assertThat(query.page().direction()).isEqualTo(SortDirection.DESC);
    }

    @Test
    @WithMockUser
    void shouldReturn400ForPageSizeAbove100() throws Exception {
        mockMvc.perform(get("/api/v1/copies").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("100")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/v1/copies").param("sort", "bookId,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("bookId")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/v1/copies").param("status", "BROKEN"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void shouldReturn400ForInvalidBookId() throws Exception {
        mockMvc.perform(get("/api/v1/copies").param("bookId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    private ListCopiesQuery capturedQuery() {
        var captor = ArgumentCaptor.forClass(ListCopiesQuery.class);
        then(listCopiesUseCase).should().list(captor.capture());
        return captor.getValue();
    }
}
