package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.catalog.application.port.in.DeactivateCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.GetCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListCopiesQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListCopiesUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateCopyCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateCopyUseCase;
import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
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
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class CopyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterCopyUseCase registerCopyUseCase;

    @MockitoBean
    private GetCopyUseCase getCopyUseCase;

    @MockitoBean
    private ListCopiesUseCase listCopiesUseCase;

    @MockitoBean
    private UpdateCopyUseCase updateCopyUseCase;

    @MockitoBean
    private DeactivateCopyUseCase deactivateCopyUseCase;

    @Test
    @WithOperator
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
    @WithOperator
    void shouldReturn400WithProblemDetailForBlankAssetCode() throws Exception {
        mockMvc.perform(post("/api/v1/copies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":1,\"assetCode\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldReturn400ForNullBookId() throws Exception {
        mockMvc.perform(post("/api/v1/copies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetCode\":\"PAT-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
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
    @WithOperator
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
    @WithOperator
    void shouldFindExistingCopyById() throws Exception {
        given(getCopyUseCase.findById(eq(1L))).willReturn(Optional.of(Copy.create(1L, "PAT-1")));

        mockMvc.perform(get("/api/v1/copies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetCode").value("PAT-1"));
    }

    @Test
    @WithOperator
    void shouldReturn404ForNonExistentId() throws Exception {
        given(getCopyUseCase.findById(eq(99L))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/copies/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
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
    @WithOperator
    void shouldListOnlyActiveCopiesSortedByAssetCodeByDefault() throws Exception {
        given(listCopiesUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/copies")).andExpect(status().isOk());

        var query = capturedQuery();
        assertThat(query.bookId()).isNull();
        assertThat(query.status()).isNull();
        assertThat(query.assetCode()).isNull();
        assertThat(query.active()).isTrue();
        assertThat(query.page().page()).isZero();
        assertThat(query.page().size()).isEqualTo(20);
        assertThat(query.page().sortBy()).isEqualTo("assetCode");
        assertThat(query.page().direction()).isEqualTo(SortDirection.ASC);
    }

    @Test
    @WithOperator
    void shouldPassTheAssetCodeFilterToTheUseCase() throws Exception {
        given(listCopiesUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/copies").param("assetCode", "PAT-0007")).andExpect(status().isOk());

        assertThat(capturedQuery().assetCode()).isEqualTo("PAT-0007");
    }

    @Test
    @WithOperator
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
    @WithOperator
    void shouldReturn400ForPageSizeAbove100() throws Exception {
        mockMvc.perform(get("/api/v1/copies").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("100")));
    }

    @Test
    @WithOperator
    void shouldReturn400ForUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/v1/copies").param("sort", "bookId,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("bookId")));
    }

    @Test
    @WithOperator
    void shouldReturn400ForInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/v1/copies").param("status", "BROKEN"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldReturn400ForInvalidBookId() throws Exception {
        mockMvc.perform(get("/api/v1/copies").param("bookId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldUpdateCopyStatusAndReturn200() throws Exception {
        given(updateCopyUseCase.update(any()))
                .willReturn(Copy.reconstitute(1L, 7L, "PAT-1", CopyStatus.DAMAGED, true, 1L));

        mockMvc.perform(patch("/api/v1/copies/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DAMAGED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("DAMAGED"));

        then(updateCopyUseCase).should().update(new UpdateCopyCommand(1L, CopyStatus.DAMAGED));
    }

    @Test
    @WithOperator
    void shouldSendNullStatusWhenItIsOmitted() throws Exception {
        given(updateCopyUseCase.update(any())).willReturn(Copy.create(7L, "PAT-1"));

        mockMvc.perform(patch("/api/v1/copies/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        then(updateCopyUseCase).should().update(new UpdateCopyCommand(1L, null));
    }

    @Test
    @WithOperator
    void shouldIgnoreAssetCodeAndBookIdSentOnUpdate() throws Exception {
        given(updateCopyUseCase.update(any())).willReturn(Copy.create(7L, "PAT-1"));

        mockMvc.perform(patch("/api/v1/copies/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetCode\":\"OUTRO\",\"bookId\":99,\"status\":\"LOST\"}"))
                .andExpect(status().isOk());

        then(updateCopyUseCase).should().update(new UpdateCopyCommand(1L, CopyStatus.LOST));
    }

    @Test
    @WithOperator
    void shouldReturn400WhenUpdatingWithUnknownStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/copies/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BROKEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(updateCopyUseCase).shouldHaveNoInteractions();
    }

    @Test
    @WithOperator
    void shouldReturn404WhenUpdatingNonExistentCopy() throws Exception {
        given(updateCopyUseCase.update(any()))
                .willThrow(new ResourceNotFoundException("exemplar não encontrado: 99"));

        mockMvc.perform(patch("/api/v1/copies/99")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LOST\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldReturn422WhenStatusTransitionIsNotAllowed() throws Exception {
        given(updateCopyUseCase.update(any())).willThrow(new BusinessRuleException(
                "o status BORROWED só muda pelos fluxos de empréstimo, devolução e reserva"));

        mockMvc.perform(patch("/api/v1/copies/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BORROWED\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("BORROWED")));
    }

    @Test
    @WithOperator
    void shouldDeactivateCopyAndReturn204WithoutBody() throws Exception {
        mockMvc.perform(delete("/api/v1/copies/1").with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(deactivateCopyUseCase).should().deactivate(1L);
    }

    @Test
    @WithOperator
    void shouldReturn404WhenDeactivatingNonExistentCopy() throws Exception {
        willThrow(new ResourceNotFoundException("exemplar não encontrado: 99"))
                .given(deactivateCopyUseCase).deactivate(99L);

        mockMvc.perform(delete("/api/v1/copies/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithOperator
    void shouldReturn422WhenDeactivatingBorrowedCopy() throws Exception {
        willThrow(new BusinessRuleException("o exemplar não pode ser inativado enquanto estiver emprestado"))
                .given(deactivateCopyUseCase).deactivate(1L);

        mockMvc.perform(delete("/api/v1/copies/1").with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("emprestado")));
    }

    private ListCopiesQuery capturedQuery() {
        var captor = ArgumentCaptor.forClass(ListCopiesQuery.class);
        then(listCopiesUseCase).should().list(captor.capture());
        return captor.getValue();
    }

    // authorization by profile (RNF08): the whole controller is for an administrator or an operator

    @Test
    @WithMockUser(roles = "USER")
    void shouldRefuseAnAuthenticatedUserWithoutAnAllowedProfileWith403() throws Exception {
        mockMvc.perform(get("/api/v1/copies"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Você não tem permissão para acessar este recurso."));
        mockMvc.perform(delete("/api/v1/copies/1").with(csrf())).andExpect(status().isForbidden());

        then(deactivateCopyUseCase).should(never()).deactivate(any());
    }

    @Test
    @WithMockUser(roles = {})
    void shouldRefuseAnAuthenticatedUserWithNoProfileAtAllWith403() throws Exception {
        mockMvc.perform(delete("/api/v1/copies/1").with(csrf())).andExpect(status().isForbidden());

        then(deactivateCopyUseCase).should(never()).deactivate(any());
    }

    @Test
    @WithAdmin
    void anAdministratorMayCallTheRoutesToo() throws Exception {
        mockMvc.perform(delete("/api/v1/copies/1").with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    @WithOperator
    void anOperatorMayCallTheRoutes() throws Exception {
        mockMvc.perform(delete("/api/v1/copies/1").with(csrf())).andExpect(status().isNoContent());
    }
}
