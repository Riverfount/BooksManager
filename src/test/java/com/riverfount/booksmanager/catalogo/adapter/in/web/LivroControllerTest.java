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

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarLivroUseCase;
import com.riverfount.booksmanager.catalogo.application.port.in.ConsultarLivroUseCase;
import com.riverfount.booksmanager.catalogo.domain.Isbn;
import com.riverfount.booksmanager.catalogo.domain.Livro;
import com.riverfount.booksmanager.compartilhado.adapter.in.web.TratadorDeExcecaoGlobal;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LivroController.class)
@Import(TratadorDeExcecaoGlobal.class)
class LivroControllerTest {

    private static final Clock RELOGIO_FIXO = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CadastrarLivroUseCase cadastrarLivroUseCase;

    @MockitoBean
    private ConsultarLivroUseCase consultarLivroUseCase;

    private Livro livroExemplo() {
        return Livro.novo(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L),
                RELOGIO_FIXO);
    }

    @Test
    @WithMockUser
    void deveCadastrarLivroEDevolver201ComLocation() throws Exception {
        given(cadastrarLivroUseCase.cadastrar(any())).willReturn(livroExemplo());

        mockMvc.perform(post("/api/v1/livros")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","titulo":"Dom Casmurro","editora":"Editora X",
                                 "anoPublicacao":1899,"categoriaId":1,"autorIds":[10]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/livros/")))
                .andExpect(jsonPath("$.isbn").value("9788533302273"))
                .andExpect(jsonPath("$.titulo").value("Dom Casmurro"))
                .andExpect(jsonPath("$.categoriaId").value(1))
                .andExpect(jsonPath("$.autorIds[0]").value(10));
    }

    @Test
    @WithMockUser
    void deveDevolver400ComProblemDetailParaTituloEmBranco() throws Exception {
        mockMvc.perform(post("/api/v1/livros")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","titulo":"","categoriaId":1,"autorIds":[10]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void deveDevolver400ParaAutorIdsVazio() throws Exception {
        mockMvc.perform(post("/api/v1/livros")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","titulo":"Dom Casmurro","categoriaId":1,"autorIds":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void deveDevolver400ParaAutorIdNuloDentroDoConjunto() throws Exception {
        // autorIds:[null] passa por Jackson (Set<Long> aceita elemento nulo
        // em tempo de execução), e sem essa validação ia quebrar lá na
        // frente com 500 em vez de um 400 limpo
        mockMvc.perform(post("/api/v1/livros")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","titulo":"Dom Casmurro","categoriaId":1,"autorIds":[null]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void deveDevolver422ParaCategoriaOuAutorInexistente() throws Exception {
        given(cadastrarLivroUseCase.cadastrar(any()))
                .willThrow(new RegraDeNegocioException("categoria não encontrada: 999999"));

        mockMvc.perform(post("/api/v1/livros")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","titulo":"Dom Casmurro","categoriaId":999999,"autorIds":[10]}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value("categoria não encontrada: 999999"));
    }

    @Test
    @WithMockUser
    void deveDevolver422ParaIsbnDuplicado() throws Exception {
        given(cadastrarLivroUseCase.cadastrar(any()))
                .willThrow(new RegraDeNegocioException("já existe um livro com o ISBN '9788533302273'"));

        mockMvc.perform(post("/api/v1/livros")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"9788533302273","titulo":"Dom Casmurro","categoriaId":1,"autorIds":[10]}
                                """))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @WithMockUser
    void deveBuscarLivroExistentePorId() throws Exception {
        given(consultarLivroUseCase.buscarPorId(eq(1L))).willReturn(Optional.of(livroExemplo()));

        mockMvc.perform(get("/api/v1/livros/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Dom Casmurro"));
    }

    @Test
    @WithMockUser
    void deveDevolver404ParaIdInexistente() throws Exception {
        given(consultarLivroUseCase.buscarPorId(eq(99L))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/livros/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }
}
