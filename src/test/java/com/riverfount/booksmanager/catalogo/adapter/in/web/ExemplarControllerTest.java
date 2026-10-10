package com.riverfount.booksmanager.catalogo.adapter.in.web;

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

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarExemplarUseCase;
import com.riverfount.booksmanager.catalogo.application.port.in.ConsultarExemplarUseCase;
import com.riverfount.booksmanager.catalogo.domain.Exemplar;
import com.riverfount.booksmanager.compartilhado.adapter.in.web.TratadorDeExcecaoGlobal;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExemplarController.class)
@Import(TratadorDeExcecaoGlobal.class)
class ExemplarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CadastrarExemplarUseCase cadastrarExemplarUseCase;

    @MockitoBean
    private ConsultarExemplarUseCase consultarExemplarUseCase;

    @Test
    @WithMockUser
    void deveCadastrarExemplarEDevolver201ComLocation() throws Exception {
        given(cadastrarExemplarUseCase.cadastrar(any())).willReturn(Exemplar.novo(1L, "PAT-1"));

        mockMvc.perform(post("/api/v1/exemplares")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livroId\":1,\"codigoPatrimonio\":\"PAT-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/exemplares/")))
                .andExpect(jsonPath("$.livroId").value(1))
                .andExpect(jsonPath("$.codigoPatrimonio").value("PAT-1"))
                .andExpect(jsonPath("$.status").value("DISPONIVEL"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @WithMockUser
    void deveDevolver400ComProblemDetailParaCodigoPatrimonioEmBranco() throws Exception {
        mockMvc.perform(post("/api/v1/exemplares")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livroId\":1,\"codigoPatrimonio\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void deveDevolver400ParaLivroIdNulo() throws Exception {
        mockMvc.perform(post("/api/v1/exemplares")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoPatrimonio\":\"PAT-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void deveDevolver422ParaLivroInexistente() throws Exception {
        given(cadastrarExemplarUseCase.cadastrar(any()))
                .willThrow(new RegraDeNegocioException("livro não encontrado: 999999"));

        mockMvc.perform(post("/api/v1/exemplares")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livroId\":999999,\"codigoPatrimonio\":\"PAT-1\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value("livro não encontrado: 999999"));
    }

    @Test
    @WithMockUser
    void deveDevolver422ParaCodigoPatrimonioDuplicado() throws Exception {
        given(cadastrarExemplarUseCase.cadastrar(any()))
                .willThrow(new RegraDeNegocioException("já existe um exemplar com o código de patrimônio 'PAT-1'"));

        mockMvc.perform(post("/api/v1/exemplares")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"livroId\":1,\"codigoPatrimonio\":\"PAT-1\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @WithMockUser
    void deveBuscarExemplarExistentePorId() throws Exception {
        given(consultarExemplarUseCase.buscarPorId(eq(1L))).willReturn(Optional.of(Exemplar.novo(1L, "PAT-1")));

        mockMvc.perform(get("/api/v1/exemplares/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoPatrimonio").value("PAT-1"));
    }

    @Test
    @WithMockUser
    void deveDevolver404ParaIdInexistente() throws Exception {
        given(consultarExemplarUseCase.buscarPorId(eq(99L))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/exemplares/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }
}
